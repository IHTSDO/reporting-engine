package org.ihtsdo.termserver.scripting.delta.one_offs;

import com.google.common.io.Files;
import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ComponentAnnotationEntry;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.InactivationIndicatorEntry;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.ihtsdo.termserver.scripting.util.DialectChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class INFRA13323_AddAttributionAnnotations extends DeltaGenerator implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(INFRA13323_AddAttributionAnnotations.class);
	private static final int BATCH_SIZE = 50;
	private static final String ATTRIBUTION_ADDED = "Orphanet attribution added";
	private static final String BRACKETED_TEXT_REGEX = "\\([^\\)]*\\bsee (this|these) terms?\\)";

	private Concept annotationType = null;
	private String annotationStr = "Inserm Orphanet";
	private Set<Concept> conceptsAnnotated = new HashSet<>();
	private Map<Concept, String> conceptDefinitions = new HashMap<>();

	public static void main(String[] args) throws TermServerScriptException {
		INFRA13323_AddAttributionAnnotations delta = new INFRA13323_AddAttributionAnnotations();
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"; //Ad-Hoc Batch Updates
			delta.getArchiveManager().setPopulateReleasedFlag(true);
			delta.init(args);
			delta.inputFileHasHeaderRow = true;
			delta.loadProjectSnapshot(false); //Need all descriptions loaded.
			delta.postInit();
			delta.loadDefinitionsFile();
			delta.annotationType = delta.gl.getConcept("1295448001"); // |Attribution (attribute)|
			int lastBatchSize = delta.process();
			delta.createOutputArchive(false, lastBatchSize);
		} finally {
			delta.finish();
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[]{
				"SCTID, FSN, SemTag, Severity, Action, Info, detail, , ",
				"SCTID, FSN, SemTag, Reason, detail, detail,"
		};

		String[] tabNames = new String[]{
				"Map Records Processed",
				"Map Records Skipped"
		};
		super.postInit(tabNames, columnHeadings, false);
	}

	private void loadDefinitionsFile() throws TermServerScriptException {
		int lineNum = 0;
		try {
			List<String> lines = Files.readLines(getInputFileOrThrow(2), StandardCharsets.UTF_8);
			for (String line : lines) {
				lineNum++;
				String[] columns = line.split(TAB);
				if (columns.length < 4 || columns[IDX_ID].equals("ORPHAcode") || columns[IDX_ID].isEmpty()) {
					LOGGER.warn("Skipping line {} : {}", lineNum, line);
					continue;
				}

				String sctId = columns[2];
				String definition = columns[3];
				if (definition.charAt(0) == '"') {
					//Remove the quotes from the beginning and end of the definition
					definition = definition.substring(1, definition.length() - 1);
				}
				Concept c = gl.getConcept(sctId);
				conceptDefinitions.put(c, definition);
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to read input file at line " + lineNum, e);
		}
		LOGGER.info("Loaded definitions for {} concepts", conceptDefinitions.size());
	}

	private int process() throws TermServerScriptException {
		int conceptsInThisBatch = 0;
		for (Component c : processFile()) {
			conceptsInThisBatch += addAnnotation((Concept)c);
			if (conceptsInThisBatch >= BATCH_SIZE) {
				if (!dryRun) {
					createOutputArchive(false, conceptsInThisBatch);
					outputDirName = "output"; //Reset so we don't end up with _1_1_1
					initialiseOutputDirectory();
					initialiseFileHeaders();
				}
				gl.setAllComponentsClean();
				conceptsInThisBatch = 0;
			}
		}
		return conceptsInThisBatch;
	}

	private int addAnnotation(Concept c) throws TermServerScriptException {
		int changesMade = replaceTextDefinitions(c);

		String processingDetail = null;
		ReportActionType action = ReportActionType.NO_CHANGE;
		String rmStr = "";
		if (c.hasIssues()) {
			processingDetail = c.getIssues();
		} else if (!c.isActiveSafely()) {
			processingDetail = "Concept now inactive";
		} else if (!c.getComponentAnnotationEntries().isEmpty()) {
			processingDetail = "Already has annotation";
		} else {
			ComponentAnnotationEntry cae = ComponentAnnotationEntry.withDefaults(c, annotationType, annotationStr);
			c.addComponentAnnotationEntry(cae);
			rmStr = cae.toString();
			outputRF2(c);
			action = ReportActionType.REFSET_MEMBER_ADDED;
			changesMade++;
			countIssue(c);
			conceptsAnnotated.add(c);
			processingDetail = ATTRIBUTION_ADDED;
		}

		if (processingDetail.equals(ATTRIBUTION_ADDED)) {
			report(c, Severity.LOW, action, processingDetail, rmStr);
		} else {
			report(SECONDARY_REPORT, c, Severity.HIGH, action, processingDetail, rmStr);
		}

		//If we've made any changes, count this concept as part of our batch.  Otherwise, not.
		return changesMade == 0 ? 0 : 1;
	}

	private int replaceTextDefinitions(Concept c) throws TermServerScriptException {
		int changesMade = 0;

		//Do we have a text definition from Orphanet?
		if (!conceptDefinitions.containsKey(c)) {
			c.addIssue("No Orphanet definition supplied");
			return NO_CHANGES_MADE;
		}
		String definition = normalizeSuppliedTextDefinition(c);
		boolean textDefinitionNeeded = true;
		boolean usgbVarianceDetected = false;
		//Do we already have the expected text definition?
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
				//Do we have us/gb variance?
				if (d.isPreferred(RF2Constants.US_ENG_LANG_REFSET) && !d.isPreferred(RF2Constants.GB_ENG_LANG_REFSET)) {
					usgbVarianceDetected = true;
				}

				//Because of dialect variance, we need to check ALL descriptions for the new text definition
				if (conceptFeaturesNewTextDefinition(c, definition)) {
					report(c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Text definition already present", d);
					textDefinitionNeeded = false;
				} else {
					d.setActive(false, true);
					d.addInactivationIndicator(InactivationIndicatorEntry.withDefaults(d, SCTID_INACT_OUTDATED));
					report(c, Severity.LOW, ReportActionType.INACT_IND_ADDED, "Outdated", d);
					changesMade += 2;
				}
			}
		}

		if (textDefinitionNeeded) {
			addNewTextDefinition(c, usgbVarianceDetected, definition);
			changesMade++;
		}

		return changesMade;
	}

	private boolean conceptFeaturesNewTextDefinition(Concept c, String targetText) {
		for (Description d : c.getDescriptions()) {
			if (d.getTerm().equals(targetText)) {
				return true;
			}
		}
		return false;
	}

	private void addNewTextDefinition(Concept c, boolean usgbVarianceDetected, String definition) throws TermServerScriptException {
		//Add the Orphnet supplied text definition to the concept
		//Check for US/GB Variance
		DialectChecker dc = DialectChecker.create(); //Will only load the us/gb file the first time this singleton is requested
		String usgbTerm = dc.findFirstUSGBSpecificTerm(definition);
		boolean replacementContainsVariance = false;
		String acceptabilityStr = "US + GB";
		if (usgbTerm != null) {
			report(c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "US/GB variance '" + usgbTerm + "' detected in new text definition", definition);
			replacementContainsVariance = true;
		} else 	if (usgbVarianceDetected) {
			//We previously had a US/GB variance, but it doesn't appear in the replacement
			report(c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "US/GB variance does not feature in replacement definition", definition);
		}

		String definitionInDialect = definition;
		if (replacementContainsVariance) {
			//We need to replace the US/GB variance with the appropriate term
			definitionInDialect = dc.makeUSSpecific(definition);

			Description d = Description.withDefaults(definitionInDialect, DescriptionType.TEXT_DEFINITION, Acceptability.PREFERRED);
			d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
			//Remove the GB acceptability
			d.removeAcceptability(RF2Constants.GB_ENG_LANG_REFSET, true);
			d.setConceptId(c.getId());
			d.setId(descIdGenerator.getSCTID());
			c.addDescription(d);
			report(c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, "Text definition added for US", d);

			//Now create the GB version
			definitionInDialect = dc.makeGBSpecific(definition);
			acceptabilityStr = "GB";
		}

		//Now create either just the one definition, or a 2nd one that's uniquely GB
		Description d = Description.withDefaults(definitionInDialect, DescriptionType.TEXT_DEFINITION, Acceptability.PREFERRED);
		d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
		if (replacementContainsVariance) {
			d.removeAcceptability(RF2Constants.US_ENG_LANG_REFSET, true);
		}
		d.setConceptId(c.getId());
		d.setId(descIdGenerator.getSCTID());
		c.addDescription(d);
		report(c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, "Text definition added for " + acceptabilityStr, d);
	}

	private String normalizeSuppliedTextDefinition(Concept c) throws TermServerScriptException {
		String definition = conceptDefinitions.get(c);
		//Remove all instances of the text "(something; see this term)" from the definition
		definition = definition.replaceAll(BRACKETED_TEXT_REGEX, "");
		definition = definition.replaceAll("  ", " ")
				.replaceAll(" \\.", "\\.")
				.replaceAll(" ,", ",").trim();
 		if (!definition.equals(conceptDefinitions.get(c))) {
			String beforeAfter = "BEFORE: " + conceptDefinitions.get(c) + "\nAFTER: " + definition;
			report(c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Removed 'see this/these term(s)'", beforeAfter);
		}

		String markupRemoved = definition.replaceAll("<[^>]+>", "");
		if (!definition.equals(markupRemoved)) {
			report(c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Removed <i> markup", definition);
		}
		definition = markupRemoved;

		if (!definition.endsWith(".")) {
			definition += ".";
		}

		return definition;
	}

	private void report(Concept c, Severity severity, ReportActionType action, String processingDetail, String rmStr) throws TermServerScriptException {
		List<Description> textDefinitions = c.getDescriptions(Acceptability.BOTH, DescriptionType.TEXT_DEFINITION, ActiveState.ACTIVE);
		String textDefn = textDefinitions.stream()
				.map(d -> d.getTerm())
				.collect(Collectors.joining(",\n"));
		String textDefnET = textDefinitions.stream()
				.findFirst()
				.map(d -> d.getEffectiveTime())
				.orElse("");
		report(c, severity, action, processingDetail, rmStr, textDefn, textDefnET);
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[REF_IDX_REFCOMPID]);
		boolean rmActive = lineItems[REF_IDX_ACTIVE].equals("1");
		String effectiveTime = lineItems[REF_IDX_EFFECTIVETIME];
		if (!rmActive) {
			c.addIssue("Orphanet map inactive");
		} else if (effectiveTime.compareTo("20160131") < 0) {
			c.addIssue("Orphanet map predates 20160131");
		}
		return Collections.singletonList(c);
	}

}

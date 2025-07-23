package org.ihtsdo.termserver.scripting.delta;

import com.google.common.io.Files;
import java.nio.charset.StandardCharsets;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ComponentAnnotationEntry;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class AddAttributionAnnotations extends DeltaGenerator implements ScriptConstants{

	private Concept annotationType = null;
	private String annotationStr = "Inserm Orphanet";
	private static final int BATCH_SIZE = 999999;

	Set<Concept> confirmedConcepts;
	Set<Concept> conceptsAnnotated = new HashSet<>();
	private int lastBatchSize = 0;

	private static final Logger LOGGER = LoggerFactory.getLogger(AddAttributionAnnotations.class);

	public static void main(String[] args) throws TermServerScriptException {
		AddAttributionAnnotations delta = new AddAttributionAnnotations();
		try {
			delta.newIdsRequired = false; // We'll only be modifying existing descriptions
			delta.init(args);
			delta.inputFileHasHeaderRow = true;
			delta.loadProjectSnapshot(false); //Need all descriptions loaded.
			delta.postInit(GFOLDER_ADHOC_UPDATES);
			delta.loadConfirmationFile();
			delta.annotationType = delta.gl.getConcept("1295448001"); // |Attribution (attribute)|
			delta.process();
			delta.createOutputArchive(false, delta.lastBatchSize);
		} finally {
			delta.finish();
		}
	}

	private void loadConfirmationFile() throws TermServerScriptException {
		if (getInputFile() != null) {
			confirmedConcepts = processFile().stream()
					.map(c -> (Concept)c)
					.collect(Collectors.toSet());
			LOGGER.info("Checking annotations against list of {} concepts", confirmedConcepts.size());
		} else {
			LOGGER.info("No confirmation file provided, processing full set");
		}
	}

	@Override
	protected void process() throws TermServerScriptException {
			//Work through all inactive concepts and check the inactivation indicator on all
			//active descriptions
			int conceptsInThisBatch = 0;
			for (Concept c : getCandidateConcepts()) {
				conceptsInThisBatch += addAnnotation(c);
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
			//Were any concepts confirmed for annotations that we didn't annotate?
			if (confirmedConcepts != null) {
				Set<Concept> unannotated = new HashSet<>(confirmedConcepts);
				unannotated.removeAll(conceptsAnnotated);
				for (Concept c : unannotated) {
					report(c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Concept confirmed for annotation, but was marked invalid or missing", "");
				}
			}

		lastBatchSize = conceptsInThisBatch;
	}

	private int addAnnotation(Concept c) throws TermServerScriptException {
		int changesMade = 0;
		//Do we have a text definition?
		boolean hasTextDef = !getTextDefinitionsSince(c, 2002).isEmpty();
		//Is it after 2015?
		boolean hasRecentTextDef = !getTextDefinitionsSince(c, 2015).isEmpty();
		String processingDetail = "Orphanet attribution added";
		ReportActionType action = ReportActionType.NO_CHANGE;
		String rmStr = "";
		if (c.hasIssues()) {
			processingDetail = c.getIssues(",\n");
		} else if (!hasTextDef) {
			processingDetail = "No text definition";
		} else if (!c.isActiveSafely()) {
			processingDetail = "Concept now inactive";
		} else if (!hasRecentTextDef) {
			processingDetail = "No recent text definition";
		} else if (!c.getComponentAnnotationEntries().isEmpty()) {
			processingDetail = "Already has annotation";
		} else if (confirmedConcepts != null && !confirmedConcepts.contains(c)) {
			processingDetail = "Concept not confirmed for annotation";
		} else {
			ComponentAnnotationEntry cae = ComponentAnnotationEntry.withDefaults(c, annotationType, annotationStr);
			c.addComponentAnnotationEntry(cae);
			rmStr = cae.toString();
			outputRF2(c);
			action = ReportActionType.REFSET_MEMBER_ADDED;
			changesMade++;
			countIssue(c);
			conceptsAnnotated.add(c);
		}
		report(c, Severity.LOW, action, processingDetail, rmStr);
		return changesMade;
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

	private List<Concept> getCandidateConcepts() throws TermServerScriptException {
		List<Concept> candidateConcepts = new ArrayList<>();
		int lineNum = 0;
		try {
			List<String> lines = Files.readLines(getInputFileOrThrow(2), StandardCharsets.UTF_8);
			for (String line : lines) {
				lineNum++;
				String[] columns = line.split(TAB);
				if (columns[IDX_ID].equals("id")) {
					//Skip the header line
					continue;
				}
				boolean rmActive = columns[REF_IDX_ACTIVE].equals("1");
				String sctid = columns[REF_IDX_REFCOMPID];
				Concept c = gl.getConcept(sctid);
				if (!rmActive) {
					c.addIssue("Orphanet map inactive");
				}
				candidateConcepts.add(gl.getConcept(sctid));
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to read input file at line " + lineNum, e);
		}
		return SnomedUtils.sort(candidateConcepts);
	}

	private List<Description> getTextDefinitionsSince(Concept c, int effectiveTime) {
		return c.getDescriptions().stream()
				.filter(d -> d.getType().equals(DescriptionType.TEXT_DEFINITION))
				.filter(d -> Integer.parseInt(d.getEffectiveTime()) > effectiveTime)
				.toList();
	}

}

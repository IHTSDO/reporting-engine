package org.ihtsdo.termserver.scripting.delta;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ComponentAnnotationEntry;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class AddAttributionAnnotations extends DeltaGenerator implements ScriptConstants{

	private Concept annotationType = null;
	private String annotationStr = "Inserm Orphanet";
	private final int BatchSize = 50;

	private static final Logger LOGGER = LoggerFactory.getLogger(AddAttributionAnnotations.class);

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		AddAttributionAnnotations delta = new AddAttributionAnnotations();
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"; //Ad-Hoc Batch Updates
			delta.newIdsRequired = false; // We'll only be modifying existing descriptions
			delta.init(args);
			//delta.getArchiveManager().setAllowStaleData(true);
			delta.loadProjectSnapshot(false); //Need all descriptions loaded.
			delta.postInit();
			delta.annotationType = delta.gl.getConcept("1295448001"); // |Attribution (attribute)|
			int lastBatchSize = delta.process();
			delta.createOutputArchive(false, lastBatchSize);
		} finally {
			delta.finish();
		}
	}

	private int process() throws ValidationFailure, TermServerScriptException, IOException {
		//Work through all inactive concepts and check the inactivation indicator on all
		//active descriptions
		int conceptsInThisBatch = 0;
		for (Concept c : getCandidateConcepts()) {
			conceptsInThisBatch += addAnnotation(c);
			if (conceptsInThisBatch >= BatchSize) {
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
		int changesMade = 0;
		//Do we have a text definition?
		boolean hasTextDef = getTextDefinitionsSince(c, 2002).size() > 0;
		//Is it after 2015?
		boolean hasRecentTextDef = getTextDefinitionsSince(c, 2015).size() > 0;
		String processingDetail = "Orphanet attribution added";
		ReportActionType action = ReportActionType.NO_CHANGE;
		String rmStr = "";
		String textDefn = "";
		String textDefnET = "";
		if (c.hasIssues()) {
			processingDetail = c.getIssues();
		} else if (!hasTextDef) {
			processingDetail = "No text definition";
		} else if (!c.isActive()) {
			processingDetail = "Concept now inactive";
		} else if (!hasRecentTextDef) {
			processingDetail = "No recent text definition";
		} else if (c.getComponentAnnotationEntries().size() > 0) {
			processingDetail = "Already has annotation";
		} else {
			ComponentAnnotationEntry cae = ComponentAnnotationEntry.withDefaults(c, annotationType, annotationStr);
			c.addComponentAnnotationEntry(cae);
			rmStr = cae.toString();
			outputRF2(c);
			action = ReportActionType.REFSET_MEMBER_ADDED;
			changesMade++;
			countIssue(c);
			List<Description> textDefinitions = c.getDescriptions(Acceptability.BOTH, DescriptionType.TEXT_DEFINITION, ActiveState.ACTIVE);
			textDefn = textDefinitions.stream()
					.map(d -> d.getTerm())
					.collect(Collectors.joining(",\n"));
			textDefnET = textDefinitions.stream()
					.findFirst()
					.map(d -> d.getEffectiveTime())
					.orElse("");
		}
		report(c, Severity.LOW, action, processingDetail, rmStr, textDefn, textDefnET);
		return changesMade;
	}

	private List<Concept> getCandidateConcepts() throws TermServerScriptException {
		List<Concept> candidateConcepts = new ArrayList<>();
		int lineNum = 0;
		try {
			List<String> lines = Files.readLines(getInputFile(), Charsets.UTF_8);
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
					c.setIssue("Orphanet map inactive");
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

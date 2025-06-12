package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class CreateConceptsDeltaDriven extends CreateConceptsDelta {

	List<ConceptRow> conceptsToCreate;
	private static final String SEMTAG = " (substance)";
	private static final String FSN_TERM_PREFIX = "Deoxyribonucleic acid of ";
	private static final String FSN_TERM_SUFFIX = "";
	private static final String PT_TERM_PREFIX = "";
	private static final String PT_TERM_SUFFIX = " DNA";

	private static final int BATCH_SIZE = 10;

	public static void main(String[] args) throws TermServerScriptException {
		new CreateConceptsDeltaDriven().standardExecutionWithIds(args);
	}

	@Override
	protected void init (String[] args) throws TermServerScriptException {
		super.init(args);
		targetModuleId = sourceModuleIds.iterator().next();
		//Load in the list of concepts to create from our input file
		processInputFile();
	}

	@Override
	public void process() throws TermServerScriptException {
		for (ConceptRow row : conceptsToCreate) {
			Concept c = new Concept(conIdGenerator.getSCTID());
			c.setActive(true);
			c.setDefinitionStatus(defStatus);
			c.setModuleId(targetModuleId);
			addFsnAndCounterpart(c, generateFSN(row.termBase), false, CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
			addDescription(c, DescriptionType.SYNONYM, generateSynonym(row.termBase), true, CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
			for (String synonym : row.synonyms) {
				addDescription(c, DescriptionType.SYNONYM, generateSynonym(synonym), false, CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
			}
			for (String parent: row.parents) {
				addRelationships(c, parent);
			}
			report(c, Severity.LOW, ReportActionType.CONCEPT_ADDED);
			SnomedUtils.setAllComponentsDirty(c, true);

			//We'll store the reference (eg the relevant LOINC Part) in the concept issues for output
			if (row.reference != null && !row.reference.isEmpty()) {
				c.clearIssues();
				c.addIssue(row.reference);
			}

			if (++conceptsInLastBatch >= BATCH_SIZE) {
				if (!dryRun) {
					createOutputArchive(true, conceptsInLastBatch);
					outputDirName = "output"; //Reset so we don't end up with _1_1_1
					initialiseOutputDirectory();
					initialiseFileHeaders();
				}
				gl.setAllComponentsClean();
				conceptsInLastBatch = 0;
			}
		}
	}

	private String generateFSN(String termBase) {
		return FSN_TERM_PREFIX + termBase + FSN_TERM_SUFFIX + SEMTAG;
	}

	private String generateSynonym(String termBase) {
		return PT_TERM_PREFIX + termBase + PT_TERM_SUFFIX;
	}

	private void processInputFile() throws TermServerScriptException {
		try (BufferedReader reader = new BufferedReader(new FileReader(getInputFile()))) {
			String line;
			boolean isFirstLine = true;
			conceptsToCreate = new ArrayList<>();
			while ((line = reader.readLine()) != null) {
				if (isFirstLine) {
					isFirstLine = false;
					continue; // skip header
				}
				String[] cols = line.split("\t");
				if (cols.length < 3) {
					throw new TermServerScriptException("Invalid line format: " + line);
				}
				ConceptRow row = new ConceptRow();
				row.termBase = cols[0].trim();
				row.synonyms = Arrays.stream(cols[1].split("(?=[A-Z])"))
				    .map(String::trim)
				    .filter(s -> !s.isEmpty())
				    .toList();
				row.parents = Arrays.stream(cols[2].split(","))
						.map(String::trim)
						.filter(s -> !s.isEmpty())
						.toList();
				row.reference=cols[3].trim();
				conceptsToCreate.add(row);
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to read input file", e);
		}
	}

	class ConceptRow {
		String termBase;
		List<String> synonyms;
		List<String> parents;
		String reference;
	}
}

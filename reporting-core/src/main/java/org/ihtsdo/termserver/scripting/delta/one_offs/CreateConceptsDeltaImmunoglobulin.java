package org.ihtsdo.termserver.scripting.delta.one_offs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.delta.CreateConceptsDelta;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CreateConceptsDeltaImmunoglobulin extends CreateConceptsDelta {

	private static final Logger LOGGER = LoggerFactory.getLogger(CreateConceptsDeltaImmunoglobulin.class);

	List<ConceptRow> conceptsToCreate;
	private static final String SEMTAG = " (substance)";
	private static final String FSN_TERM_PREFIX = "Immunoglobulin G4 antibody to ";
	private static final String FSN_TERM_SUFFIX = "";
	private static final String PT_TERM_PREFIX = "";
	private static final String PT_TERM_SUFFIX = " IgG4";

	private static final int BATCH_SIZE = 10;

	public static void main(String[] args) throws TermServerScriptException {
		new CreateConceptsDeltaImmunoglobulin().standardExecutionWithIds(args);
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
			createConcept(row);

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

	private void createConcept(ConceptRow row) throws TermServerScriptException {
		Concept c = new Concept(conIdGenerator.getSCTID());
		c.setActive(true);
		c.setDefinitionStatus(defStatus);
		c.setModuleId(targetModuleId);
		addFsnAndCounterpart(c, generateFSN(row.fsnBase), row.useFsnBaseAsPt, CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
		if (!row.useFsnBaseAsPt) {
			addDescription(c, DescriptionType.SYNONYM, generateSynonym(row.preferredTermBase), true, row.ptCS);
		}
		for (Synonym synonym : row.synonyms) {
			addDescription(c, DescriptionType.SYNONYM, generateSynonym(synonym.term), false, synonym.cs);
		}
		for (String parent: row.parents) {
			addRelationships(c, parent);
		}
		report(c, Severity.LOW, ReportActionType.CONCEPT_ADDED);
		SnomedUtils.setAllComponentsDirty(c, true);
		gl.registerConcept(c);

		//We'll store the reference (eg the relevant LOINC Part) in the concept issues for output
		if (row.reference != null && !row.reference.isEmpty()) {
			c.clearIssues();
			c.addIssue(row.reference);
		}
	}

	private String generateFSN(String termBase) {
		return FSN_TERM_PREFIX + termBase + FSN_TERM_SUFFIX + SEMTAG;
	}

	private String generateSynonym(String termBase) {
		return PT_TERM_PREFIX + termBase + PT_TERM_SUFFIX;
	}

	private void processInputFile() throws TermServerScriptException {
		LOGGER.info("Processing input file: {}", getInputFile());
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
				if (cols.length < 5) {
					throw new TermServerScriptException("Invalid line format: " + line);
				}
				ConceptRow row = new ConceptRow();
				row.fsnBase = cols[0].trim();
				determinePtAndSynonyms(row, cols);
				row.parents = Arrays.stream(cols[4].split(","))
						.map(String::trim)
						.filter(s -> !s.isEmpty())
						.toList();
				row.reference=cols[5].trim();
				conceptsToCreate.add(row);
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to read input file", e);
		}
	}

	private void determinePtAndSynonyms(ConceptRow row, String[] cols) {
		row.synonyms = new ArrayList<>();
		//If we have a common name, that's our PT
		if (!cols[1].isEmpty()) {
			row.preferredTermBase = cols[1].trim();
			//And add a basic form of the scientifc name
			Synonym scientific = new Synonym();
			scientific.term = cols[0];
			scientific.cs = CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
			row.synonyms.add(scientific);
		} else {
			row.preferredTermBase = cols[0].trim();
			row.ptCS = CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
		}

		//Add an extra synonym of "Anti-X" + PT_TERM_SUFFIX (added later)
		Synonym antiXSynonym = new Synonym();
		antiXSynonym.term = "Anti-" + cols[0];
		row.synonyms.add(antiXSynonym);
		addSynoymsIfPresent(row, cols[2].trim(), CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
		addSynoymsIfPresent(row, cols[3].trim(), CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
	}

	private void addSynoymsIfPresent(ConceptRow row, String col, CaseSignificance cs) {
		if (col != null && !col.isEmpty()) {
			List<Synonym> synonyms = Arrays.stream(col.split(","))
					.map(String::trim)
					.map(s -> {
						Synonym synonym = new Synonym();
						synonym.term = s;
						synonym.cs = cs;
						return synonym;
					})
					.toList();
			row.synonyms.addAll(synonyms);
		}
	}

	class ConceptRow {
		boolean useFsnBaseAsPt = false;
		CaseSignificance ptCS = CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE;
		String fsnBase;
		String preferredTermBase;
		List<Synonym> synonyms;
		List<String> parents;
		String reference;
	}

	class Synonym {
		String term;
		CaseSignificance cs = CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE;
	}
}

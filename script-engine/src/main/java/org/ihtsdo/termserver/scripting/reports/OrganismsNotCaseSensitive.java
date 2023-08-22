package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

public class OrganismsNotCaseSensitive extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(OrganismsNotCaseSensitive.class);

	private static final List<String> skip = Arrays.asList("Family", "Genus", "Order", "Infraorder", "Class", "Phylum",
			"Subclass", "Superorder", "Suborder", "Superfamily", "Subfamily", "Tribe", "Subtribe", "Superkingdom",
			"Shiga", "Human", "Extended", "Subgenus","Adult","Multiple", "Influenza", "Enterotoxigenic", "Egg",
			"Measles", "Form", "Subdivision", "Larva", "Enterohemorrhagic", "Enteropathogenic", "Enteroinvasive",
			"Infraclass", "Superclass", "Toxigenic", "Cyst", "Subspecies", "Subphylum", "Subkingdom", "Mammalian",
			"Rotavirus", "Equine", "Kingdom", "Domain", "Division"
	);

	public static void main(String[] args) throws TermServerScriptException, IOException {
		OrganismsNotCaseSensitive report = new OrganismsNotCaseSensitive();
		try {
			ReportSheetManager.targetFolderId = "1bwgl8BkUSdNDfXHoL__ENMPQy_EdEP7d";
			report.additionalReportColumns = "FSN, SemanticTag, Substance Description, Organism Description, Case Significance Mismatch";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			report.reportOrganismsInSubstances();
		} catch (Exception e) {
			LOGGER.info("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void reportOrganismsInSubstances() throws TermServerScriptException {
		int organismsChecked = 0;
		for (Concept organism : ORGANISM.getDescendents(NOT_SET)) {
			if (organism.getFSNDescription().getCaseSignificance().equals(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE)
				&& !startsWithSkippedWord(organism.getFsn())) {
				report(organism);
				countIssue(organism);
				organismsChecked++;
			}
		}
		LOGGER.debug("Organisms checked: " + organismsChecked);
	}

	private boolean startsWithSkippedWord(String fsn) {
		for (String skipWord : skip) {
			if (fsn.startsWith(skipWord)) {
				return true;
			}
		}
		return false;
	}

}

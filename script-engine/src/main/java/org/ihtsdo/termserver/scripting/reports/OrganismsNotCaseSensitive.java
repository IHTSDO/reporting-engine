package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

public class OrganismsNotCaseSensitive extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(OrganismsNotCaseSensitive.class);

	private static final List<String> skip = Arrays.asList("Family", "Genus", "Order", "Infraorder", "Class", "Phylum",
			"Subclass", "Superorder", "Suborder", "Superfamily", "Subfamily", "Tribe", "Subtribe", "Superkingdom",
			"Shiga", "Human", "Extended", "Subgenus","Adult","Multiple", "Influenza", "Enterotoxigenic", "Egg",
			"Measles", "Form", "Subdivision", "Larva", "Enterohemorrhagic", "Enteropathogenic", "Enteroinvasive",
			"Infraclass", "Superclass", "Toxigenic", "Cyst", "Subspecies", "Subphylum", "Subkingdom", "Mammalian",
			"Rotavirus", "Equine", "Kingdom", "Domain", "Division",
			"Carbapenem", "Yeast", "Linezolid", "Trophozoite", "Ballistoconidium", "Fluoroquinolone", "Aerobic",
			"Methicillin", "Nymph", "Arthrospore", "Vancomycin", "Amastigote", "Mold", "Metallo-beta-lactamase",
			"Proglottid", "Small-colony-forming", "Intraspecies", "Arthroconidium", "Vesicular", "Sporozoite",
			"Spherule", "Filariform", "Hemolytically", "Enteroaggregative", "Diffuse", "Carbapenemase-producing",
			"Infertile", "Chlamydoconidium", "Oocyst", "Helical", "Beta-hemolytic", "Nontoxigenic", "Curved",
			"Catalase", "Anaerobic", "Cephalosporin", "Non group", "Saccharolytic", "Gamma-hemolytic", "Clarithromycin",
			"Sexual", "Atypical", "Multidrug-resistant", "Infrakingdom", "Merozoite", "Mature", "Non-mucoid", "Pandemic",
			"Non-multiple", "Fertile", "Rapid", "Rhabditiform", "Fermentative", "Hydrogen", "Provisional", "Ookinete",
			"Sporocyst", "Schizont", "Procercoid", "Developing", "Asaccharolytic", "Pseudoalteromonas", "Annelloconidium",
			"Penicillinase-producing", "Blastoconidium", "Asexual", "Plerocercoid", "Gametocyte", "Vibrio", "Isometric",
			"Large-colony-forming", "Zygote", "Porcine", "Microgametocyte", "Extensively", "Sporont", "Penicillin",
			"Fusiform", "Decorticated", "Reptilian", "Non-fermentative", "Spore", "Serogroup", "Basidiospore",
			"Trypomastigote", "Aerotolerant", "Gamete", "Epimastigote", "Glycopeptide", "Coagulase", "Pupa", "Ampicillin",
			"Borderline", "Promastigote", "Imago", "Mitosporic", "Fastidious", "Coryneform", "Domesticated", "Immature",
			"Hypnozoite", "Conidium", "Non-motile", "Spectinomycin-resistant", "Phialoconidium", "Meront", "Macroconidium",
			"Alpha-hemolytic", "Corticated", "Tobacco", "Tetracycline-resistant", "Microconidium", "Catalase-negative",
			"Tissue", "Ascospore", "Nutritionally", "Coagulase-negative", "Multilocular", "Attenuated", "Magnetotactic",
			"Merozoite", "Macrogametocyte", "Elastase-producing", "Hepatitis"
	);

	public static void main(String[] args) throws TermServerScriptException {
		OrganismsNotCaseSensitive report = new OrganismsNotCaseSensitive();
		try {
			ReportSheetManager.setTargetFolderId("1bwgl8BkUSdNDfXHoL__ENMPQy_EdEP7d");
			report.additionalReportColumns = "FSN, SemanticTag, ";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			report.reportOrganismsInSubstances();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report", e);
		} finally {
			report.finish();
		}
	}

	private void reportOrganismsInSubstances() throws TermServerScriptException {
		int organismsChecked = 0;
		report(PRIMARY_REPORT, "Specifically these are organisms where the FSN is cI ie initial character case insensitive");
		report(PRIMARY_REPORT, "");

		for (Concept organism : ORGANISM.getDescendants(NOT_SET)) {
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

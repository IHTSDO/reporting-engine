package org.ihtsdo.termserver.scripting.reports;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * SUBST-287 Ensure case sensitivity correct: organism in term - antibody and antigen (et al.)
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrganismsInSubstances extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(OrganismsInSubstances.class);

	Map<String, Description> organismNames;
	Set<Concept> substancesUsedInProducts;
	List<String> skipOrganisms;
	
	public static void main(String[] args) throws TermServerScriptException {
		OrganismsInSubstances report = new OrganismsInSubstances();
		try {
			ReportSheetManager.setTargetFolderId("1bwgl8BkUSdNDfXHoL__ENMPQy_EdEP7d");
			report.additionalReportColumns = "FSN, SemanticTag, Substance Description, Organism Description, Case Significance Mismatch";
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

	@Override
	public void postInit() throws TermServerScriptException {
		substancesUsedInProducts = DrugUtils.getSubstancesUsedInProducts();
		skipOrganisms = new ArrayList<>();
		skipOrganisms.add("nit");
		skipOrganisms.add("beta");
		skipOrganisms.add("ox");
		skipOrganisms.add("bee");
		skipOrganisms.add("virus");
		skipOrganisms.add("guan");
		skipOrganisms.add("mus");
		skipOrganisms.add("sus");
		skipOrganisms.add("pan");
		skipOrganisms.add("olm");
		skipOrganisms.add("asp");
		skipOrganisms.add("ani");
		
		LOGGER.info("Mapping organism names");
		organismNames = new HashMap<>();
		for (Concept organism : ORGANISM.getDescendants(NOT_SET)) {
			for (Description d : organism.getDescriptions(ActiveState.ACTIVE)) {
				String storeTerm = d.getTerm().toLowerCase();
				if (d.getType().equals(DescriptionType.FSN)) {
					storeTerm = SnomedUtils.deconstructFSN(storeTerm)[0];
				}
				if (!organismNames.containsKey(storeTerm) && !skipOrganisms.contains(storeTerm)) {
					organismNames.put(storeTerm, d);
					//We're seeing substances using Xvirus with no space, so store them
					if (storeTerm.contains(" virus")) {
						storeTerm = storeTerm.replaceAll(" virus", "virus");
						organismNames.put(storeTerm, d);
					}
				}
			}
		}
		super.postInit();
	}

	private void reportOrganismsInSubstances() throws TermServerScriptException {
		LOGGER.info("Looking for organisms used in substances used in products");
		
		for (Concept substance : substancesUsedInProducts) {
			//Check all preferred terms
			for (Description d : substance.getDescriptions(Acceptability.PREFERRED, null, ActiveState.ACTIVE)) {
				//Add a space to beginning and end so we match the start of words, but allow for variants
				String term = " " + d.getTerm().toLowerCase();
				boolean reported = false;
				for (Map.Entry<String, Description> organismEntry : organismNames.entrySet()) {
					if (term.contains(" " + organismEntry.getKey())) {
						String csMismatch = "Y";
						if (d.getCaseSignificance().equals(organismEntry.getValue().getCaseSignificance())) {
							csMismatch = "N";
						}
						report(substance, d, organismEntry.getValue(), csMismatch);
						reported = true;
					}
				}
				//Only report once per substance
				if (reported) {
					break;
				}
			}
		}
	}

}

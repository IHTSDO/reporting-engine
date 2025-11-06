package org.ihtsdo.termserver.scripting.reports.drugs;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SUBST-267
 * Report to find dispositions that are not used by a substance disposition groupers
 * Identified by having "mechanism of action" in term.
 */
public class MissingDispositionSubstanceGroupers extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(MissingDispositionSubstanceGroupers.class);

	Map<Concept, Set<Concept>> dispositionSubstanceMap;
	Map<Concept, Concept> dispositionGrouperMap;
	
	public static void main(String[] args) throws TermServerScriptException {
		MissingDispositionSubstanceGroupers report = new MissingDispositionSubstanceGroupers();
		try {
			report.additionalReportColumns="FSN,Used by";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postLoadInit();
			report.runMissingDispositionsReport();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report", e);
		} finally {
			report.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		//Populate the disposition / substance map
		dispositionSubstanceMap = new HashMap<>();
		dispositionGrouperMap = new HashMap<>();
		for (Concept c : SUBSTANCE.getDescendants(NOT_SET)) {
			for (Concept disposition : SnomedUtils.getTargets(c, new Concept[] {HAS_DISPOSITION}, CharacteristicType.STATED_RELATIONSHIP)) {
				populateDispositionUsedByConcept(c, disposition);
			}
		}
		super.postInit();
	}

	private void populateDispositionUsedByConcept(Concept c, Concept disposition) {
		//Have we seen this disposition before?
		Set<Concept> usedBy = dispositionSubstanceMap.computeIfAbsent(disposition, k -> new HashSet<>());
		usedBy.add(c);

		//Is this a grouper concept?
		if (c.getFsn().contains("mechanism of action")) {
			if (dispositionGrouperMap.containsKey(disposition)) {
				//Ignore the one with multiple dispositions
				if (c.getFsn().contains(" and ")) {
					LOGGER.warn("Ignoring multi-disposition substance {} in favour of {}", c, dispositionGrouperMap.get(disposition));
				} else if (dispositionGrouperMap.get(disposition).getFsn().contains(" and ")) {
					LOGGER.warn("Ignoring multi-disposition substance {} in favour of {}", dispositionGrouperMap.get(disposition), c);
					dispositionGrouperMap.put(disposition, c); //Replace existing mapping
				} else {
					LOGGER.warn("{} has already been given grouper: {} now also {}", disposition,  dispositionGrouperMap.get(disposition), c);
				}
			} else {
				dispositionGrouperMap.put(disposition, c);
			}
		}
	}

	private void runMissingDispositionsReport() throws TermServerScriptException {
		Concept topDisposition = gl.getConcept("726711005"); // |Disposition (disposition)|
		for (Concept disposition : topDisposition.getDescendants(NOT_SET)) {
			//Does this disposition have a grouper concept?
			if (!dispositionGrouperMap.containsKey(disposition)) {
				//Are there substances using this disposition?  List them
				String usedByStr = "";
				if (dispositionSubstanceMap.containsKey(disposition)) {
					Set<Concept> usedBy = dispositionSubstanceMap.get(disposition);
					usedByStr = usedBy.stream().map(Concept::toString)
								.collect(Collectors.joining(", \n"));
				}
				incrementSummaryInformation("Missing groupers reported");
				report(disposition, usedByStr);
			} else {
				LOGGER.debug("{} -> {}", disposition, dispositionGrouperMap.get(disposition));
			}
			incrementSummaryInformation("Dispositions checked");
		}
	}
	
}

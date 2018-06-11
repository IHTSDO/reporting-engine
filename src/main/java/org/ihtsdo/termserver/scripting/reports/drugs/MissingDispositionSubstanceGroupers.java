package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * SUBST-267
 * Report to find dispositions that are not used by a substance disposition groupers
 * Identified by having "mechanism of action" in term.
 */
public class MissingDispositionSubstanceGroupers extends TermServerReport {
	
	List<Component> concepts;
	Concept attributeType;
	Map<Concept, Set<Concept>> dispositionSubstanceMap;
	Map<Concept, Concept> dispositionGrouperMap;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		MissingDispositionSubstanceGroupers report = new MissingDispositionSubstanceGroupers();
		try {
			report.additionalReportColumns="FSN,Used by";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postLoadInit();
			report.runMissingDispositionsReport();
		} catch (Exception e) {
			info("Failed to produce MissingAttributeReport due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		//Populate the disposition / substance map
		dispositionSubstanceMap = new HashMap<>();
		dispositionGrouperMap = new HashMap<>();
		for (Concept c : SUBSTANCE.getDescendents(NOT_SET)) {
			for (Concept disposition : SnomedUtils.getTargets(c, new Concept[] {HAS_DISPOSITION}, CharacteristicType.STATED_RELATIONSHIP)) {
				//Have we seen this disposition before?
				Set<Concept> usedBy = dispositionSubstanceMap.get(disposition);
				if (usedBy == null) {
					usedBy = new HashSet<>();
					dispositionSubstanceMap.put(disposition, usedBy);
				}
				usedBy.add(c);
				
				//Is this a grouper concept?
				if (c.getFsn().contains("mechanism of action")) {
					//debug ("Adding grouper " + c + " to disposition " + disposition);
					if (dispositionGrouperMap.containsKey(disposition)) {
						//Ignore the one with multiple dispositions
						if (c.getFsn().contains(" and ")) {
							warn ("Ignoring multi-disposition substance " + c + " in favour of " + dispositionGrouperMap.get(disposition));
						} else if (dispositionGrouperMap.get(disposition).getFsn().contains(" and ")) {
							warn ("Ignoring multi-disposition substance " + dispositionGrouperMap.get(disposition) + " in favour of " + c);
							dispositionGrouperMap.put(disposition, c); //Replace existing mapping
						} else {
						 warn (disposition + " has already been given grouper: " + dispositionGrouperMap.get(disposition) + " now also " + c);
						}
					} else {
						dispositionGrouperMap.put(disposition, c);
					}
				}
			}
		}
	}

	private void runMissingDispositionsReport() throws TermServerScriptException {
		Concept topDisposition = gl.getConcept("726711005"); // |Disposition (disposition)|
		for (Concept disposition : topDisposition.getDescendents(NOT_SET)) {
			//Does this disposition have a grouper concept?
			if (!dispositionGrouperMap.containsKey(disposition)) {
				//Are there substances using this disposition?  List them
				String usedByStr = "";
				if (dispositionSubstanceMap.containsKey(disposition)) {
					Set<Concept> usedBy = dispositionSubstanceMap.get(disposition);
					usedByStr = usedBy.stream().map(c -> c.toString())
								.collect(Collectors.joining(", \n"));
				}
				incrementSummaryInformation("Missing groupers reported");
				report (disposition, usedByStr);
			} else {
				debug (disposition + " -> " + dispositionGrouperMap.get(disposition));
			}
			incrementSummaryInformation("Dispositions checked");
		}
	}
	
}

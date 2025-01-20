package org.ihtsdo.termserver.scripting.reports;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Reports if concepts could potentially be promimal primitive modelled ie FD to top of hierarchy
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProximatePrimitiveModellingPossibleReport extends TermServerScript{

	private static final Logger LOGGER = LoggerFactory.getLogger(ProximatePrimitiveModellingPossibleReport.class);

	String publishedArchive;
	String[] hierarchies = {"64572001"}; //Disease (disorder)
	
	public static void main(String[] args) throws TermServerScriptException {
		ProximatePrimitiveModellingPossibleReport report = new ProximatePrimitiveModellingPossibleReport();
		try {
			report.additionalReportColumns = " Sem_Tag, alreadyModelledCorrectly, FDToTop, immedPrimParent, notImmediatePrimitive";
			report.init(args);
			report.loadProjectSnapshot(true);  //Load FSNs only
			report.reportIntermediatePrimitives();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report", e);
		} finally {
			report.finish();
		}
	}

	private void reportIntermediatePrimitives() throws TermServerScriptException {
		for (String hiearchySCTID : hierarchies) {

			int fdToTopCount = 0;
			int immedPrimParentCount = 0;
			int alreadyModelledCorrectlyCount = 0;
			int notImmediatePrimitiveCount = 0;
			
			Concept hierarchy = gl.getConcept(hiearchySCTID);
			Set<Concept> outsideSubHierarchy = hierarchy.getAncestors(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, true);
			Set<Concept> allHierarchy = hierarchy.getDescendants(NOT_SET, CharacteristicType.STATED_RELATIONSHIP);
			Set<Concept> allActiveFD = filterActiveFD(allHierarchy);
			LOGGER.info (hierarchy + " - " + allActiveFD.size() + "(FD) / " + allHierarchy.size() + "(Active)");
			
			for (Concept thisConcept : allActiveFD) {
				boolean alreadyModelledCorrectly = false;
				boolean fdToTop = false;
				boolean immedPrimParent = false;
				boolean notImmediatePrimitive = false;
				Set<Concept>parents = thisConcept.getParents(CharacteristicType.STATED_RELATIONSHIP); 
				//If we have a single stated parent of disease, then we're modelled correctly
				if (parents.size() == 1 && parents.iterator().next().getConceptId().equals(hiearchySCTID)) {
					alreadyModelledCorrectlyCount++;
					alreadyModelledCorrectly = true;
				} else {
					//See if ancestors up to subhierarchy start (remove outside of that) are all fully defined
					Set<Concept> ancestors = thisConcept.getAncestors(NOT_SET, CharacteristicType.STATED_RELATIONSHIP, false);
					ancestors.removeAll(outsideSubHierarchy);
					if (allFD(ancestors)) {
						fdToTopCount++;
						fdToTop = true;
					} else {
						if (!allFD(parents)) {
							immedPrimParentCount ++;
							immedPrimParent = true;
						} else {
							notImmediatePrimitiveCount++;
							notImmediatePrimitive = true;
						}
					}
				}
				report(thisConcept, SnomedUtils.deconstructFSN(thisConcept.getFsn())[1], alreadyModelledCorrectly, fdToTop, immedPrimParent, notImmediatePrimitive);
			}
			LOGGER.info("Already modelled correctly: {}", alreadyModelledCorrectlyCount);
			LOGGER.info("Fully defined to subhierarchy top: {}", fdToTopCount);
			LOGGER.info("Has immediate primitive parent: {}",  immedPrimParentCount);
			LOGGER.info("Not-immediate primitive ancestor: {}",  notImmediatePrimitiveCount);
		}
		
	}

	private boolean allFD(Collection<Concept> concepts) {
		boolean allFD = true;
		for (Concept concept : concepts) {
			if (!concept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				allFD = false;
				break;
			}
		}
		return allFD;
	}

	private Set<Concept> filterActiveFD(Set<Concept> fullSet) {
		Set <Concept> activeConcepts = new HashSet<>();
		for (Concept thisConcept : fullSet ) {
			if (thisConcept.isActiveSafely() && thisConcept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				activeConcepts.add(thisConcept);
			}
		}
		return activeConcepts;
	}

	protected void report(Concept c, String semtag, boolean one, boolean two, boolean three, boolean four) throws TermServerScriptException {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn().replace(",", "") + QUOTE_COMMA_QUOTE +
						semtag + QUOTE_COMMA +
						one + COMMA +
						two + COMMA + 
						three + COMMA +
						four;
		writeToReportFile(line);
	}

	@Override
	protected void init(String[] args) throws TermServerScriptException {
		super.init(args);
		
		for (int x=0; x<args.length; x++) {
			if (args[x].equals("-z")) {
				publishedArchive = args[++x];
			}
		}
		String hierarchiesStr = StringUtils.join(hierarchies,",");
		print ("Concepts in which Hierarchies? [" + hierarchiesStr + "]: ");
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			hierarchies = response.split(",");
		}
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return Collections.singletonList(gl.getConcept(lineItems[0]));
	}
}

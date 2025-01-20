package org.ihtsdo.termserver.scripting.reports;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Reports all concepts in a hierarchy that are used in the definition of other concepts.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequiringProxPrimModellingReport extends TermServerReport{

	private static final Logger LOGGER = LoggerFactory.getLogger(RequiringProxPrimModellingReport.class);

	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	String publishedArchive;
	String[] hierarchies = {"64572001"}; //Disease (disorder)
	
	public static void main(String[] args) throws TermServerScriptException {
		RequiringProxPrimModellingReport report = new RequiringProxPrimModellingReport();
		try {
			report.init(args);
			report.loadProjectSnapshot(true);  //Load FSNs only
			boolean reportAll = true; //Report all concepts whether they require remodelling or not
			report.reportRequiringProxPrimModelling(reportAll);
		} catch (Exception e) {
			LOGGER.info("Report failed due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void reportRequiringProxPrimModelling(boolean reportAll) throws TermServerScriptException {
		for (String hiearchySCTID : hierarchies) {
			int ok = 0;
			int multipleParentsCount = 0;
			int noDifferentiaCount = 0;
			int fdParentCount = 0;
			int requireProxPrimModellingCount = 0;
			Concept hierarchy = gl.getConcept(hiearchySCTID);
			Set<Concept> allHierarchy = hierarchy.getDescendants(NOT_SET, CharacteristicType.STATED_RELATIONSHIP);
			Set<Concept> allActiveFD = filterActiveFD(allHierarchy);
			LOGGER.info (hierarchy + " - " + allActiveFD.size() + "(FD) / " + allHierarchy.size() + "(Active)");
			for (Concept thisConcept : allActiveFD) {
				Set<Concept> parents = thisConcept.getParents(CharacteristicType.STATED_RELATIONSHIP);
				boolean hasFDParent = false;
				boolean noDifferentia = false;
				boolean multipleParents = false;
				for (Concept thisParent : parents) {
					if (thisParent.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
						hasFDParent = true;
						fdParentCount++;
						break;
					}
				}
				
				Set<Relationship> attributes = thisConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
				if (attributes.size() == 0) {
					noDifferentia = true;
					noDifferentiaCount++;
				}
				
				if (parents.size() > 1) {
					multipleParents = true;
					multipleParentsCount++;
				}
				
				if (reportAll || hasFDParent || noDifferentia || multipleParents) {
					requireProxPrimModellingCount++;
					report(thisConcept, SnomedUtils.deconstructFSN(thisConcept.getFsn())[1],hasFDParent,noDifferentia,multipleParents);
				} else {
					ok++;
				}
			}
			LOGGER.info("\tHas FD Parent: " + fdParentCount);
			LOGGER.info("\tHas no differentia: " + noDifferentiaCount);
			LOGGER.info("\tHas multiple parents: " + multipleParentsCount);
			LOGGER.info("\tRequires remodelling: " + requireProxPrimModellingCount);
			LOGGER.info("\tIs OK: " + ok);
		}
		
	}

	private Set<Concept> filterActiveFD(Set<Concept> fullSet) {
		Set <Concept> activeConcepts = new HashSet<Concept>();
		for (Concept thisConcept : fullSet ) {
			if (thisConcept.isActive() && thisConcept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				activeConcepts.add(thisConcept);
			}
		}
		return activeConcepts;
	}

	protected void report(Concept c, String semtag, boolean hasFDParent, boolean noDifferentia, boolean multipleParents) throws TermServerScriptException {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn().replace(",", "") + QUOTE_COMMA_QUOTE +
						semtag + QUOTE_COMMA +
						hasFDParent + COMMA + 
						noDifferentia + COMMA +
						multipleParents;
		writeToReportFile(line);
	}
	
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
		
		writeToReportFile ("Concept, FSN, Sem_Tag, hasFDParent,noDifferentia,multipleParents");
	}

}

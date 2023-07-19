package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Reports all concepts using Prox Prim modelling that have more groups in the 
 * inferred form, than the stated
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncreasedProxPrimInferredComplexityReport extends TermServerScript{

	private static Logger LOGGER = LoggerFactory.getLogger(IncreasedProxPrimInferredComplexityReport.class);

	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	GraphLoader gl = GraphLoader.getGraphLoader();
	String publishedArchive;
	String[] hierarchies = {"64572001"}; //Disease (disorder)
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		IncreasedProxPrimInferredComplexityReport report = new IncreasedProxPrimInferredComplexityReport();
		try {
			report.init(args);
			report.loadProjectSnapshot(true);  //Load FSNs only
			report.reportIncreasedComplexity();
		} catch (Exception e) {
			LOGGER.info("Report failed due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void reportIncreasedComplexity() throws TermServerScriptException {
		for (String hiearchySCTID : hierarchies) {
			Concept hierarchy = gl.getConcept(hiearchySCTID);
			Set<Concept> allHierarchy = hierarchy.getDescendents(NOT_SET, CharacteristicType.STATED_RELATIONSHIP);
			Set<Concept> allActiveFD = filterActiveFD(allHierarchy);
			LOGGER.info (hierarchy + " - " + allActiveFD.size() + "(FD) / " + allHierarchy.size() + "(Active)");
			
			for (Concept thisConcept : allActiveFD) {
				Set<Concept>parents = thisConcept.getParents(CharacteristicType.STATED_RELATIONSHIP); 
				//If we have a single stated parent of disease, then we're modelled correctly
				if (parents.size() == 1 && parents.iterator().next().getConceptId().equals(hiearchySCTID)) {
					//How many groups do we have in the stated and inferred forms?
					int statedGroups = countGroups(thisConcept, CharacteristicType.STATED_RELATIONSHIP);
					int inferredGroups = countGroups(thisConcept, CharacteristicType.INFERRED_RELATIONSHIP);
					LOGGER.info (thisConcept + ":  s=" + statedGroups + ", i=" + inferredGroups);
					if (inferredGroups > statedGroups) {
						report(thisConcept, SnomedUtils.deconstructFSN(thisConcept.getFsn())[1]);
					}
				}
			}
		}
		
	}

	private int countGroups(Concept c, CharacteristicType cType) {
		Set<Integer> groupNumbersActive = new HashSet<Integer>();
		Set<Relationship> attributes = c.getRelationships(cType, ActiveState.ACTIVE) ;
		for (Relationship r : attributes){
			Integer groupNum = new Integer((int)r.getGroupId());
			if (!groupNumbersActive.contains(groupNum)) {
				groupNumbersActive.add(groupNum);
			}
		}
		return groupNumbersActive.size();
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

	protected void report (Concept c, String semtag) throws TermServerScriptException {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn().replace(",", "") + QUOTE_COMMA_QUOTE +
						semtag + QUOTE;
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
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return Collections.singletonList(gl.getConcept(lineItems[0]));
	}
}

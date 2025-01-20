package org.ihtsdo.termserver.scripting.reports;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Reports concepts that are primitive, and have both fully defined ancestors and descendants 
 * */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IntermediatePrimitivesReport extends TermServerReport{

	private static final Logger LOGGER = LoggerFactory.getLogger(IntermediatePrimitivesReport.class);

	List<Concept> topLevelHierarchies;
	CharacteristicType targetCharType = CharacteristicType.STATED_RELATIONSHIP;

	public static void main(String[] args) throws TermServerScriptException {
		IntermediatePrimitivesReport report = new IntermediatePrimitivesReport();
		try {
			report.additionalReportColumns = "semanticTag, hasImmediateSDParent, hasImmediateSDChild, hasAllParentsSD, hasAllSDChildren";
			report.init(args);
			report.loadProjectSnapshot(true);  //just FSNs
			report.getTopLevelHierarchies();
			report.reportIntermediatePrimitives();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report", e);
		} finally {
			report.finish();
		}
	}
	
	private void getTopLevelHierarchies() throws TermServerScriptException {
		Concept rootConcept = gl.getConcept(SCTID_ROOT_CONCEPT.toString());
		topLevelHierarchies = new ArrayList<Concept>(rootConcept.getDescendants(IMMEDIATE_CHILD));
		//Sort by FSN
		Collections.sort(topLevelHierarchies, new Comparator<Concept>() {
			@Override
			public int compare(Concept c1, Concept c2) {
				return c1.getFsn().compareTo(c2.getFsn());
			}
		});
	}

	private void reportIntermediatePrimitives() throws TermServerScriptException {
		int rowsReported = 0;
		LOGGER.info("Scanning all concepts...");
		//Work through all top level hierarchies and list semantic tags along with their counts
		for (Concept thisHierarchy : topLevelHierarchies) {
			int hierarchyIpCount = 0;
			Set<Concept> descendants = thisHierarchy.getDescendants(NOT_SET, targetCharType);
			for (Concept c : descendants) {
				if (c.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
					hierarchyIpCount += checkConceptForIntermediatePrimitive(c);
				}
			}
			LOGGER.info(simpleName(thisHierarchy.getConceptId()) + ": " + hierarchyIpCount + " / " + descendants.size());
			rowsReported += hierarchyIpCount;
		}
		addSummaryInformation("Concepts checked", gl.getAllConcepts().size());
		addSummaryInformation("Rows reported", rowsReported);
	}

	private int checkConceptForIntermediatePrimitive(Concept c) throws TermServerScriptException {
		//Do we have both ancestor and descendant fully defined concepts?
		Set<Concept> descendants = c.getDescendants(NOT_SET, targetCharType);
		boolean hasFdDescendants = containsFdConcept(descendants);
		if (hasFdDescendants && containsFdConcept(c.getAncestors(NOT_SET, targetCharType, false))) {
			//This is an intermediate primitive, but does it have immediately close SD concepts?
			boolean hasImmediateSDParent = containsFdConcept(c.getParents(targetCharType));
			boolean hasImmediateSDChild = containsFdConcept(c.getChildren(targetCharType));
			boolean hasAllParentsSD = hasAllParentsSD(c);
			boolean hasAllSDChildren = hasAllSDChildren(c);
			report(c, SnomedUtils.deconstructFSN(c.getFsn())[1],
					(hasImmediateSDParent?"Yes":"No"),
					(hasImmediateSDChild?"Yes":"No"),
					(hasAllParentsSD?"Yes":"No"),
					(hasAllSDChildren?"Yes":"No"));
			return 1;
		}
		return 0;
	}

	private boolean hasAllParentsSD(Concept c) {
		for (Concept parent : c.getParents(targetCharType)) {
			if (parent.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
				return false;
			}
		}
		return true;
	}
	
	private boolean hasAllSDChildren(Concept c) {
		for (Concept child : c.getChildren(targetCharType)) {
			if (child.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
				return false;
			}
		}
		return true;
	}

	private boolean containsFdConcept(Collection<Concept> concepts) {
		for (Concept c : concepts) {
			if (c.isActive() && c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				return true;
			}
		}
		return false;
	}

	private String simpleName(String sctid) throws TermServerScriptException {
		Concept c = gl.getConcept(sctid);
		return SnomedUtils.deconstructFSN(c.getFsn())[0];
	}
	
}

package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Reports concepts that are primitive, and have both fully defined ancestors and descendants 
 * */
public class IntermediatePrimitivesReport extends TermServerReport{
	
	List<Concept> topLevelHierarchies;
	CharacteristicType targetCharType = CharacteristicType.STATED_RELATIONSHIP;
	//CharacteristicType targetCharType = CharacteristicType.INFERRED_RELATIONSHIP;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		IntermediatePrimitivesReport report = new IntermediatePrimitivesReport();
		try {
			report.additionalReportColumns = "semanticTag, hasImmediateSDParent, hasImmediateSDChild, hasAllParentsSD, hasAllSDChildren";
			report.init(args);
			report.loadProjectSnapshot(true);  //just FSNs
			report.getTopLevelHierarchies();
			report.reportIntermediatePrimitives();
		} catch (Exception e) {
			info("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}
	
	private void getTopLevelHierarchies() throws TermServerScriptException {
		Concept rootConcept = gl.getConcept(SCTID_ROOT_CONCEPT.toString());
		topLevelHierarchies = new ArrayList<Concept>(rootConcept.getDescendents(IMMEDIATE_CHILD));
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
		info ("Scanning all concepts...");
		//Work through all top level hierarchies and list semantic tags along with their counts
		for (Concept thisHierarchy : topLevelHierarchies) {
			int hierarchyIpCount = 0;
			Set<Concept> descendents = thisHierarchy.getDescendents(NOT_SET, targetCharType);
			for (Concept c : descendents) {
				if (c.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
					hierarchyIpCount += checkConceptForIntermediatePrimitive(c);
				}
			}
			info(simpleName(thisHierarchy.getConceptId()) + ": " + hierarchyIpCount + " / " + descendents.size());
			rowsReported += hierarchyIpCount;
		}
		addSummaryInformation("Concepts checked", gl.getAllConcepts().size());
		addSummaryInformation("Rows reported", rowsReported);
	}

	private int checkConceptForIntermediatePrimitive(Concept c) throws TermServerScriptException {
		//Do we have both ancestor and descendant fully defined concepts?
		Set<Concept> descendants = c.getDescendents(NOT_SET, targetCharType);
		boolean hasFdDescendants = containsFdConcept(descendants);
		if (hasFdDescendants && containsFdConcept(c.getAncestors(NOT_SET, targetCharType, false))) {
			//This is an intermediate primitive, but does it have immediately close SD concepts?
			boolean hasImmediateSDParent = containsFdConcept(c.getParents(targetCharType));
			boolean hasImmediateSDChild = containsFdConcept(c.getChildren(targetCharType));
			boolean hasAllParentsSD = hasAllParentsSD(c);
			boolean hasAllSDChildren = hasAllSDChildren(c);
			report (c, SnomedUtils.deconstructFSN(c.getFsn())[1],
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

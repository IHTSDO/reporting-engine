package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * QI-25
 * Find concepts in the subhierarchy which have an inferred parent that is _outside_ of the 
 * ie where the subhierarchy is not a neat triangle, but is seeping into other subhierarchies.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrossHierarchyLeakage extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(CrossHierarchyLeakage.class);

	Concept subHierarchy;
	
	public static void main(String[] args) throws TermServerScriptException {
		CrossHierarchyLeakage report = new CrossHierarchyLeakage();
		try {
			//report.additionalReportColumns = "FSN in Target Hierarchy, Parent in other hierarchy, Parent in Target Hierarchy";
			//report.additionalReportColumns = "FSN, Parents";
			
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postLoadInit();
			report.runCrossHierarchyLeakageReport();
			//report.runMultipleParentsReport();
		} catch (Exception e) {
			LOGGER.info("Failed to produce MissingAttributeReport due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept("307824009"); // |Administrative statuses (finding)|
		//subHierarchy = gl.getConcept("419891008"); // |Record artifact (record artifact)|
	}

	private void runCrossHierarchyLeakageReport() throws TermServerScriptException {
		for (Concept c : gl.getDescendantsCache().getDescendantsOrSelf(subHierarchy)) {
			Concept parentInHierarchy = null;
			//Check all my inferred parents for concepts in target hierarchy
			for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
				if (gl.getDescendantsCache().getDescendantsOrSelf(subHierarchy).contains(parent)) {
					parentInHierarchy = parent;
				}
			}
			
			//Check all my inferred parents for concepts in other hierarchies
			for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
				boolean leakingParentFound = false;
				if (!gl.getDescendantsCache().getDescendantsOrSelf(subHierarchy).contains(parent)) {
					report(c, parent, parentInHierarchy);
					leakingParentFound = true;
				}
				if (leakingParentFound) {
					incrementSummaryInformation("Leaking concept identified");
				}
			}
		}
	}
	
	private void runMultipleParentsReport() throws TermServerScriptException {
		for (Concept c : gl.getDescendantsCache().getDescendantsOrSelf(subHierarchy)) {
			Set<Concept> parents = new HashSet<>(c.getParents(CharacteristicType.STATED_RELATIONSHIP));
			parents.addAll(c.getParents(CharacteristicType.INFERRED_RELATIONSHIP));
			if (parents.size() > 1) {
				String parentsStr = parents.stream().map(p -> p.toString()).collect(Collectors.joining(", \n"));
				report(c, parentsStr);
				incrementSummaryInformation("Concept with multiple parents identified");
			}
		}
	}
}

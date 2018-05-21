package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.IOException;
import java.io.PrintStream;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * QI-25
 * Find concepts in the subhierarchy which have an inferred parent that is _outside_ of the 
 * ie where the subhierarchy is not a neat triangle, but is seeping into other subhierarchies.
 */
public class CrossHierarchyLeakage extends TermServerReport {
	
	Concept subHierarchy;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		CrossHierarchyLeakage report = new CrossHierarchyLeakage();
		try {
			report.additionalReportColumns = "FSN in Target Hierarchy, Parent in other hierarchy, Parent in Target Hierarchy";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postLoadInit();
			report.runCrossHierarchyLeakageReport();
		} catch (Exception e) {
			info("Failed to produce MissingAttributeReport due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept("307824009"); // |Administrative statuses (finding)|
	}

	private void runCrossHierarchyLeakageReport() throws TermServerScriptException {
		for (Concept c : descendantsCache.getDescendentsOrSelf(subHierarchy)) {
			Concept parentInHierarchy = null;
			//Check all my inferred parents for concepts in target hierarchy
			for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
				if (descendantsCache.getDescendentsOrSelf(subHierarchy).contains(parent)) {
					parentInHierarchy = parent;
				}
			}
			
			//Check all my inferred parents for concepts in other hierarchies
			for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
				boolean leakingParentFound = false;
				if (!descendantsCache.getDescendentsOrSelf(subHierarchy).contains(parent)) {
					report (c, parent, parentInHierarchy);
					leakingParentFound = true;
				}
				if (leakingParentFound) {
					incrementSummaryInformation("Leaking concept identified");
				}
			}
		}
	}
	
}

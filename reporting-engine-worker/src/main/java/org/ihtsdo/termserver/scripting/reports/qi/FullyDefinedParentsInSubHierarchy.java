package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * QI-31
 * Once remodelling is complete, a "Post Changes" check that can be run is to 
 * check for any concepts that still have immediate fully defined parents.
 */
public class FullyDefinedParentsInSubHierarchy extends TermServerReport {
	
	Concept subHierarchy;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException {
		FullyDefinedParentsInSubHierarchy report = new FullyDefinedParentsInSubHierarchy();
		try {
			report.additionalReportColumns = "FSN, Parents, Calculated PPPs";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postLoadInit();
			report.runFullyDefinedInSubHierarchyReport();
		} catch (Exception e) {
			info("Failed to produce MissingAttributeReport due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept("126537000"); // |Neoplasm of bone (disorder)|
	}

	private void runFullyDefinedInSubHierarchyReport() throws TermServerScriptException {
		nextConcept:
		for (Concept c : gl.getDescendantsCache().getDescendents(subHierarchy)) {
			for (Concept parent : c.getParents(CharacteristicType.STATED_RELATIONSHIP)) {
				if (parent.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
					String parentStr = c.getParents(CharacteristicType.STATED_RELATIONSHIP)
							.stream()
							.map(p -> p.toString())
							.collect(Collectors.joining(", \n"));
					List<Concept> PPPs = determineProximalPrimitiveParents(c);
					String PPPStr = PPPs.stream()
							.map(p -> p.toString())
							.collect(Collectors.joining(", \n"));
					report (c, parentStr, PPPStr);
					incrementSummaryInformation("Concepts reported");
					continue nextConcept;
				}
			}
		}
	}
	
}

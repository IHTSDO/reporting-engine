package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.IOException;
import java.io.PrintStream;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * QI-25
 * Find Sufficiently Defined concepts in a subhierarchy and list all ancestors
 */
public class FullyDefinedInSubHierarchy extends TermServerReport {
	
	Concept subHierarchy;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		FullyDefinedInSubHierarchy report = new FullyDefinedInSubHierarchy();
		try {
			report.additionalReportColumns = "FSN, Parents...";
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
		subHierarchy = gl.getConcept("307824009"); // |Administrative statuses (finding)|
	}

	private void runFullyDefinedInSubHierarchyReport() throws TermServerScriptException {
		for (Concept c : gl.getDescendantsCache().getDescendents(subHierarchy)) {
			if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				incrementSummaryInformation("FDs reported");
				report (c, c.getParents(CharacteristicType.INFERRED_RELATIONSHIP).stream().map(p -> p.toString()).collect(Collectors.joining(", \n")));
			}
		}
	}
	
}

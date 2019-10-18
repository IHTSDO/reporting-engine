package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScriptException;

import org.ihtsdo.termserver.scripting.domain.*;
/**
 * FD19947 Get all descendants for two concepts (de-duplicate
 */
public class SubHierarchyConceptsReport extends TermServerReport{
	
	Concept[] subHierarchies;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		SubHierarchyConceptsReport report = new SubHierarchyConceptsReport();
		try {
			report.additionalReportColumns="Descriptions";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postLoadInit();
			report.reportConcepts();
		} catch (Exception e) {
			info("Failed to produce Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}
	
	private void postLoadInit() throws TermServerScriptException {
		subHierarchies = new Concept[] { gl.getConcept("73211009"), // | Diabetes mellitus (disorder) |
										gl.getConcept("74627003") }; //| Diabetic complication (disorder) |
	}

	private void reportConcepts() throws TermServerScriptException {
		Set<Concept> concepts = new HashSet<>();
		for (Concept subHierarchy : subHierarchies) {
			concepts.addAll(subHierarchy.getDescendents(NOT_SET));
		}
		
		for (Concept c : concepts) {
			Object[] terms = c.getDescriptions(ActiveState.ACTIVE)
					.stream()
					.map(desc->desc.toString())
					.toArray(String[]::new);
			report (c, terms);
			incrementSummaryInformation("Concepts reported");
		}
	}

}

package org.ihtsdo.termserver.scripting.reports;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
/**
 * FD19947 Get all descendants for two concepts (de-duplicate
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubHierarchyConceptsReport extends TermServerReport{

	private static final Logger LOGGER = LoggerFactory.getLogger(SubHierarchyConceptsReport.class);

	Concept[] subHierarchies;
	
	public static void main(String[] args) throws TermServerScriptException {
		SubHierarchyConceptsReport report = new SubHierarchyConceptsReport();
		try {
			report.additionalReportColumns="Descriptions";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postLoadInit();
			report.reportConcepts();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report", e);
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
			concepts.addAll(subHierarchy.getDescendants(NOT_SET));
		}
		
		for (Concept c : concepts) {
			Object[] terms = c.getDescriptions(ActiveState.ACTIVE)
					.stream()
					.map(desc->desc.toString())
					.toArray(String[]::new);
			report(c, terms);
			incrementSummaryInformation("Concepts reported");
		}
	}

}

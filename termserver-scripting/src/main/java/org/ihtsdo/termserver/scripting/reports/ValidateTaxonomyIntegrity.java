package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;

/**
 * Reports all terms that contain the specified text
 */
public class ValidateTaxonomyIntegrity extends TermServerScript{
	
	List<String> criticalErrors = new ArrayList<String>();
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	GraphLoader gl = GraphLoader.getGraphLoader();
	String matchText = "+"; 
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		ValidateTaxonomyIntegrity report = new ValidateTaxonomyIntegrity();
		try {
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.validateTaxonomyIntegrity();
		} catch (Exception e) {
			println("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
			for (String err : report.criticalErrors) {
				println (err);
			}
		}
	}

	private void validateTaxonomyIntegrity() throws TermServerScriptException {
		Collection<Concept> concepts = gl.getAllConcepts();
		println ("Validating all relationships");
		for (Concept c : concepts) {
			validateRelationships(c, CharacteristicType.INFERRED_RELATIONSHIP);
			validateRelationships(c, CharacteristicType.STATED_RELATIONSHIP);
		}
		addSummaryInformation("Concepts checked", concepts.size());
	}

	private void validateRelationships(Concept c, CharacteristicType charType) {
		for (Relationship r : c.getRelationships(charType, ActiveState.ACTIVE)) {
			//Check for an FSN to ensure Concept fully exists
			if (r.getSource().getFsn() == null || r.getSource().getFsn().isEmpty()) {
				String msg = "Non-existent source in " + charType + " relationship: " + r;
				report (c, msg);
			} else if (!r.getSource().isActive()) {
				String msg = "Inactive source in " + charType + " relationship: " + r;
				report (c, msg);
			}
			
			if (r.getTarget().getFsn() == null || r.getTarget().getFsn().isEmpty()) {
				String msg = "Non-existent target in " + charType + " relationship: " + r;
				report (c, msg);
			} else if (!r.getTarget().isActive()) {
				String msg = "Inactive target in " + charType + " relationship: " + r;
				report (c, msg);
			}
		}
	}

	protected void report (Concept c, String issue) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA_QUOTE + 
						issue + QUOTE;
		writeToFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException {
		super.init(args);
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String reportFilename = getScriptName() + "_" + project.toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToFile ("Concept, FSN, Issue");
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}
}

package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;

/**
 * Reports instances of more than one attributes of a given type
 */
public class AttributeCardinalityReport extends TermServerScript{
	
	List<String> criticalErrors = new ArrayList<String>();
	String subHierarchyStr = "373873005"; // |Pharmaceutical / biologic product (product)|
	String targetAttributeStr = "411116001"; // |Has manufactured dose form (attribute)|
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	GraphLoader gl = GraphLoader.getGraphLoader();
	String matchText = "+"; 
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		AttributeCardinalityReport report = new AttributeCardinalityReport();
		try {
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.runAttributeCardinalityReport();
		} catch (Exception e) {
			println("Failed to produce AttributeCardinalityReport Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
			for (String err : report.criticalErrors) {
				println (err);
			}
		}
	}

	private void runAttributeCardinalityReport() throws TermServerScriptException {
		Collection<Concept> subHierarchy = gl.getConcept(subHierarchyStr).getDescendents(NOT_SET);
		println ("Validating all relationships");
		long issuesEncountered = 0;
		for (Concept c : subHierarchy) {
			if (c.isActive()) {
				issuesEncountered += checkAttributeCardinality(c);
			}
		}
		addSummaryInformation("Concepts checked", subHierarchy.size());
		addSummaryInformation("Issues encountered", issuesEncountered);
	}

	private long checkAttributeCardinality(Concept c) throws TermServerScriptException {
		long issues = 0;
		Concept typeOfInterest = gl.getConcept(targetAttributeStr);
		Set<Concept> typesEncountered = new HashSet<Concept>();
		
		List<Relationship> statedRelationshipsOfInterest = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, typeOfInterest, ActiveState.ACTIVE);
		List<Relationship> inferredRelationshipsOfInterest = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, typeOfInterest, ActiveState.ACTIVE);
		if (statedRelationshipsOfInterest.size() != inferredRelationshipsOfInterest.size()) {
			String msg = "Cardinality mismatch between stated and inferred relationships - (S: " + statedRelationshipsOfInterest.size() + " I: " + inferredRelationshipsOfInterest.size() + ")";
			report(c, msg);
			issues++;
		}
		
		for (Relationship r : statedRelationshipsOfInterest) {
			//Have we seen this value for the target attribute type before?
			Concept type = r.getType();
			if (typesEncountered.contains(type)) {
				String msg = "Multiple Stated instances of attribute " + type;
				report(c, msg);
				issues++;
			}
			
			//Check that we have an inferred relationship that matches this value
			boolean matchFound = false;
			for (Relationship rInf : inferredRelationshipsOfInterest) {
				if (rInf.getTarget().equals(r.getTarget())) {
					matchFound = true;
				}
			}
			if (!matchFound) {
				String msg = "Stated relationship has no inferred counterpart: " + r;
				report(c, msg);
				issues++;
			}
			typesEncountered.add(type);
		}
		return issues;
	}

	protected void report (Concept c, String issue) {
		String line =	c.getConceptId() + COMMA_QUOTE + 
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

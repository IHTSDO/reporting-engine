package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExportType;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExtractType;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class SubHierarchyConceptsReport extends TermServerScript{
	
	List<String> criticalErrors = new ArrayList<String>();
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	GraphLoader gl = GraphLoader.getGraphLoader();
	ActiveState filterOnActiveState = null;
	Concept subHierarchy;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		SubHierarchyConceptsReport report = new SubHierarchyConceptsReport();
		try {
			report.init(args);
			report.loadProjectSnapshot(true);  //Load FSNs only
			report.reportConcepts();
		} catch (Exception e) {
			println("Failed to produce Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
			for (String err : report.criticalErrors) {
				println (err);
			}
		}
	}
	
	private void reportConcepts() throws TermServerScriptException {
		Collection<Concept> conceptsToExamine = subHierarchy.getDescendents(NOT_SET);
		println("Examining " + conceptsToExamine.size() + " concepts");
		int reportedConcepts = 0;
		for (Concept thisConcept : conceptsToExamine) {
			if (SnomedUtils.conceptHasActiveState(thisConcept, filterOnActiveState)) {
				if (thisConcept.getFsn() == null) {
					String msg = "Concept " + thisConcept.getConceptId() + " has no FSN";
					criticalErrors.add(msg);
					println(msg);
				}
				report (thisConcept);
				reportedConcepts++;
			}
		}
		println("Reported " + reportedConcepts + " concepts in active state: " + filterOnActiveState);
		println("Graph loader log: \n" + gl.log);
	}
	
	protected void report (Concept c) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA + 
						c.isActive() + COMMA + 
						c.getEffectiveTime().equals(transientEffectiveDate);

		writeToReportFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException, SnowOwlClientException {
		super.init(args);
		print ("What date identifies 'new' relationships? [" + transientEffectiveDate + "]: ");
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			long dateAsNumber = Long.parseLong(response);
			if (dateAsNumber < 2000000L || dateAsNumber > 30000000) {
				throw new TermServerScriptException("Invalid date: "  + response);
			}
			transientEffectiveDate = response;
		}
		
		print ("Filter for a particular sub-hierarchy? (eg 373873005 or return for none): ");
		response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			subHierarchy = gl.getConcept(response);
		} else {
			subHierarchy = gl.getConcept(SCTID_ROOT_CONCEPT.toString());
		}
		
		while (filterOnActiveState == null) {
			print ("Report which active state(s)? [A,I,B]: ");
			response = STDIN.nextLine().trim();
			if (!response.isEmpty()) {
				switch (response.toUpperCase()) {
					case "A" : filterOnActiveState = ActiveState.ACTIVE;
															break;
					case "I" : filterOnActiveState = ActiveState.INACTIVE;
															break;
					case "B" : filterOnActiveState = ActiveState.BOTH;
				default:
				}
			} 
		}
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String reportFilename = "concepts_" + project.getKey().toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToReportFile ("Concept, FSN, Concept_Active, Concept_Modified");
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}
}

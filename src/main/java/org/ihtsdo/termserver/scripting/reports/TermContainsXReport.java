package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * Reports all terms that contain the specified text
 */
public class TermContainsXReport extends TermServerScript{
	
	List<String> criticalErrors = new ArrayList<String>();
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	//String matchText = "+"; 
	String matchText = "/1 each";
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		TermContainsXReport report = new TermContainsXReport();
		try {
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.reportDescriptionContainsX();
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

	private void reportDescriptionContainsX() throws TermServerScriptException {
		Collection<Concept> concepts = gl.getAllConcepts();
		Multiset<String> tags = HashMultiset.create();
		
		for (Concept c : concepts) {
			if (c.isActive()) {
				for (Description d : c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE)) {
				//for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (d.getTerm().contains(matchText)) {
						String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
						report(c, d, semTag);
						tags.add(semTag);
					}
				}
				
			}
		}
		addSummaryInformation("Concepts checked", concepts.size());
		addSummaryInformation("Matching Descriptions", tags.size());
		for (String tag : tags.elementSet()) {
			addSummaryInformation( "Tag: " + tag , tags.count(tag));
		}
	}

	protected void report (Concept c, Description d, String semTag) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA_QUOTE + 
						semTag + QUOTE_COMMA_QUOTE +
						d.getDescriptionId() + QUOTE_COMMA_QUOTE +
						d.getTerm() + QUOTE;
		writeToFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException, SnowOwlClientException {
		super.init(args);
		
		/*print ("Filter for a particular sub-hierarchy? (eg 373873005 or return for none): ");
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			subHierarchy = gl.getConcept(response);
		}*/
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		//String reportFilename = "changed_relationships_" + project.getKey().toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		//String filter = (subHierarchy == null) ? "" : "_" + subHierarchy.getConceptId();
		String reportFilename = getScriptName() + /*filter +*/ "_" + project.getKey().toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToFile ("Concept, FSN, Sem_Tag, Desc_SCTID, Term");
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}
}

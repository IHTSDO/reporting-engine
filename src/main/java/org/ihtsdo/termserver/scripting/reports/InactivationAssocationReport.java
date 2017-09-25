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
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.HistoricalAssociation;
import org.ihtsdo.termserver.scripting.domain.InactivationIndicatorEntry;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * Reports all concepts inactivated with the target inactivation reason, 
 * which also has an active association
 * Run a query to find inactive concepts using WAS A as the Association type for LIMITED, OUTDATED or ERRONEOUS inactivation reasons.
 */
public class InactivationAssocationReport extends TermServerScript{
	
	String[] targetInactivationReasons = new String[] {SCTID_INACT_LIMITED, SCTID_INACT_OUTDATED, SCTID_INACT_ERRONEOUS};
	String[] targetAssocationRefsetIds = new String[] {SCTID_HIST_WAS_A_REFSETID};
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		InactivationAssocationReport report = new InactivationAssocationReport();
		try {
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.reportMatchingInactivations();
		} catch (Exception e) {
			println("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void reportMatchingInactivations() throws TermServerScriptException {
		int rowsReported = 0;
		println ("Scanning all concepts...");
		for (Concept c : gl.getAllConcepts()) {
			//For a change we're interested in inactive concepts!
			if (!c.isActive()) {
				//Does this inactivated concept have one of our target inactivation reasons?
				for (String inactivationReasonSctId : targetInactivationReasons) {
					for (InactivationIndicatorEntry inactivationIndicator : c.getInactivationIndicatorEntries(ActiveState.ACTIVE)) {
						if (inactivationIndicator.getInactivationReasonId().equals(inactivationReasonSctId)) {
							//Now does the concept have one of our target historical associations?
							for (HistoricalAssociation histAssoc : c.getHistorialAssociations(ActiveState.ACTIVE)) {
								for (String targetAssocationRefsetId : targetAssocationRefsetIds) {
									if (histAssoc.getRefsetId().equals(targetAssocationRefsetId)) {
										report(c,inactivationIndicator, histAssoc);
										rowsReported++;
									}
								}
							}
						}
					}
				}
			}
		}
		addSummaryInformation("Concepts checked", gl.getAllConcepts().size());
		addSummaryInformation("Rows reported", rowsReported);
	}

	protected void report (Concept c, InactivationIndicatorEntry inact, HistoricalAssociation assoc) throws TermServerScriptException {
		
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA_QUOTE + 
						inact.getEffectiveTime() + QUOTE_COMMA_QUOTE + 
						simpleName(inact.getInactivationReasonId()) + QUOTE_COMMA_QUOTE +
						assoc.getEffectiveTime() + QUOTE_COMMA_QUOTE + 
						simpleName(assoc.getRefsetId()) + " -> " + gl.getConcept(assoc.getTargetComponentId())+ QUOTE;
		writeToFile(line);
	}
	
	private String simpleName(String sctid) throws TermServerScriptException {
		Concept c = gl.getConcept(sctid);
		return SnomedUtils.deconstructFSN(c.getFsn())[0];
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException, SnowOwlClientException {
		super.init(args);
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String reportFilename = getScriptName() + "_" + project.getKey().toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToFile ("Concept, FSN, inact_effective, inactivation_reason, assocation_effective, association");
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}
}

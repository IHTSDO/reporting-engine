package org.ihtsdo.termserver.job.schedule;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.List;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.reports.qi.InitialAnalysis;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * QI-76 Add support for running a report multiple times for different subhierarchies
 * Generating Jira tickets to receive each one.
 * */
public class MutliReportRunner extends TermServerReport {
	
	ReportClass report; 
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		MutliReportRunner report = new MutliReportRunner();
		try {
			report.init(args);
			report.loadProjectSnapshot(false); 
			report.runMultipleReports();
		} catch (Exception e) {
			info("Failed to produce MutliReportRunner due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}
	
	public String getReportName() {
		//We'll delegate that to the report we're actually running
		return report.getReportName();
	}
	
	public void init (String[] args) throws TermServerScriptException, SnowOwlClientException {
		super.init(args, true);  //Delay initialisation of reports
		report = new InitialAnalysis(this);
	}

	private void runMultipleReports() throws TermServerScriptException, IOException {
		info("Loading " + inputFile);
		if (!inputFile.canRead()) {
			throw new TermServerScriptException("Cannot read: " + inputFile);
		}
		List<String> lines = Files.readLines(inputFile, Charsets.UTF_8);
		for (String line : lines) {
			
		}
	}
	
}

package org.ihtsdo.termserver.job.schedule;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import org.apache.commons.lang.ArrayUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.JiraHelper;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.reports.qi.InitialAnalysis;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * QI-76 Add support for running a report multiple times for different subhierarchies
 * Generating Jira tickets to receive each one.
 * */
public class MutliReportRunner extends TermServerReport {
	
	ReportClass report; 
	JiraHelper jira;
	
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
		report = new InitialAnalysis(this);
		super.init(args, true);  //Delay initialisation of reports
		jira = new JiraHelper();
	}

	private void runMultipleReports() throws TermServerScriptException, IOException {
		report.setReportManager(getReportManager());
		info("Loading " + inputFile);
		if (!inputFile.canRead()) {
			throw new TermServerScriptException("Cannot read: " + inputFile);
		}
		List<String> lines = Files.readLines(inputFile, Charsets.UTF_8);
		for (String line : lines) {
			//In this file, the first item is the subhierarchy and any
			//others are the exceptions
			String[] items = line.split(COMMA);
			String subHiearchyStr = items[0];
			String[] exclusions = (String[]) ArrayUtils.removeElement(items, subHiearchyStr);
			Concept subHierarchy = gl.getConcept(line);
			report.postInit(line);
			report.setExclusions(exclusions);
			String url = report.runReport();
			String summary = subHierarchy + " - Main Ticket";
			String description = "Initial Analysis: " + url;
			jira.createJiraTicket("QI", summary, description);
		}
	}
	
}

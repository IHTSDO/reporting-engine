package org.ihtsdo.termserver.scripting;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import org.apache.commons.lang.ArrayUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.JiraHelper;

import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * QI-76 Add support for running a report multiple times for different subhierarchies
 * Generating Jira tickets to receive each one.
 * */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiReportRunner extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(MultiReportRunner.class);

	ReportClass report; 

	public static void main(String[] args) throws TermServerScriptException {
		MultiReportRunner report = new MultiReportRunner();
		try {
			report.init(args);
			report.loadProjectSnapshot(false); 
			report.runMultipleReports();
		} catch (Exception e) {
			LOGGER.info("Failed to produce MutliReportRunner due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	public String getReportName() {
		//We'll delegate that to the report we're actually running
		return report.getReportName();
	}
	
	private void runMultipleReports() throws TermServerScriptException, IOException {
		report.setReportManager(getReportManager());
		LOGGER.info("Loading " + getInputFile());
		if (!getInputFile().canRead()) {
			throw new TermServerScriptException("Cannot read: " + getInputFile());
		}
		List<String> lines = Files.readLines(getInputFile(), Charsets.UTF_8);
		for (String line : lines) {
			//In this file, the first item is the subhierarchy and any
			//others are the exceptions
			String[] items = line.split(COMMA);
			String subHiearchyStr = items[0];
			String[] exclusions = (String[]) ArrayUtils.removeElement(items, subHiearchyStr);
			Concept subHierarchy = gl.getConcept(line.split(TAB)[0]);
			//TODO
			//report.postInit(line);
			report.setExclusions(exclusions);
			//TODO
			//String url = report.runReport();
			String summary = subHierarchy + " - Main Ticket";
			String description = "Initial Analysis: " + url;
			//jira.createJiraTicket("QI", summary, description);
		}
	}
	
}

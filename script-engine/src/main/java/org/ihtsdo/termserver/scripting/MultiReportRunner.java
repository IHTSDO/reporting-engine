package org.ihtsdo.termserver.scripting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.apache.commons.lang.ArrayUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;

import org.ihtsdo.termserver.scripting.reports.TermServerReport;

import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * QI-76 Add support for running a report multiple times for different subhierarchies
 * Generating Jira tickets to receive each one.
 * */
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
			LOGGER.info("Failed to produce report ", e);
		} finally {
			report.finish();
		}
	}

	private void runMultipleReports() throws TermServerScriptException, IOException {
		report.setReportManager(getReportManager());
		LOGGER.info("Loading {}", getInputFile());
		if (!getInputFile().canRead()) {
			throw new TermServerScriptException("Cannot read: " + getInputFile());
		}
		List<String> lines = Files.readLines(getInputFile(), StandardCharsets.UTF_8);
		for (String line : lines) {
			//In this file, the first item is the subhierarchy and any
			//others are the exceptions
			String[] items = line.split(COMMA);
			String subHiearchyStr = items[0];
			List<Concept> exclusions = Arrays.stream(ArrayUtils.removeElement(items, subHiearchyStr))
					.map(s -> gl.getConceptSafely((String)s))
					.toList();
			Concept subHierarchy = gl.getConcept(line.split(TAB)[0]);
			report.setExclusions(exclusions);
			report.runJob();
			String url = report.getJobRun().getResultUrl();
			String summary = subHierarchy + " - Main Ticket";
			String description = "Initial Analysis: " + url;
			LOGGER.info("Option here to create a JIRA ticket to follow up on these reports {} : {}", summary, description);
		}
	}
	
}

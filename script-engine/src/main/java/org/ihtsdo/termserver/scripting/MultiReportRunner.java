package org.ihtsdo.termserver.scripting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.apache.commons.lang.ArrayUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;

import org.ihtsdo.termserver.scripting.reports.TermServerReport;

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

	public static void main(String[] args) throws TermServerScriptException, IOException {
		MultiReportRunner report = new MultiReportRunner();
		try {
			report.init(args);
			report.loadProjectSnapshot(false); 
			report.runMultipleReports();
		} catch (Exception e) {
			LOGGER.error("Failed to produce Multi Report Runner", e);
		} finally {
			report.finish();
		}
	}

	@Override
	public String getReportName() {
		//We'll delegate that to the report we're actually running
		return report.getReportName();
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
			excludeHierarchies = Arrays.stream(ArrayUtils.removeElement(items, subHiearchyStr))
					.map(o -> (String)o)
					.map(s -> gl.getConceptSafely(s))
					.toList();
			report.runJob();
		}
	}
	
}

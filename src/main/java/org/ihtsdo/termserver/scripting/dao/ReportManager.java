package org.ihtsdo.termserver.scripting.dao;

import java.io.*;
import java.util.*;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;

public class ReportManager implements RF2Constants {
	
	boolean writeToFile = true;
	ReportFileManager reportFileManager;
	
	boolean writeToSheet = true;
	ReportSheetManager reportSheetManager;
	
	protected int numberOfDistinctReports = 1;
	protected String reportName;
	protected String env;
	List<String> tabNames;
	
	public void init(String environment, String reportName) {
		this.env = environment;
		this.reportName = reportName;
		reportFileManager = new ReportFileManager(this);
		reportSheetManager = new ReportSheetManager(this);
	}
	
	public void writeToReportFile(int reportIdx, String line) throws IOException {
		if (writeToFile) {
			reportFileManager.writeToReportFile(reportIdx, line);
		}
		
		if (writeToSheet) {
			reportSheetManager.writeToReportFile(reportIdx, line, false);
		}
	}
	
	PrintWriter getPrintWriter(String fileName) throws TermServerScriptException {
		return reportFileManager.getPrintWriter(fileName);
	}

	public void flushFiles(boolean andClose) {
		reportFileManager.flushFiles(andClose);
	}

	public void writeToRF2File(String fileName, Object[] columns) throws TermServerScriptException {
		reportFileManager.writeToRF2File(fileName, columns);
	}
	
	public void initialiseReportFiles(String[] columnHeaders) throws IOException, TermServerScriptException {
		if (writeToFile) {
			reportFileManager.initialiseReportFiles(columnHeaders);
		}
		
		if (writeToSheet) {
			reportSheetManager.initialiseReportFiles(columnHeaders);
		}
	}
	
	public Map<String, PrintWriter> getPrintWriterMap() {
		return reportFileManager.printWriterMap;
	}

	public void setPrintWriterMap(Map<String, PrintWriter> printWriterMap) {
		reportFileManager.setPrintWriterMap(printWriterMap);
	}

	public void setNumberOfDistinctReports(int x) {
		numberOfDistinctReports = x;
	}

	public int getNumberOfDistinctReports() {
		return numberOfDistinctReports;
	}

	public String getReportName() {
		return reportName;
	}
	
	public List<String> getTabNames() {
		return tabNames;
	}
	
	public void setTabNames(String[] tabNames) {
		this.tabNames = Arrays.asList(tabNames);
	}
	
}

package org.ihtsdo.termserver.scripting.dao;

import java.io.*;
import java.util.*;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;

public class ReportManager implements RF2Constants {
	
	public static final String STANDARD_HEADERS = "Concept SCTID, Detail";
	boolean writeToFile = false;
	ReportFileManager reportFileManager;
	
	boolean writeToSheet = true;
	ReportSheetManager reportSheetManager;
	
	protected int numberOfDistinctReports = 1;
	protected String reportName;
	protected String env;
	List<String> tabNames;
	
	private ReportManager() {};
	
	public static ReportManager create(String environment, String reportName) {
		ReportManager rm = new ReportManager();
		rm.init(environment, reportName);
		return rm;
	}
	
	public void init(String environment, String reportName) {
		this.env = environment;
		this.reportName = reportName;
		reportFileManager = new ReportFileManager(this);
		reportSheetManager = new ReportSheetManager(this);
		tabNames = Arrays.asList(new String[] {"Sheet1"});
	}
	
	public void writeToReportFile(int reportIdx, String line) throws TermServerScriptException {
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

	public void flushFiles(boolean andClose) throws TermServerScriptException {
		//Watch that we might have written to RF2 files, even if writeToFile is set to false
		reportFileManager.flushFiles(andClose);
		
		if (writeToSheet) {
			reportSheetManager.flush();
			if (andClose) {
				System.out.println("See Google Sheet: " + reportSheetManager.getUrl());
			}
		}
	}
	
	public void flushFilesSoft() throws TermServerScriptException {
		if (writeToFile) {
			reportFileManager.flushFiles(false);
		}
		
		if (writeToSheet) {
			reportSheetManager.flushSoft();
		}
	}
	
	public void initialiseReportFiles(String[] columnHeaders) throws TermServerScriptException {
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
	
	public void setReportName(String reportName) {
		this.reportName = reportName;
	}
	
	public List<String> getTabNames() {
		return tabNames;
	}
	
	public void setTabNames(String[] tabNames) {
		this.tabNames = Arrays.asList(tabNames);
		this.numberOfDistinctReports = tabNames.length;
	}

	public void setFileOnly() {
		writeToFile = true;
		writeToSheet = false;
	}

	public String getEnv() {
		return env;
	}
	
	public String getUrl() {
		if (writeToSheet) {
			return reportSheetManager.getUrl();
		} else {
			return reportFileManager.getFileName();
		}
	}
	
	public void setWriteToFile(boolean flag) {
		writeToFile = flag;
	}
	
	public void setWriteToSheet(boolean flag) {
		writeToSheet = flag;
	}
	
	public boolean isWriteToSheet() {
		return writeToSheet;
	}
	
}

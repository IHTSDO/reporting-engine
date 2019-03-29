package org.ihtsdo.termserver.scripting.dao;

import java.io.*;
import java.util.*;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;

public class ReportManager implements RF2Constants {
	
	public static final String STANDARD_HEADERS = "Concept SCTID, Detail";
	boolean writeToFile = false;
	ReportFileManager reportFileManager;
	
	boolean writeToSheet = true;
	ReportSheetManager reportSheetManager;
	
	protected int numberOfDistinctReports = 1;
	protected TermServerScript ts;
	protected String env;
	List<String> tabNames;
	
	private ReportManager() {};
	
	public static ReportManager create(TermServerScript ts) {
		ReportManager rm = new ReportManager();
		rm.init(ts);
		return rm;
	}
	
	public void init(TermServerScript ts) {
		if (ts != null) {
			this.env = ts.getEnv();
			this.ts = ts;
		}
		reportFileManager = new ReportFileManager(this);
		if (this.ts.isOffline()) {
			TermServerScript.info("Running in offline mode. Outputting to file rather than google sheets");
			writeToSheet = false;
			writeToFile = true;
		} else {
			reportSheetManager = new ReportSheetManager(this);
			writeToSheet = true;
			writeToFile = false;
		}
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

	public void flushFiles(boolean andClose, boolean withWait) throws TermServerScriptException {
		//Watch that we might have written to RF2 files, even if writeToFile is set to false
		reportFileManager.flushFiles(andClose);
		
		if (writeToSheet) {
			reportSheetManager.flushWithWait();
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

	public TermServerScript getScript() {
		return ts;
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
		//If we're working against a published release, then the environment isn't relevant
		String projKey = getScript().getProject().getKey();
		String releaseBranch = getScript().getArchiveManager().detectReleaseBranch(projKey);
		return releaseBranch == null ? env : releaseBranch;
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

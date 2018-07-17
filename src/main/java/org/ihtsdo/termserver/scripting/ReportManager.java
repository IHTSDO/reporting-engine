package org.ihtsdo.termserver.scripting;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class ReportManager implements RF2Constants {
	enum Mode { FILE, GOOGLE, BOTH }
	Mode mode = Mode.BOTH;
	
	protected File[] reportFiles;
	protected Map<String, PrintWriter> printWriterMap = new HashMap<>();
	protected String currentTimeStamp;
	protected int numberOfDistinctReports = 1;
	protected String reportName;
	protected String env;
	
	public void init(String environment, String reportName) {
		this.env = environment;
		this.reportName = reportName;
	}
	
	protected void writeToReportFile(int reportIdx, String line) {
		try {
			PrintWriter pw = getPrintWriter(reportFiles[reportIdx].getAbsolutePath());
			pw.println(line);
		} catch (Exception e) {
			TermServerScript.info ("Unable to output report line: " + line + " due to " + e.getMessage());
			TermServerScript.info (org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(e));
		}
	}
	
	PrintWriter getPrintWriter(String fileName) throws TermServerScriptException {
		try {
			PrintWriter pw = printWriterMap.get(fileName);
			if (pw == null) {
				File file = SnomedUtils.ensureFileExists(fileName);
				OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8);
				BufferedWriter bw = new BufferedWriter(osw);
				pw = new PrintWriter(bw);
				printWriterMap.put(fileName, pw);
			}
			return pw;
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to initialise " + fileName + " due to " + e.getMessage(), e);
		}
	}

	public void flushFiles(boolean andClose) {
		for (PrintWriter pw : printWriterMap.values()) {
			try {
				pw.flush();
				if (andClose) {
					pw.close();
				}
			} catch (Exception e) {}
		}
		if (andClose) {
			printWriterMap = new HashMap<>();
		}
	}

	protected void writeToRF2File(String fileName, Object[] columns) throws TermServerScriptException {
		PrintWriter out = getPrintWriter(fileName);
		try {
			StringBuffer line = new StringBuffer();
			for (int x=0; x<columns.length; x++) {
				if (x > 0) {
					line.append(TSV_FIELD_DELIMITER);
				}
				line.append(columns[x]==null?"":columns[x]);
			}
			out.print(line.toString() + LINE_DELIMITER);
		} catch (Exception e) {
			TermServerScript.info ("Unable to output report rf2 line due to " + e.getMessage());
		}
	}
	
	public void initialiseReportFiles(String[] columnHeaders) throws IOException {
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		currentTimeStamp = df.format(new Date());
		reportFiles = new File[numberOfDistinctReports];
		for (int reportIdx = 0; reportIdx < numberOfDistinctReports; reportIdx++) {
			String idxStr = reportIdx == 0 ? "" : "_" + reportIdx;
			String reportFilename = "results_" + reportName + "_" + currentTimeStamp + "_" + env  + idxStr + ".csv";
			reportFiles[reportIdx] = new File(reportFilename);
			TermServerScript.info ("Outputting Report to " + reportFiles[reportIdx].getAbsolutePath());
			writeToReportFile (reportIdx, columnHeaders[reportIdx]);
		}
		flushFiles(false);
	}
	
	public Map<String, PrintWriter> getPrintWriterMap() {
		return printWriterMap;
	}

	public void setPrintWriterMap(Map<String, PrintWriter> printWriterMap) {
		this.printWriterMap = printWriterMap;
	}

	public void setNumberOfDistinctReports(int x) {
		numberOfDistinctReports = x;
	}
	
}

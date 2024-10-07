package org.ihtsdo.termserver.scripting.cis;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.springframework.util.StringUtils;

import com.google.common.collect.Iterables;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CisReconciliation extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(CisReconciliation.class);

	public static String AVAILABLE = "Available";
	public static String RESERVED = "Reserved";
	public static String DEPRECATED = "Deprecated";
	public static String ASSIGNED = "Assigned";
	public static String PUBLISHED = "Published";
	
	private Map<Long, CisRecord> cisRecords = new HashMap<>();
	Set<Long> missingIds = new HashSet<>();
	Map<String, Set<Long>> wrongStatusMap = new HashMap<>();
	int correctlyRecorded = 0;
	
	private static String today = new SimpleDateFormat("yyyy-MM-dd HH:MM:ss").format(new Date());

	public static void main(String[] args) throws TermServerScriptException {
		CisReconciliation report = new CisReconciliation();
		try {
			report.additionalReportColumns = "CharacteristicType, Attribute, WhatWasInferred?";
			report.summaryTabIdx = PRIMARY_REPORT;
			report.init(args);
			report.postInit();
			report.importDbState();
			report.reconcileDbAgainstSnapshot();
			report.takeCorrectiveAction();
		} catch (Exception e) {
			LOGGER.info("Failed to produce ConceptsWithOrTargetsOfAttribute Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("13XiH3KVll3v0vipVxKwWjjf-wmjzgdDe");  // Technical Specialist
		String[] columnHeadings = new String[] {	"Summary Item, Detail, ",
													"SCTID, Effective Time, FileName",
													"Effective Time, SCTID, Current Status, Software, JobId, Created, Modified, ,",
													"SQL Statement, "};
		String[] tabNames = new String[] {	"Summary",
											"Missing Ids",
											"Incorrect Status",
											"Corrective Action"};
		super.postInit(tabNames, columnHeadings);
}

	private void importDbState() throws IOException {
		LOGGER.info("Importing DB State");
		ZipInputStream zis = new ZipInputStream(new FileInputStream(getInputFile()));
		ZipEntry ze = zis.getNextEntry();
		int row = 0;
		try {
			while (ze != null) {
				if (!ze.isDirectory()) {
					final String fileName = ze.getName();
					if (fileName.endsWith(".txt")) {
						try (BufferedReader reader = new BufferedReader(new InputStreamReader(zis))) {
							while (reader.ready()) {
								String line = reader.readLine();
								CisRecord cr = CisRecord.create(line.split("\t"));
								cisRecords.put(cr.getSctid(), cr);
								if (++row % 1000000 == 0) {
									LOGGER.info("Imported " + row + " cis records");
								}
							}
							LOGGER.info("Imported " + row + " cis records - complete.");
							ze = zis.getNextEntry();
						}
					}
				}
			}
		} finally {
			try{
				zis.closeEntry();
				zis.close();
			} catch (Exception e){} //Well, we tried.
		}
	}

	private void reconcileDbAgainstSnapshot() throws IOException, TermServerScriptException {
		LOGGER.info("Commencing reconcilliation");
		ZipInputStream zis = new ZipInputStream(new FileInputStream(getInputFile(1)));
		ZipEntry ze = zis.getNextEntry();
		try {
			while (ze != null) {
				if (!ze.isDirectory()) {
					final String fileName = StringUtils.getFilename(ze.getName());
					if (fileName.contains("sct2") && fileName.contains("Snapshot")) {
						LOGGER.info("Processing " + fileName);
						BufferedReader br = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
						String line;
						int row = 0;
						while ((line = br.readLine()) != null) {
								if (row > 0) {
									String[] fields = line.split("\t");
									String sctId = fields[0];
									if (sctId.contains("-")) {
										//Axioms have a UUID - skip this file
										LOGGER.info("Skipping " + fileName);
										break;
									}
									String effectiveTime = fields[1];
									reconcilePublishedId(Long.parseLong(sctId), effectiveTime);
								}
								
								if (++row % 1000000 == 0) {
									LOGGER.info("Processed " + row + " records in " + fileName);
								}
						}
						LOGGER.info("Processed " + row + " records in " + fileName);
					}
				}
				ze = zis.getNextEntry();
			}
		} finally {
			try{
				zis.closeEntry();
				zis.close();
			} catch (Exception e){} //Well, we tried.
		}
	}

	private void reconcilePublishedId(Long sctId, String et) throws TermServerScriptException {
		//Firstly, is this ID known to the database?
		if (!cisRecords.containsKey(sctId)) {
			missingIds.add(sctId);
			report(SECONDARY_REPORT, sctId, et);
			incrementSummaryInformation("SCTID Missing from CIS db");
		} else {
			CisRecord cr = cisRecords.get(sctId);
			if (cr.getStatus().equals(PUBLISHED)) {
				correctlyRecorded++;
			} else {
				report(TERTIARY_REPORT, et, cr.toArray());
				recordWrongStatus(cr, et);
			}
		}
	}

	private void recordWrongStatus(CisRecord cr, String et) {
		Set<Long> wrongIdsForStatus = wrongStatusMap.get(cr.getStatus());
		if (wrongIdsForStatus == null) {
			wrongIdsForStatus = new HashSet<>();
			wrongStatusMap.put(cr.getStatus(), wrongIdsForStatus);
		}
		wrongIdsForStatus.add(cr.getSctid());
		incrementSummaryInformation("Status: " + cr.getStatus());
		incrementSummaryInformation("EffectiveTime: " + et);
		incrementSummaryInformation("NameSpace: " + SnomedUtilsBase.getNamespace(Long.toString(cr.getSctid())));
		countIssue(null);
	}
	
	
	private void takeCorrectiveAction() throws TermServerScriptException {
		LOGGER.info("Taking corrective action...");
		if (missingIds.size() > 0) {
			throw new TermServerScriptException("Wasn't expecting to see missing Ids");
		}
		
		/* No need to add to audit.  This is taken care of by a trigger on the sctId table
		LOGGER.info("Adding audit LOGGER.information");
		for (String currentStatus : wrongStatusMap.keySet()) {
			report(QUATERNARY_REPORT, "");
			report(QUATERNARY_REPORT, " -- Audit for " + currentStatus);
			for (List<Long> sctIds : Iterables.partition(wrongStatusMap.get(currentStatus), 100)) {
				String idsStr = sctIds.stream().map(l -> Long.toString(l)).collect(Collectors.joining(", "));
				String auditSQL = "insert into sctId_log select * from sctId " +
						"WHERE sctId in (" + idsStr + ")";
				report(QUATERNARY_REPORT, auditSQL);
			}
		}*/
		
		LOGGER.info("Updating incorrect statuses...");
		for (String currentStatus : wrongStatusMap.keySet()) {
			report(QUATERNARY_REPORT, "");
			report(QUATERNARY_REPORT, " -- Updates for " + currentStatus);
			for (List<Long> sctIds : Iterables.partition(wrongStatusMap.get(currentStatus), 100)) {
				String idsStr = sctIds.stream().map(l -> Long.toString(l)).collect(Collectors.joining(", "));
				String updateSQL = "Update sctId set status = 'Published', " +
						"software = 'PWI Script', comment = 'INFRA-11130 Reconciliation', " +
						"modified_at = '" + today + "' " +
						"WHERE sctId in (" + idsStr + ");";
				report(QUATERNARY_REPORT, updateSQL);
			}
		}
	}
}

package org.ihtsdo.termserver.scripting.cis;

import com.google.common.collect.Lists;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtils;
import org.ihtsdo.termserver.scripting.client.CisClient;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * In this case, the cookie passed in will not be the usual ims cookie, but the cis token
 */
public class PublishSctids extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(PublishSctids.class);

	public static String AVAILABLE = "Available";
	public static String RESERVED = "Reserved";
	public static String DEPRECATED = "Deprecated";
	public static String ASSIGNED = "Assigned";
	public static String PUBLISHED = "Published";

	Map<String, Set<String>> newSctidsByNamespace = new HashMap<>();
	Map<String, Set<String>> oldSctidsByNamespace = new HashMap<>();

	Set<Long> missingIds = new HashSet<>();
	Map<String, Set<Long>> wrongStatusMap = new HashMap<>();
	int correctlyRecorded = 0;

	Set<String> namespaces = new HashSet<>();
	String targetET = "20231130";
	int batchSize = 100;

	private CisClient cisClient;
	
	private static String today = new SimpleDateFormat("yyyy-MM-dd HH:MM:ss").format(new Date());

	public static void main(String[] args) throws TermServerScriptException, IOException {
		PublishSctids report = new PublishSctids();
		try {
			report.summaryTabIdx = PRIMARY_REPORT;
			report.localClientsRequired = false;
			report.summaryTabIdx = PRIMARY_REPORT;
			report.init(args);
			report.postInit();
			report.groupSCTIDsByNamespace("Snapshot", true);
			report.groupSCTIDsByNamespace("Full", false);
			report.filterOutOldSCTIDs();
			report.publishSCTIDS();
		} catch (Exception e) {
			LOGGER.info("Failed to publish sctids due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void filterOutOldSCTIDs() {
		for (String namespace : newSctidsByNamespace.keySet()) {
			 newSctidsByNamespace.get(namespace).removeAll(oldSctidsByNamespace.get(namespace));
		}
	}

	public void init(String[] args) throws TermServerScriptException {
		super.init(args);
		String url = "https://cis.ihtsdotools.org/";
		cisClient = new CisClient(url, authenticatedCookie);
	}


	private void publishSCTIDS() throws TermServerScriptException {
		for (String namespace : newSctidsByNamespace.keySet()) {
			List<String> sctids = new ArrayList<>(newSctidsByNamespace.get(namespace));
			if (namespace.equals("0") || sctids.size() == 0) {
				LOGGER.info("Skipping " + sctids.size() + " sctids for namespace " + namespace);
				continue;
			}
			LOGGER.info("Processing " + sctids.size() + " sctids for namespace " + namespace);
			List<List<String>> batches = Lists.partition(sctids, batchSize);
			for (List<String> batch : batches) {
				//Filter out those records that are currently 'Reserved'
				batch = removeAndReportReserved(batch);
				//Have we lost all of them?
				if (batch.isEmpty()) {
					LOGGER.info("Skipping empty batch");
					continue;
				}
				CisBulkRequest cisBulkRequest = new CisBulkRequest("RP-836 Bulk publish of " + batch.size() + " sctids",
						Long.parseLong(namespace),
						batch,
						"Script-Engine");
				CisResponse response = cisClient.publishSctids(cisBulkRequest);
				List<CisRecord> records = cisClient.getBulkJobBlocking(response.getId());
				report(SECONDARY_REPORT, cisBulkRequest, response);
				for (CisRecord record : records) {
					incrementSummaryInformation("SCTIDs published namespace " + namespace);
					report(TERTIARY_REPORT, record.getSctid(), response.getId(), record.getStatus());
				}
				LOGGER.info("Published {} SCTIDS for namespace {}", records.size(), namespace);
				flushFilesWithWait(false);
			}
		}
	}

	private List<String> removeAndReportReserved(List<String> batch) throws TermServerScriptException {
		//We need to recover the current state so we can 'Assign' SCTIDs that are currently only at Status 'Reserved'
		List<CisRecord> currentStatus = cisClient.getSCTIDs(batch);
		for (CisRecord record : currentStatus) {
			if (record.getStatus().equals(RESERVED)) {
				incrementSummaryInformation("SCTIDs stuck at reserved");
				report(TERTIARY_REPORT, record.getSctid(), "SCTID is currently reserved. Cannot assign without calculating systemId", record);
				batch.remove(record.getSctid().toString());
			} else if (record.getStatus().equals(PUBLISHED)) {
				incrementSummaryInformation("SCTIDs already published");
				report(TERTIARY_REPORT, record.getSctid(), "SCTID is already published.", record);
				batch.remove(record.getSctid().toString());
			} else if (!record.getStatus().equals(ASSIGNED)) {
				incrementSummaryInformation("SCTIDs state " + record.getStatus());
				report(TERTIARY_REPORT, record.getSctid(), "SCTID status: " + record.getStatus() + " cannot be published.", record);
				batch.remove(record.getSctid().toString());
			}
		}
		return batch;
	}

	private void assignSCTIDS(List<CisRecord> records) {
		//We just need the IDs of those records that are reserved

	}

	private void groupSCTIDsByNamespace(String fileType, boolean findNewSCTIDs) {
		Map<String, Set<String>> sctidsByNamespace = findNewSCTIDs ? newSctidsByNamespace : oldSctidsByNamespace;
		try {
			InputStream is = new FileInputStream("releases/" + projectName);
			ZipInputStream zis = new ZipInputStream(is);
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if (entry.getName().endsWith(".txt")
						&& entry.getName().contains(fileType)
						&& !entry.getName().contains("Readme")) {
					LOGGER.info("Processing " + entry.getName());
					BufferedReader br = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
					String line;
					while ((line = br.readLine()) != null) {
						String[] parts = line.split("\t");
						String sctid = parts[0];
						String effectiveTime = parts[1];

						//If we're looking for new SCTIDs we're interested in them matching the ET
						//If we're looking for old SCTIDs, then we're interested in them NOT matching the ET
						if ( findNewSCTIDs != effectiveTime.equals(this.targetET)) {
							continue;
						}

						if (sctid.equals("id") || sctid.contains("-")) {
							continue;
						}
						String namespace = null;
						for (String knownNamespace : namespaces) {
							if (sctid.contains(knownNamespace)) {
								namespace = knownNamespace;
								break;
							}
						}
						if (namespace == null) {
							namespace = SnomedUtils.getNamespace(sctid);
						}
						if (!sctidsByNamespace.containsKey(namespace)) {
							sctidsByNamespace.put(namespace, new HashSet<>());
						}
						sctidsByNamespace.get(namespace).add(sctid);
					}
				}
			}
		} catch (IOException e) {
			LOGGER.error("Failed to process " + projectName);
		}
	}

	public void postInit() throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "13XiH3KVll3v0vipVxKwWjjf-wmjzgdDe";  // Technical Specialist
		String[] columnHeadings = new String[] {	"Summary Item, Detail, ",
													"Request, JobId, Response,  , , ",
													"SCTID, JobId, Status"};
		String[] tabNames = new String[] {	"Summary",
											"Batch Request/Response",
											"SCTID Detail"};
		super.postInit(tabNames, columnHeadings, false);
	}


	
	

}

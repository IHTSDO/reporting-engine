package org.ihtsdo.termserver.scripting.cis;

import com.google.common.collect.Lists;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.client.CisClient;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * In this case, the cookie passed in will not be the usual ims cookie, but the cis token
 */
public class PublishSctids extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(PublishSctids.class);

	private enum ACTION {PUBLISH, REGISTER};

	public static String AVAILABLE = "Available";
	public static String RESERVED = "Reserved";
	public static String DEPRECATED = "Deprecated";
	public static String ASSIGNED = "Assigned";
	public static String PUBLISHED = "Published";

	private Map<String, Set<String>> newSctidsByNamespace = new HashMap<>();
	private Map<String, Set<String>> oldSctidsByNamespace = new HashMap<>();

	private Set<String> namespaces = new HashSet<>();
	private String targetET = "20231130";
	private int batchSize = 100;
	private boolean includeLegacySCTIDS = true;
	private boolean publishInternationalSCTIDS = true;

	private CisClient cisClient;
	

	public static void main(String[] args) throws TermServerScriptException {
		PublishSctids report = new PublishSctids();
		try {
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
		if (includeLegacySCTIDS) {
			return;
		}

		for (String namespace : newSctidsByNamespace.keySet()) {
			 newSctidsByNamespace.get(namespace).removeAll(oldSctidsByNamespace.get(namespace));
		}
	}

	@Override
	public void init(String[] args) throws TermServerScriptException {
		super.init(args);
		String url = "https://cis.ihtsdotools.org/";
		cisClient = new CisClient(url, authenticatedCookie);
	}


	private void publishSCTIDS() throws TermServerScriptException {
		if (newSctidsByNamespace.isEmpty()) {
			LOGGER.info("No SCTIDs to publish");
			report(PRIMARY_REPORT, "No SCTIDs detected to publish");
			return;
		}

		for (String namespace : newSctidsByNamespace.keySet()) {
			List<String> sctids = new ArrayList<>(newSctidsByNamespace.get(namespace));
			if ((!publishInternationalSCTIDS && namespace.equals("0"))
					|| sctids.size() == 0) {
				LOGGER.info("Skipping " + sctids.size() + " sctids for namespace " + namespace);
				continue;
			}
			LOGGER.info("Processing " + sctids.size() + " sctids for namespace " + namespace);
			List<List<String>> batches = Lists.partition(sctids, batchSize);
			int batchCount = 0;
			int originalBatchSize = batches.size();  //This will change as we remove empty batches
			for (List<String> batch : batches) {
				LOGGER.debug("Processing batch {} of {} sctids", (++batchCount + "/" + originalBatchSize), batch.size());
				//Filter out those records that are currently 'Reserved'
				//Take a copy of the list because the loop doesn't like becoming empty during processing!
				batch = removeAndReportReserved(namespace, new ArrayList<String>(batch));
				//Have we lost all of them?
				if (batch.isEmpty()) {
					LOGGER.info("Skipping empty batch - none remain to be published after filtering.");
					continue;
				}

				transitionSCTIDS(batch, namespace, ACTION.PUBLISH);
				flushFilesWithWait(false);
			}
		}
	}

	private void transitionSCTIDS(List<String> batch, String namespace, ACTION action) throws TermServerScriptException {
		String actionStr = action.toString().toLowerCase();

		CisResponse response = null;
		String requestStr = null;
		switch (action) {
			case REGISTER:
				CisBulkRegisterRequest cisBulkRegisterRequest = new CisBulkRegisterRequest("RP-836 Bulk " + actionStr + " of " + batch.size() + " sctids",
						Long.parseLong(namespace),
						batch,
						"Script-Engine");
				requestStr = cisBulkRegisterRequest.toString();
				response = cisClient.registerSctids(cisBulkRegisterRequest);
				break;
			case PUBLISH:
				CisBulkRequest cisBulkRequest = new CisBulkRequest("RP-836 Bulk " + actionStr + " of " + batch.size() + " sctids",
						Long.parseLong(namespace),
						batch,
						"Script-Engine");
				requestStr = cisBulkRequest.toString();
				response = cisClient.publishSctids(cisBulkRequest);
				break;
			default:
				throw new TermServerScriptException("Unsupported action: " + action);
		}
		List<CisRecord> records = cisClient.getBulkJobBlocking(response.getId());
		report(SECONDARY_REPORT, requestStr, response);
		for (CisRecord record : records) {
			incrementSummaryInformation("SCTIDs " + actionStr + "ed namespace " + namespace);
			report(TERTIARY_REPORT, record.getSctid(), response.getId(), record.getStatus());
		}
		LOGGER.info(StringUtils.capitalizeFirstLetter(actionStr) + "ed {} SCTIDS for namespace {}", records.size(), namespace);
	}

	private List<String> removeAndReportReserved(String namespace, List<String> batch) throws TermServerScriptException {
		//We need to recover the current state so we can 'Assign' SCTIDs that are currently only at Status 'Reserved'
		List<CisRecord> currentStatus = cisClient.getSCTIDs(batch);
		List<String> availableSCTIDs = new ArrayList<>();
		for (CisRecord record : currentStatus) {
			if (record.getStatus().equals(RESERVED)) {
				incrementSummaryInformation("SCTIDs stuck at reserved");
				report(TERTIARY_REPORT, record.getSctid(), "SCTID is currently reserved. Cannot assign without calculating systemId", record);
				batch.remove(record.getSctid().toString());
			} else if (record.getStatus().equals(PUBLISHED)) {
				incrementSummaryInformation("SCTIDs already published");
				report(TERTIARY_REPORT, record.getSctid(), "SCTID is already published.", record);
				batch.remove(record.getSctid().toString());
			} else if (!record.getStatus().equals(AVAILABLE)) {
				incrementSummaryInformation("SCTIDs state " + record.getStatus());
				report(TERTIARY_REPORT, record.getSctid(), "SCTID status: " + record.getStatus() + " moving to be ASSIGNED", record);
				availableSCTIDs.add(record.getSctid().toString());
			} else if (!record.getStatus().equals(ASSIGNED)) {
				incrementSummaryInformation("SCTIDs state " + record.getStatus());
				report(TERTIARY_REPORT, record.getSctid(), "SCTID status: " + record.getStatus() + " cannot be published.", record);
				batch.remove(record.getSctid().toString());
			}
		}

		if (availableSCTIDs.size() > 0) {
			transitionSCTIDS(availableSCTIDs, namespace, ACTION.REGISTER);
		}

		return batch;
	}

	private void registerSCTIDS(List<CisRecord> records) {
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
						if (!includeLegacySCTIDS && findNewSCTIDs != effectiveTime.equals(this.targetET)) {
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
							namespace = SnomedUtilsBase.getNamespace(sctid);
						}
						if (!sctidsByNamespace.containsKey(namespace)) {
							sctidsByNamespace.put(namespace, new HashSet<>());
						}
						sctidsByNamespace.get(namespace).add(sctid);
					}
				}
			}
		} catch (IOException e) {
			LOGGER.error("Failed to process {}", projectName, e);
		}
	}

	public void postInit() throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("13XiH3KVll3v0vipVxKwWjjf-wmjzgdDe");  // Technical Specialist
		String[] columnHeadings = new String[] {	"Summary Item, Detail, ",
													"Request, JobId, Response,  , , ",
													"SCTID, JobId, Status"};
		String[] tabNames = new String[] {	"Summary",
											"Batch Request/Response",
											"SCTID Detail"};
		super.postInit(tabNames, columnHeadings);
	}


	
	

}

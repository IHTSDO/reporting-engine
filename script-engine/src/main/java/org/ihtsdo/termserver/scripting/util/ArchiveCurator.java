package org.ihtsdo.termserver.scripting.util;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.termserver.scripting.dao.VersionedContentLoaderConfig;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.module.storage.ModuleMetadata;
import org.snomed.module.storage.ModuleStorageCoordinator;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArchiveCurator extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveCurator.class);
	private static final String INT = "INT";

	private Map<String, String> extensionModuleMap = new HashMap<>();
	private Pattern msFilePattern = Pattern.compile("ManagedService([A-Z]{2}).*?([0-9]{8})T[0-9]{6}Z");
	private Pattern intFilePattern = Pattern.compile("International.*?([0-9]{8})T120000Z");
	private ModuleStorageCoordinator moduleStorageCoordinator;
	private String localCache = "UNKNOWN";

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		ArchiveCurator curator = new ArchiveCurator();
		try {
			ReportSheetManager.targetFolderId = "13XiH3KVll3v0vipVxKwWjjf-wmjzgdDe"; //Technical Specialist
			curator.init(args);
			curator.postInit(null, new String[] {"Archive, Result, , ,"}, false);
			curator.curateArchives();
		} finally {
			curator.finish();
		}
	}

	private void curateArchives() throws TermServerScriptException, IOException {
		populateExtensionModuleMap();
		VersionedContentLoaderConfig config = new VersionedContentLoaderConfig();
		config.init("versioned-content");
		ResourceLoader rl = getArchiveManager().getS3Manager().getResourceLoader();
		ResourceManager rm = new ResourceManager(config, rl);
		localCache = rm.getCachePath();
		moduleStorageCoordinator = ModuleStorageCoordinator.initDev(rm);

		LOGGER.info("Processing all archives in " + rm.getCachePath());
		List<ModuleMetadata> metadataList = new ArrayList<>();
		for (String archiveStr : rm.listCachedFilenames(null)){
			try {
				if (archiveStr.endsWith(".zip")) {
					LOGGER.info("Processing: " + archiveStr);
					metadataList.add(determineInitialMetadata(archiveStr));
				}
			} catch (Exception e) {
				String msg = "Failed to determine initial metadata: " + archiveStr;
				LOGGER.warn(msg, e);
				report(PRIMARY_REPORT, archiveStr, ExceptionUtils.getExceptionCause(msg, e));
			}
		}

		ModuleMetadata.sortByCS(metadataList, false);
		for (ModuleMetadata metadata : metadataList) {
			try {
				moduleStorageCoordinator.generateMetadata(metadata);
				report(PRIMARY_REPORT, metadata.getFilename(), metadata);
			} catch (Exception e) {
				String msg = "Failed to process archive: " + metadata.getFilename();
				LOGGER.warn(msg, e);
				report(PRIMARY_REPORT, metadata.getFilename(), ExceptionUtils.getExceptionCause(msg, e));
			}
		}
	}

	private ModuleMetadata determineInitialMetadata(String archiveStr) throws TermServerScriptException {
		try {
			//Determine the two letter extension code and the release date from the archive filename
			Matcher extMatcher = msFilePattern.matcher(archiveStr);
			String cs = "UNKNOWN";
			String et = "UNKNOWN";

			if (extMatcher.find()) {
				cs = extMatcher.group(1);
				et = extMatcher.group(2);
				LOGGER.info("Identified: {}_{}", cs, et);
			} else {
				Matcher intMatcher = intFilePattern.matcher(archiveStr);
				if (intMatcher.find()) {
					et = intMatcher.group(1);
					cs = INT;
					LOGGER.info("Identified: INT_{}", et);
				} else {
					LOGGER.warn("Pattern not found in the input string: " + archiveStr);
				}
			}

			return new ModuleMetadata()
					.withCodeSystemShortName(cs)
					.withEffectiveTime(Integer.parseInt(et))
					.withIdentifyingModuleId(extensionModuleMap.get(cs))
					.withFile(new File(localCache + File.separator + archiveStr));
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to determine metadata for archive: " + archiveStr, e);
		}
	}

	private void populateExtensionModuleMap() {
		for (Project p : scaClient.listProjects()) {
			if (p.getKey().length() == 2) {
				LOGGER.info("Project: " + p.getKey() + " " + p.getMetadata().getDefaultModuleId());
				extensionModuleMap.put(p.getKey(), p.getMetadata().getDefaultModuleId());
			}
		}
		extensionModuleMap.put("INT", SCTID_CORE_MODULE);
	}



}

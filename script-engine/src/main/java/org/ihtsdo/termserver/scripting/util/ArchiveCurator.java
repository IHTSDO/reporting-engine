package org.ihtsdo.termserver.scripting.util;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.ihtsdo.termserver.scripting.dao.VersionedContentLoaderConfig;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.module.storage.ModuleMetadata;
import org.snomed.module.storage.ModuleStorageCoordinator;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArchiveCurator extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveCurator.class);

	private Map<String, String> extensionModuleMap = new HashMap<>();
	private Pattern msFilePattern = Pattern.compile("ManagedService([A-Z]{2}).*?([0-9]{8})T[0-9]{6}Z");
	private Pattern intFilePattern = Pattern.compile("International.*?([0-9]{8})T120000Z");
	private ModuleStorageCoordinator moduleStorageCoordinator;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		ArchiveCurator curator = new ArchiveCurator();
		try {
			ReportSheetManager.targetFolderId = "13XiH3KVll3v0vipVxKwWjjf-wmjzgdDe"; //Technical Specialist
			curator.init(args);
			curator.postInit(null, new String[] {"Task, Archive, Result"}, false);
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
		moduleStorageCoordinator = ModuleStorageCoordinator.initDev(rm);
		
		LOGGER.info("Processing all archives in " + rm.getCachePath());
		//Arrays.sort(dirListing, NumberAwareStringComparator.INSTANCE);
		for (String archiveStr : rm.listCachedFilenames(null)){
			try {
				if (archiveStr.endsWith(".zip")) {
					LOGGER.info("Processing: " + archiveStr);
					determinelMetadata(archiveStr);
				}
			} catch (Exception e) {
				report(PRIMARY_REPORT, archiveStr, e);
			}
		}
	}

	private ModuleMetadata determineMetadata(String archiveStr) {
		//Determine the two letter extension code and the release date from the archive filename
		Matcher extMatcher = msFilePattern.matcher(archiveStr);
		String cs;
		String et;
		if (extMatcher.find()) {
			cs = extMatcher.group(1);
			et = extMatcher.group(2);
			LOGGER.info("Identified: {}_{}", cs, et);
		} else {
			Matcher intMatcher = intFilePattern.matcher(archiveStr);
			if (intMatcher.find()) {
				et = intMatcher.group(1);
				cs = "INT";
				LOGGER.info("Identified: INT_{}", et);
			} else {
				LOGGER.warn("Pattern not found in the input string: " + archiveStr);
			}
		}
		
		ModuleMetadata mm = new ModuleMetadata()
				.withCodeSystemShortName(cs)
				.withEffectiveTime(et)
				.withIdentifyingModuleId(extensionModuleMap.get(cs));
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

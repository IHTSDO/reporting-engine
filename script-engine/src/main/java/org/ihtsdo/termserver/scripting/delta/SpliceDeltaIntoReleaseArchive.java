package org.ihtsdo.termserver.scripting.delta;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClient.ExtractType;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Watch that this is a partial implementation that will be added to as the need arrises.
 * For INFRA-9963 we only need to splice in new Common French descriptions to a published Swiss package
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpliceDeltaIntoReleaseArchive extends DeltaGenerator implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(SpliceDeltaIntoReleaseArchive.class);

	public static final String SCTID_CF_LRS = "21000241105";   //Common French Language Reference Set
	public static final String SCTID_CF_MOD = "11000241103";   //Common French Module
	public static final String SCTID_CH_MOD = "2011000195101"; //Swiss Module
	public static final String SCTID_CH_LRS = "2021000195106"; //Swiss French Language Reference Set
	
	Map<String, String[]> descriptionMap = new HashMap<>();
	Map<String, InactivationIndicatorEntry> cfInactivationIndicatorsMapSept = new HashMap<>();
	public static final String ET = "20220930";
	
	Map<String, SummaryCount> summaryCounts = new HashMap<>();
	
	private static final Map<String, String> fileMap = new HashMap<>();
	static {
		//Package filename, mapped to the name of the delta filename to be pulled in
		fileMap.put("sct2_Description_#TYPE#-fr_CH1000195_20221207.txt", "sct2_Description_Delta_CommonFrench-Extension");
	}
	
	public static void main(String[] args) throws TermServerScriptException {
		SpliceDeltaIntoReleaseArchive delta = new SpliceDeltaIntoReleaseArchive();
		try {
			delta.sourceModuleIds = Set.of(SCTID_CH_MOD);
			delta.runStandAlone = true;
			delta.newIdsRequired = false;
			delta.init(args);
			delta.postInit(GFOLDER_ADHOC_UPDATES);
			delta.process();
			delta.getRF2Manager().flushFiles(true);  //Flush and Close
			SnomedUtils.createArchive(new File(delta.outputDirName));
			delta.outputSummaryCounts();
		} finally {
			delta.finish();
		}
	}

	@Override
	public void postInit(String googleFolder) throws TermServerScriptException {
		String[] columnHeadings = new String[]{
			"File, Severity, Action, Details, Details, , "
		};
		
		String[] tabNames = new String[]{
			"Splicing"
		};
		super.postInit(googleFolder, tabNames, columnHeadings);
	}

	@Override
	protected void initialiseFileHeaders() throws TermServerScriptException {
		LOGGER.info("Skipping initialisation of usual delta output files");
	}
	
	private  void outputSummaryCounts() throws TermServerScriptException {
		report(PRIMARY_REPORT, "");
		for (Map.Entry<String, SummaryCount> summaryCountEntry : summaryCounts.entrySet()) {
			String filename = summaryCountEntry.getKey();
			SummaryCount summaryCount = summaryCountEntry.getValue();
			report(PRIMARY_REPORT, filename, "Rows passed through", summaryCount.rowsPassedThrough);
			report(PRIMARY_REPORT, filename, "Rows updated", summaryCount.rowsUpdated);
			report(PRIMARY_REPORT, filename, "Rows added", summaryCount.rowsAdded);
		}
	}

	@Override
	public void process() throws TermServerScriptException {
		try {
			loadDelta();
			LOGGER.info("Processing {}", getInputFile(1));
			ZipInputStream zis = new ZipInputStream(new FileInputStream(getInputFile(1)));
			ZipEntry ze = zis.getNextEntry();
			try {
				while (ze != null) {
					if (!ze.isDirectory()) {
						Path path = Paths.get(ze.getName());
						processFile(path, zis);
					}
					ze = zis.getNextEntry();
				}
			} finally {
				close(zis);
			}
			LOGGER.info("Finished Loading {}", getInputFile(1));
			getRF2Manager().flushFiles(false);
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to process " + getInputFile(1), e);
		}
	}

	private void processFile(Path path, InputStream is) throws TermServerScriptException, IOException {
		String pathStr = path.toString();
		File targetFile = new File(outputDirName + File.separator + path.toString());
		
		//Is this a file we're interested in modifying?
		ExtractType type = SnomedUtils.getExtractType(pathStr);
		
		if (pathStr.contains("fr_CH")) {
			LOGGER.debug("here");
		}
		
		if (type == null || !isFileOfInterest(pathStr, type)) {
			report(PRIMARY_REPORT, pathStr, Severity.LOW, ReportActionType.NO_CHANGE, targetFile);
			LOGGER.info("Passing through {}", pathStr);
			FileUtils.copyToFile(is, targetFile);
		} else {
			SummaryCount summaryCount = new SummaryCount();
			summaryCounts.put(pathStr, summaryCount);
			//We're not expecting to output a delta. 
			//If it's the full we just add in all the rows.
			//If it's Snapshot, we pass through unless we detect a row where we have an entry for that id, and also
			//top up with new rows that haven't been used at the end.
			if (type == ExtractType.SNAPSHOT) {
				LOGGER.info("Splicing {}", pathStr);
				processSnapshotFile(pathStr, is, targetFile, descriptionMap, summaryCount);
			} else if (type == ExtractType.FULL) {
				LOGGER.info("Appending to {}", pathStr);
				FileUtils.copyToFile(is, targetFile);
				for (String[] columns : descriptionMap.values()) {
					writeToRF2File(targetFile.getAbsolutePath(), columns);
					summaryCount.rowsAdded++;
				}
			} else {
				throw new IllegalArgumentException("Wasn't expecting to process " + type + " file " + pathStr);
			}
		}
	}

	private void processSnapshotFile(String pathStr, InputStream is, File targetFile, Map<String, String[]> replacementMap, SummaryCount summaryCount) throws IOException, TermServerScriptException {
		Set<String> idsReplaced = new HashSet<>();
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			String[] lineItems = line.split(FIELD_DELIMITER);
			if (!isHeaderLine) {
				String id = lineItems[IDX_ID];
				String et = lineItems[IDX_EFFECTIVETIME];
				//Do we have a replacement for this row?
				if (replacementMap.containsKey(id)) {
					if (et.compareTo(ET) >= 0) {
						throw new IllegalStateException("Attempting to replace row where later effective time already exists: " + line);
					}
					lineItems = replacementMap.get(id);
					summaryCount.rowsUpdated++;
					idsReplaced.add(id);
					report(PRIMARY_REPORT, pathStr, Severity.LOW, ReportActionType.COMPONENT_UPDATED, id);
				}
			} else {
				isHeaderLine = false;
			}
			
			summaryCount.rowsPassedThrough++;
			writeToRF2File(targetFile.getAbsolutePath(), lineItems);
		}
		
		//Now add in any rows that we haven't already processed
		for (String idRemaining : replacementMap.keySet()) {
			if (idsReplaced.contains(idRemaining)) {
				continue;
			}
			summaryCount.rowsAdded++;
			report(PRIMARY_REPORT, pathStr, Severity.LOW, ReportActionType.COMPONENT_ADDED, idRemaining);
			writeToRF2File(targetFile.getAbsolutePath(), replacementMap.get(idRemaining));
		}
	}

	private boolean isFileOfInterest(String pathStr, ExtractType type) {
		for (String fileOfInterestTemplate : fileMap.keySet()) {
			String fileOfInterest = fileOfInterestTemplate.replace("#TYPE#", SnomedUtils.getExtractTypeString(type));
			if (pathStr.contains(fileOfInterest)) {
				return true;
			}
		}
		return false;
	}

	private void loadDelta() throws IOException {
		LOGGER.info("Loading {}", getInputFile());
		ZipInputStream zis = new ZipInputStream(new FileInputStream(getInputFile()));
		ZipEntry ze = zis.getNextEntry();
		try {
			while (ze != null) {
				if (!ze.isDirectory()) {
					Path path = Paths.get(ze.getName());
					loadFile(path, zis, DELTA);
				}
				ze = zis.getNextEntry();
			}
		} finally {
			try{
				zis.closeEntry();
				zis.close();
			} catch (Exception e){} //Well, we tried.
		}
		LOGGER.info("Finished Loading {}", getInputFile());
	}
	
	private void loadFile(Path path, InputStream is, String fileType)  {
		try {
			String fileName = path.getFileName().toString();
			if (fileName.contains("._")) {
				return;
			}
			
			if (fileName.contains(fileType) && fileName.contains("sct2_Description_" )) {
				LOGGER.info("Loading Description {} file.", fileType);
				loadDescriptionFile(is);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Unable to load " + path + " due to " + e.getMessage(), e);
		}
	}
	
	public void loadDescriptionFile(InputStream is) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				String id = lineItems[IDX_ID];
				descriptionMap.put(id, lineItems);
			} else {
				isHeaderLine = false;
			}
		}
	}
	
	/*public void loadLanguageFile(InputStream is) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				LangRefsetEntry langRefsetEntry = LangRefsetEntry.fromRf2(lineItems);
				String descId = langRefsetEntry.getReferencedComponentId();
				if (cfLangRefsetMapSept.containsKey(descId)) {
					throw new IllegalStateException("Multiple LangRefsets Encountered for " + descId);
				}
				cfLangRefsetMapSept.put(descId, langRefsetEntry);
			} else {
				isHeaderLine = false;
			}
		}
	}
	
	public void loadInactivationIndicatorFile(InputStream is) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				InactivationIndicatorEntry i = InactivationIndicatorEntry.fromRf2(lineItems);
				cfInactivationIndicatorsMapSept.put(i.getReferencedComponentId(), i);
			} else {
				isHeaderLine = false;
			}
		}
	}*/
	
	class SummaryCount {
		int rowsPassedThrough = 0;
		int rowsUpdated = 0;
		int rowsAdded = 0;
	}

}

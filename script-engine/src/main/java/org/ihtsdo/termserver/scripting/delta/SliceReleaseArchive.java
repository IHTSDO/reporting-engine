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
 * @author peter
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SliceReleaseArchive extends DeltaGenerator implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(SliceReleaseArchive.class);

	Set<String> knownDescriptions = new HashSet<>();
	Map<String, SummaryCount> summaryCounts = new HashMap<>();
	
	public static Set<String> filesOfInterest = new HashSet<>(); 
	static {
		//Package filename, mapped to the name of the delta filename to be pulled in
		filesOfInterest.add("der2_cRefset_Language#TYPE#-fr-ch_CH1000195_20221207.txt");
	}
	
	public static void main(String[] args) throws TermServerScriptException {
		SliceReleaseArchive delta = new SliceReleaseArchive();
		try {
			delta.runStandAlone = true;
			delta.newIdsRequired = false; 
			delta.init(args);
			//delta.loadProjectSnapshot(false); //Don't need anything in memory for this
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
	
	private void outputSummaryCounts() throws TermServerScriptException {
		report(PRIMARY_REPORT, "");
		for (Map.Entry<String, SummaryCount> summaryCountEntry : summaryCounts.entrySet()) {
			String fileName = summaryCountEntry.getKey();
			SummaryCount summaryCount = summaryCountEntry.getValue();
			output(fileName, "Rows passed through", summaryCount.rowsPassedThrough);
			output(fileName, "Rows updated", summaryCount.rowsUpdated);
			output(fileName, "Rows added", summaryCount.rowsAdded);
			output(fileName, "Rows skipped", summaryCount.rowsSkipped);
			output(fileName, "Rows passed through but EN", summaryCount.rowsProblematic);
			//output("", "Concepts Affected", conceptsAffected.size());
		}
	}

	private void output(String fileName, String msg, int data) throws TermServerScriptException {
		LOGGER.info(fileName + ": " + msg + " - " + data);
		report(PRIMARY_REPORT, fileName, msg, data);
	}

	@Override
	public void process() throws TermServerScriptException {
		try {
			firstPass();
			LOGGER.info("{} descriptions are known", knownDescriptions.size());
			LOGGER.info("Second Pass {}", getInputFile());
			ZipInputStream zis = new ZipInputStream(new FileInputStream(getInputFile()));
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
			LOGGER.info("Finished Processing {}", getInputFile());
			getRF2Manager().flushFiles(false);
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to process " + getInputFile(), e);
		}
	}

	private void processFile(Path path, InputStream is) throws TermServerScriptException, IOException {
		String pathStr = path.toString();
		File targetFile = new File(outputDirName + File.separator + path.toString());
		
		//Is this a file we're interested in modifying?
		ExtractType type = SnomedUtils.getExtractType(pathStr);
		
		if (type == null || !isFileOfInterest(pathStr, type)) {
			report(PRIMARY_REPORT, pathStr, Severity.LOW, ReportActionType.NO_CHANGE, targetFile);
			LOGGER.info("Passing through {}", pathStr);
			FileUtils.copyToFile(is, targetFile);
		} else {
			SummaryCount summaryCount = new SummaryCount();
			summaryCounts.put(path.getFileName().toString(), summaryCount);
			LOGGER.info("Slicing {}", pathStr);
			processLangFile(pathStr, is, targetFile, summaryCount);
		}
	}

	private void processLangFile(String pathStr, InputStream is, File targetFile, SummaryCount summaryCount) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			String[] lineItems = line.split(FIELD_DELIMITER);
			if (!isHeaderLine) {
				String refCompId = lineItems[ASSOC_IDX_REFCOMPID];
				//Do we know about the description that this lang refset?
				if (!knownDescriptions.contains(refCompId)) {
					//Could we be looking at a English description?
					if (SnomedUtils.isExtensionSCTID(refCompId)) {
						report(PRIMARY_REPORT, pathStr, Severity.MEDIUM, ReportActionType.COMPONENT_DELETED, line);
						summaryCount.rowsSkipped++;
						continue;
					} else {
						summaryCount.rowsProblematic++;
					}
				}
			} else {
				isHeaderLine = false;
			}
			
			summaryCount.rowsPassedThrough++;
			writeToRF2File(targetFile.getAbsolutePath(), lineItems);
		}
	}

	private boolean isFileOfInterest(String pathStr, ExtractType type) {
		for (String fileOfInterestTemplate : filesOfInterest) {
			String fileOfInterest = fileOfInterestTemplate.replace("#TYPE#", SnomedUtils.getExtractTypeString(type));
			if (pathStr.contains(fileOfInterest)) {
				return true;
			}
		}
		return false;
	}

	private void firstPass() throws IOException, TermServerScriptException {
		LOGGER.info("Loading {}", getInputFile());
		ZipInputStream zis = new ZipInputStream(new FileInputStream(getInputFile()));
		ZipEntry ze = zis.getNextEntry();
		try {
			while (ze != null) {
				if (!ze.isDirectory()) {
					Path path = Paths.get(ze.getName());
					loadFile(path, zis, SNAPSHOT);
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
			
			if (fileName.contains(fileType) && fileName.contains("sct2_Description_" ) || fileName.contains("sct2_Text" )) {
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
				knownDescriptions.add(id);
			} else {
				isHeaderLine = false;
			}
		}
	}
	
	class SummaryCount {
		int rowsPassedThrough = 0;
		int rowsUpdated = 0;
		int rowsAdded = 0;
		int rowsSkipped = 0;
		int rowsProblematic = 0;
	}

}

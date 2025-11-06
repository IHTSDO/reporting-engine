package org.ihtsdo.termserver.scripting.delta;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Rf2File;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
/**
 * ISRS-1256 Requirement to update an existing archive with a Delta
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApplyDeltaToArchive extends DeltaGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApplyDeltaToArchive.class);

	Map<String, Map<String, String>> deltaArchiveMap = new HashMap<>();
	Map<String, String> originalFileNames = new HashMap<>();
	Set<String> filesProcessed = new HashSet<>();
	
	String oldEffectiveTime;
	String newEffectiveTime;
	String filenameModule = "CH1000195";
	
	public static void main(String[] args) throws TermServerScriptException {
		ApplyDeltaToArchive app = new ApplyDeltaToArchive();
		try {
			app.newIdsRequired = false;
			app.dryRun = false;
			app.init(args);
			app.loadDeltaArchive();
			app.applyDeltaToArchive();
			app.mopUp();
			SnomedUtils.createArchive(new File(app.outputDirName));
		} finally {
			app.finish();
		}
	}

	private void loadDeltaArchive() throws TermServerScriptException{
		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(getInputFile()));
			ZipEntry ze = zis.getNextEntry();
			try {
				while (ze != null) {
					if (!ze.isDirectory()) {
						Path p = Paths.get(ze.getName());
						String fileName = p.getFileName().toString();
						if (newEffectiveTime == null) {
							newEffectiveTime = extractEffectiveTime(fileName);
						}
						if (fileName.contains("Delta")) {
							if (fileName.contains("Language")) {
								LOGGER.info("Skipping all Language files eg " + fileName);
							} else {
								String fileNameShort = getFileNameShort(fileName, "Delta");
								ComponentType componentType = Rf2File.getComponentType(fileName, FileType.DELTA);
								if (componentType != null && !fileName.startsWith("._")) {
									LOGGER.info("Processing " + fileName);
									Map<String, String> fileMap = processFixDeltaFile(zis, componentType);
									deltaArchiveMap.put(fileNameShort, fileMap);
									originalFileNames.put(fileNameShort, fileName);
								} else {
									LOGGER.info("Skipping unrecognised file: " + fileName);
								}
							}
						}
					}
					ze = zis.getNextEntry();
				}
			} finally {
				zis.closeEntry();
				zis.close();
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to process delta " + getInputFile(), e);
		}
	}

	private String extractEffectiveTime(String fileName) {
		Pattern r = Pattern.compile("\\d{8}+T");
		Matcher m = r.matcher(fileName);
		if (m.find()) {
			return m.group();
		}
		
		r = Pattern.compile("\\d{8}+");
		m = r.matcher(fileName);
		if (m.find()) {
			return m.group();
		}
		return null;
	}

	private Map<String, String> processFixDeltaFile(InputStream is, ComponentType componentType) throws Exception {
		//Not putting this in a try resource block otherwise it will close the stream on completion and we've got more to read!
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		String line;
		boolean isHeader = true;
		Map<String, String> fileMap = new HashMap<>();
		while ((line = br.readLine()) != null) {
			if (!isHeader) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				String id = lineItems[IDX_ID];
				if (fileMap.containsKey(id)) {
					throw new TermServerScriptException("Duplicate row for " + id);
				}
				fileMap.put(id, line);
			} else {
				isHeader = false;
			}
		}
		return fileMap;
	}
	
	private void applyDeltaToArchive() throws TermServerScriptException {
		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(project.getKey()));
			ZipEntry ze = zis.getNextEntry();
			try {
				while (ze != null) {
					if (!ze.isDirectory()) {
						Path p = Paths.get(ze.getName());
						String fileName = p.toString();
						int cut = fileName.indexOf("\\");
						fileName = fileName.substring(cut + 1);
							if (fileName.contains("Delta")) {
								processDeltaFull(fileName, zis, "Delta");
							} else if (fileName.contains("Full")) {
								processDeltaFull(fileName, zis, "Full");
							} else if (fileName.contains("Snapshot")) {
								processSnapshot(fileName, zis);
							} else {
								LOGGER.info("Skipping unrecognised file: " + fileName);
							}
						}
					ze = zis.getNextEntry();
				}
			} finally {
				zis.closeEntry();
				zis.close();
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to process archive " + project.getKey(), e);
		}
	}

	private void processDeltaFull(String fileName, ZipInputStream zis, String fileType) throws IOException, TermServerScriptException {
		//Delta and Full processing just need to add the new delta onto the existing file
		String targetFileName = calculateTargetFileName(fileName);
		String fileNameShort = getFileNameShort(fileName, fileType);
		
		//Pass through
		BufferedReader br = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
		String line;
		while ((line = br.readLine()) != null) {
			writeToRF2File(targetFileName, line);
		}
		
		//Now do we have any rows to add from the delta?
		if (fileNameShort.contains("Lang") && !fileNameShort.contains("-fr_")) {
			LOGGER.warn(fileType + " processing, suppressing LangRefset from Delta: " + fileNameShort);
		} else if (deltaArchiveMap.containsKey(fileNameShort)) {
			for (String deltaLine : deltaArchiveMap.get(fileNameShort).values()) {
				writeToRF2File(targetFileName, deltaLine);
			}
		}
	}

	private String getFileNameShort(String fileName, String fileType) {
		int from = fileName.lastIndexOf('\\');
		if (from == NOT_SET) {
			from = 0;
		} else {
			from++;
		}
		
		int to = fileName.indexOf(fileType, from);
		String prefix = fileName.substring(from, to);
		//We also need to pull out any language modifier
		int langCut = fileName.indexOf(filenameModule, from);
		
		//WARNING WORKAROUND FOR COMMON FRENCH ARCHIVE
		//Does not follow standard naming so we can't extract the language codes
		String langModifier = "_";
		if (langCut == NOT_FOUND) {
			if (fileName.contains("sct2_Description") 
					|| fileName.contains("sct2_TextDefinition") 
					|| fileName.contains("Language")) {
				langModifier = "-fr_";
			}
 		} else {
			langModifier = fileName.substring(to + fileType.length(), langCut);
 		}
		return prefix + langModifier;
	}

	private String calculateTargetFileName(String fileName) {
		if (oldEffectiveTime == null) {
			oldEffectiveTime = extractEffectiveTime(fileName);
		}
		return packageDir + "/" + fileName.replace(oldEffectiveTime, newEffectiveTime);
	}
	
	private void processSnapshot(String fileName, ZipInputStream zis) throws IOException, TermServerScriptException {
		String targetFileName = calculateTargetFileName(fileName);
		String fileNameShort = getFileNameShort(fileName, "Snapshot");
		//Did we get one of these files in our delta?
		Map<String, String> deltaFileContent = deltaArchiveMap.get(fileNameShort);
		Map<String, String> suppressContent = null;
		if (deltaFileContent == null) {
			//If we have langrefset entries from fr-ch that have been modified in the CF
			//Then we're going to not include those in the Snapshot
			if (fileNameShort.equals("der2_cRefset_Language-fr-ch_")) {
				suppressContent = deltaArchiveMap.get("der2_cRefset_Language-fr_");
				LOGGER.warn("Skipping CF content in fr-ch where same id modified in fr ");
			} else {
				LOGGER.warn("No content found to merge for " + fileNameShort);
			}
		} else {
			//WARNING WORKAROUND - Not including other langrefsets 
			if (fileNameShort.contains("Lang") && !fileNameShort.contains("-fr_")) {
				LOGGER.warn("Snapshot processing, suppressing LangRefset from Delta: " + fileNameShort);
				deltaFileContent = null;
			} else {
				LOGGER.info("Merging delta from " + fileNameShort + " to " + fileName);
			}
		}
		String line;
		Set<String> processedIds = new HashSet<>();
		boolean isHeader = true;
		BufferedReader br = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
		while ((line = br.readLine()) != null) {
			if (!isHeader) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				String id = lineItems[IDX_ID];
				if (suppressContent != null && suppressContent.containsKey(id)) {
					LOGGER.info("Switching fr-ch line back to ch : " + line);
				} else if (deltaFileContent != null && deltaFileContent.containsKey(id)) {
					writeToRF2File(targetFileName, deltaFileContent.get(id));
				} else {
					writeToRF2File(targetFileName, line);
				}
				processedIds.add(id);
			} else {
				isHeader = false;
				writeToRF2File(targetFileName, line);
			}
		}
		
		if (deltaFileContent != null) {
			//And then any components that have not been written should be added
			for (Map.Entry<String, String> idLine : deltaFileContent.entrySet()) {
				if (!processedIds.contains(idLine.getKey())) {
					writeToRF2File(targetFileName, idLine.getValue());
				}
			}
		}
		
		filesProcessed.add(fileNameShort);
	}
	
	private void mopUp() {
		//Are there any files that were present in the delta which we have not applied
		//to the full archive?
		for (String fileShortName : deltaArchiveMap.keySet()) {
			if (!filesProcessed.contains(fileShortName)) {
				String origFileName = originalFileNames.get(fileShortName);
				LOGGER.warn("Did not merge delta from " + fileShortName + " --> " + origFileName);
			}
		}
	}

}

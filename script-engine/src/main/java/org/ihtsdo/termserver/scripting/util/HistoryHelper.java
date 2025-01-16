package org.ihtsdo.termserver.scripting.util;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class HistoryHelper implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(HistoryHelper.class);

	public static final String FILE_TYPE = "Full";

	private static HistoryHelper singleton = null;

	private TermServerScript ts = null;

	Map<Long,Long> orginalPublicationDates = new HashMap<>();

	private HistoryHelper(TermServerScript ts) {
		//Singleton, obtain via "create"
		this.ts = ts;
	}

	public static HistoryHelper get(TermServerScript ts) throws TermServerScriptException {
		if (singleton != null) {
			return singleton;
		}
		singleton = new HistoryHelper(ts);
		singleton.init();
		return singleton;
	}

	private void init() throws TermServerScriptException {
		//Load the full concept file and record the earliest publication date for each concept
		LOGGER.info("Loading concept full file to determine earliest publication date");
		File previousPackage = ts.getArchiveManager().determinePreviousPackage(ts.getProject());
		loadArchiveZip(previousPackage);
		LOGGER.info("Completed import of historical data for {} concepts", orginalPublicationDates.size());
	}

	private void loadArchiveZip(File archive) throws TermServerScriptException {
		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(archive))) {
			ZipEntry ze = zis.getNextEntry();
			while (ze != null) {
				if (!ze.isDirectory()) {
					Path path = Paths.get(ze.getName());
					loadFile(path, zis);
				}
				ze = zis.getNextEntry();
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to load archive " + archive, e);
		}
	}

	private void loadFile(Path path, InputStream is) {
		try {
			String fileName = path.getFileName().toString();
			//Skip zip file artifacts
			if (fileName.contains("._")) {
				return;
			}

			if (fileName.contains(FILE_TYPE) && fileName.contains("sct2_Concept_")) {
				LOGGER.info("Loading Concept {} file: {}", FILE_TYPE, fileName);
				loadConceptFile(is);
			}
		} catch (Exception e) {
			LOGGER.error("Failed to load file {}", path, e);
		}
	}

	public void loadConceptFile(InputStream is) throws IOException {
		//Not putting this in a try resource block otherwise it will close the stream on completion and we've got more to read!
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		String line;
		boolean isHeaderLine = true;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				Long conceptIdL = Long.parseLong(lineItems[IDX_ID]);
				Long effectiveTimeL = Long.parseLong(lineItems[IDX_EFFECTIVETIME]);
				Long existingEffectiveTime = orginalPublicationDates.get(conceptIdL);
				if (existingEffectiveTime == null || effectiveTimeL < existingEffectiveTime) {
					orginalPublicationDates.put(conceptIdL, effectiveTimeL);
				}
			} else {
				isHeaderLine = false;
			}
		}
	}

	public String findOriginalPublicationDate(Concept c) {
		return Optional.ofNullable(orginalPublicationDates.get(Long.parseLong(c.getId())))
				.orElseThrow(() -> new NoSuchElementException("Unable to find original publication date for " + c)).toString();
	}
}

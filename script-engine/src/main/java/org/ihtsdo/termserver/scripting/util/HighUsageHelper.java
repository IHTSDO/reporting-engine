package org.ihtsdo.termserver.scripting.util;

import com.google.common.io.Files;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HighUsageHelper implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(HighUsageHelper.class);

	public static final String SOURCE_UK_NHS = "uk-nhs";

	private static HighUsageHelper singleton;

	Map<String, Map<String, Integer>> highUsagesMap = new HashMap<>();

	public static HighUsageHelper get() throws TermServerScriptException {
		if (singleton == null) {
			singleton = new HighUsageHelper();
			singleton.loadUkUsage();
		}
		return singleton;
	}

	private void loadUkUsage() throws TermServerScriptException {
		String fileName = "resources/HighVolumeSCTIDs.txt";
		LOGGER.debug("Loading UK High Usage {}", fileName );
		Map<String, Integer> ukUsage = new HashMap<>();
		highUsagesMap.put(SOURCE_UK_NHS, ukUsage);

		try {
			List<String> lines = Files.readLines(new File(fileName), StandardCharsets.UTF_8);
			for (String line : lines) {
				String id = line.split(TAB)[0];
				ukUsage.put(id, 1);
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to read " + fileName, e);
		}
		LOGGER.debug("Loaded {} items of UK High Usage", ukUsage.size());
	}

	public boolean isHighUsage(Concept c, String source) {
		Map<String, Integer> usage = highUsagesMap.get(source);
		if (usage == null) {
			throw new IllegalStateException("Usage requests from unknown source: " + source);
		}
		return usage.containsKey(c.getId());
	}

	public boolean containsUsageData(String source) {
		return highUsagesMap.containsKey(source);
	}
}



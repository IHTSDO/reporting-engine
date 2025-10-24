package org.ihtsdo.termserver.scripting.util;

import org.ihtsdo.otf.exception.TermServerScriptException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HighVolumeUsageHelper {
	private static final String USAGE_FILENAME = "resources/aggregated_UK_usage_with_decile.tsv";
	private Map<String, Usage> usageMap;
	private int highUsageCutOff = 5;

	public Usage getUsage(String sctid) throws TermServerScriptException {
		ensureInitialised();
		return usageMap.get(sctid);
	}

	public boolean hasRecentHighUsage(String sctid) throws TermServerScriptException {
		ensureInitialised();
		//return true if the recent usage is 5 or greater
		if (usageMap.containsKey(sctid)) {
			return Integer.parseInt(usageMap.get(sctid).thisYearDecile) >= highUsageCutOff;
		}
		return false;
	}

	private void ensureInitialised() throws TermServerScriptException {
		if (usageMap == null) {
			try {
				loadUsageFile();
			} catch (IOException e) {
				throw new TermServerScriptException(e);
			}
		}
	}

	public boolean hasHighLifetimeUsage(String sctid) throws TermServerScriptException {
		ensureInitialised();
		//return true if the recent usage is 5 or greater
		if (usageMap.containsKey(sctid)) {
			return Integer.parseInt(usageMap.get(sctid).usageDecile) >= highUsageCutOff;
		}
		return false;
	}

	public static class Usage {
		private final String lifetimeUsageCount;       // human-readable string
		private final String usageDecile;
		private final String thisYearDecile;

		public Usage(String lifetimeUsageCount, String usageDecile, String thisYearDecile) {
			this.lifetimeUsageCount = lifetimeUsageCount;
			this.usageDecile = usageDecile;
			this.thisYearDecile = thisYearDecile;
		}

		public String getLifetimeUsageCount() {
			return lifetimeUsageCount;
		}

		public String getUsageDecile() {
			return usageDecile;
		}

		public String getThisYearDecile() {
			return thisYearDecile;
		}

		@Override
		public String toString() {
			return "Usage {" +
					"lifetimeUsage='" + lifetimeUsageCount + '\'' +
					", usageDecile=" + usageDecile +
					", thisYearDecile=" + thisYearDecile +
					'}';
		}
	}

	/**
	 * Reads a TSV file with columns:
	 * SCTID  TotalUsage  UsageDecile  ThisYearDecile
	 * @return a Map keyed by SCTID containing Usage objects
	 * @throws IOException on file read errors
	 */
	private  void loadUsageFile() throws IOException {
		usageMap = new HashMap<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(USAGE_FILENAME))) {
			String header = reader.readLine(); // skip header
			if (header == null) {
				throw new IllegalStateException("Unable to load usage file " + USAGE_FILENAME);
			}

			String line;
			while ((line = reader.readLine()) != null) {
				String[] parts = line.split("\t");
				if (parts.length < 4) {
					continue; // skip malformed lines
				}

				String sctid = parts[0].trim();
				String usageStr = parts[1].trim();
				String usageDecile = parts[2].trim();
				String thisYearDecile = parts[3].trim();

				usageMap.put(sctid, new Usage(usageStr, usageDecile, thisYearDecile));
			}
		}
	}
}


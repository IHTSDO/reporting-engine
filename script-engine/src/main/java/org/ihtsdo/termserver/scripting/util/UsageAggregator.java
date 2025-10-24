package org.ihtsdo.termserver.scripting.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.util.regex.*;
import java.util.*;

public class UsageAggregator {

	private static final Logger LOGGER = LoggerFactory.getLogger(UsageAggregator.class);

	private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})-(\\d{2})");
	private static final String OUTPUT_HEADER =
			"SCTID\tTotalUsage\tUsageDecile\tThisYearDecile";
	private static final String OUTPUT_FILENAME = "aggregated_usage_with_decile.csv";

	public static void main(String[] args) throws IOException {
		if (args.length < 1) {
			LOGGER.error("Usage: java SnomedUsageAggregator <input_directory>");
			System.exit(1);
		}

		File dir = new File(args[0]);
		if (!dir.isDirectory()) {
			throw new IllegalArgumentException("Input path must be a directory.");
		}

		Map<String, Long> totalUsage = new HashMap<>();
		Map<String, Long> latestYearUsage = new HashMap<>();

		List<Path> files;
		try (var stream = Files.list(dir.toPath())) {
			files = stream
					.filter(path -> path.getFileName().toString().endsWith(".txt"))
					.toList();
		}

		Path latestFile = determineLatestFile(files);

		for (Path file : files) {
			boolean isLatest = file.equals(latestFile);
			processFile(file.toFile(), totalUsage, latestYearUsage, isLatest);
		}

		List<Map.Entry<String, Long>> sortedTotal = sortByValueDescending(totalUsage);
		List<Map.Entry<String, Long>> sortedLatest = sortByValueDescending(latestYearUsage);

		Map<String, Integer> totalDeciles = assignDeciles(sortedTotal);
		Map<String, Integer> latestDeciles = assignDeciles(sortedLatest);

		writeOutput(sortedTotal, totalDeciles, latestDeciles);
	}

	private static Path determineLatestFile(List<Path> files) {
		return files.stream()
				.max(Comparator.comparingInt(f -> extractYear(f.getFileName().toString())))
				.orElseThrow(() -> new IllegalStateException("No usage files found."));
	}

	private static int extractYear(String filename) {
		Matcher matcher = YEAR_PATTERN.matcher(filename);
		if (matcher.find()) {
			return Integer.parseInt(matcher.group(1));
		}
		return 0;
	}

	private static void processFile(File file,
	                                Map<String, Long> totalUsage,
	                                Map<String, Long> latestYearUsage,
	                                boolean isLatest) throws IOException {

		LOGGER.info("Processing file: {}", file.getName());
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			if (!reader.readLine().startsWith("SCTID")) {
				throw new IllegalArgumentException("Invalid usage file: " + file.getAbsolutePath() + " expected header SCTID in first column.");
			}
			String line;
			while ((line = reader.readLine()) != null) {
				String[] parts = line.split("\t");
				String conceptId = parts[0].trim();
				if (parts.length < 3 || parts[2].equals("*")) {
					continue;
				}
				long usage = Long.parseLong(parts[2].trim());
				totalUsage.merge(conceptId, usage, Long::sum);

				if (isLatest) {
					latestYearUsage.merge(conceptId, usage, Long::sum);
				}
			}
		}
	}

	private static List<Map.Entry<String, Long>> sortByValueDescending(Map<String, Long> map) {
		return map.entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
				.toList();
	}

	private static Map<String, Integer> assignDeciles(List<Map.Entry<String, Long>> sorted) {
		Map<String, Integer> deciles = new HashMap<>();
		int total = sorted.size();

		for (int i = 0; i < total; i++) {
			String id = sorted.get(i).getKey();
			double percentile = (double) i / total;
			int decile = computeDecile(percentile);
			deciles.put(id, decile);
		}

		return deciles;
	}

	private static int computeDecile(double percentileRank) {
		double inverse = 1.0 - percentileRank;
		int decile = (int) Math.ceil(inverse * 10.0);
		return Math.max(1, Math.min(decile, 10));
	}

	private static void writeOutput(List<Map.Entry<String, Long>> sortedTotal,
	                                Map<String, Integer> totalDeciles,
	                                Map<String, Integer> latestDeciles) throws IOException {

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_FILENAME))) {
			writer.write(OUTPUT_HEADER);
			writer.newLine();

			for (Map.Entry<String, Long> entry : sortedTotal) {
				String id = entry.getKey();
				long usage = entry.getValue();
				int decileTotal = totalDeciles.get(id);
				int decileLatest = latestDeciles.getOrDefault(id, 1);

				writer.write(id + "\t" + formatUsage(usage) + "\t" + decileTotal + "\t" + decileLatest);
				writer.newLine();
			}
		}
	}

	public static String formatUsage(long value) {
		if (value < 1_000L) {
			return Long.toString(value);
		} else if (value < 1_000_000L) {
			return formatDecimal(value / 1_000.0) + "K";
		} else if (value < 1_000_000_000L) {
			return formatDecimal(value / 1_000_000.0) + "M";
		} else {
			return formatDecimal(value / 1_000_000_000.0) + "B";
		}
	}

	private static String formatDecimal(double v) {
		// Two significant figures, no scientific notation
		DecimalFormat df = new DecimalFormat("0.#");
		return df.format(v);
	}


}

package org.ihtsdo.termserver.scripting.util;

import it.unimi.dsi.fastutil.Pair;
import org.ihtsdo.otf.exception.ScriptException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.resourcemanager.ManualResourceConfiguration;
import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.springframework.core.io.ResourceLoader;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/*
 * PIP-256 Standardise various resource paths on S3.
 *
 * This is an idempotent report and simply lists what can be changed.
 * */
public class StandardiseFileNames extends TermServerReport {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(StandardiseFileNames.class);
	private static final String BUCKET_NAME = "snomed-releases";
	
	private static final String STANDARD_HEADER = "CodeSystem, Before, After, Action, S3, MySQL";
	private static final String ISSUE_HEADER = "Environment, Issue, Message";
	private static final String RELEASES = "releases";
	private static final String SNOMED_CT_UNDRSCR = "snomed_ct_";
	private static final boolean INCLUDE_SIMPLEX = false;

	enum Type {
		MANIFEST,
		BUILD
	}
	
	private final ResourceManager resourceManagerDev;
	private final ResourceManager resourceManagerUAT;
	private final ResourceManager resourceManagerProd;

	public StandardiseFileNames() throws TermServerScriptException {
		this.resourceManagerDev = resourceManager("dev");
		this.resourceManagerUAT = resourceManager("uat");
		this.resourceManagerProd = resourceManager("prod");
	}

	public static void main(String[] args) throws ScriptException, IOException {
		StandardiseFileNames curator = new StandardiseFileNames();
		try {
			ReportSheetManager.targetFolderId = "13XiH3KVll3v0vipVxKwWjjf-wmjzgdDe"; //Technical Specialist
			curator.init(args);
			curator.postInit(
					new String[]{
							"Dev",
							"UAT",
							"Prod",
							"Issues"
					},
					new String[]{
							STANDARD_HEADER,
							STANDARD_HEADER,
							STANDARD_HEADER,
							ISSUE_HEADER
					},
					false);
			curator.process();
		} finally {
			curator.finish();
		}
	}

	private void process() throws IOException, TermServerScriptException {
		for (Type type : List.of(Type.MANIFEST, Type.BUILD)) {
			for (String env : List.of("dev", "uat", "prod")) {
				process(env, type);
			}
		}
	}

	private void process(String env, Type type) throws IOException, TermServerScriptException {
		Set<String> resourcePaths;
		List<Pair<String, String>> beforeAfter;

		LOGGER.info("Processing {} {} files", env, type);
		resourcePaths = type == Type.MANIFEST ? getManifestResourcePaths(env) : getBuildResourcePaths(env);
		beforeAfter = type == Type.MANIFEST ? renameManifestResourcePaths(resourcePaths) : renameBuildResourcePaths(resourcePaths);
		reportBeforeAfter(env, beforeAfter, Type.BUILD == type, Type.BUILD == type);
		LOGGER.info("Finished processing {} {} files", env, type);
	}

	private void reportIssues(String environment, List<Pair<String, String>> manifestFilesBeforeAfter) throws TermServerScriptException {
		Map<String, List<String>> renameConflicts = new HashMap<>();
		for (Pair<String, String> beforeAfter : manifestFilesBeforeAfter) {
			String key = beforeAfter.second();
			List<String> value = renameConflicts.get(key);
			if (value == null) {
				value = new ArrayList<>();
			}

			value.add(key);
			renameConflicts.put(key, value);
		}

		boolean clean = true;
		for (Map.Entry<String, List<String>> entrySet : renameConflicts.entrySet()) {
			List<String> value = entrySet.getValue();
			if (value.size() > 1) {
				clean = false;
				report(3, environment, "Conflict", "Multiple destinations with value " + value.iterator().next());
			}
		}

		if (clean) {
			report(3, environment, "Conflict", "No conflicts");
		}
	}

	private ResourceManager resourceManager(String environment) throws TermServerScriptException {
		ResourceConfiguration.Cloud cloud = new ResourceConfiguration.Cloud();
		cloud.setBucketName(BUCKET_NAME);
		cloud.setPath(environment);
		ResourceLoader resourceLoader = getArchiveManager().getS3Manager().getResourceLoader();

		return new ResourceManager(new ManualResourceConfiguration(true, true, null, cloud), resourceLoader);
	}

	private ResourceManager getResourceManagerByEnvironment(String environment) {
		if ("dev".equals(environment)) {
			return resourceManagerDev;
		} else if ("uat".equals(environment)) {
			return resourceManagerUAT;
		} else if ("prod".equals(environment)) {
			return resourceManagerProd;
		} else {
			throw new IllegalArgumentException("Unrecognised environment: " + environment);
		}
	}

	private int getTabByEnvironment(String environment) throws TermServerScriptException{
		if ("dev".equals(environment)) {
			return 0;
		} else if ("uat".equals(environment)) {
			return 1;
		} else if ("prod".equals(environment)) {
			return 2;
		} else {
			throw new TermServerScriptException("Unknown environment: " + environment);
		}
	}

	private Set<String> getManifestResourcePaths(String environment) throws IOException {
		return getResourceManagerByEnvironment(environment)
				.listFilenames("manifest-files")
				.stream()
				.map(mf -> {
					String[] split = mf.split("/");

					if (split.length < 3) {
						return null;
					} else {
						return mf;
					}
				})
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
	}

	private Set<String> getBuildResourcePaths(String environment) throws IOException {
		return getResourceManagerByEnvironment(environment)
				.listFilenames("builds")
				.stream()
				.map(build -> {
					String[] split = build.split("/");
					return split[0] + "/" + split[1] + "/" + split[2];
				}).collect(Collectors.toSet());
	}

	private void reportBeforeAfter(String environment, List<Pair<String, String>> beforeAfters, boolean includeMySQL, boolean isFolder) throws TermServerScriptException {
		for (Pair<String, String> beforeAfter : beforeAfters) {
			String first = beforeAfter.first();
			String second = beforeAfter.second();
			String action = first.equals(second) ? "NONE" : "UPDATE";
			String codeSystem = beforeAfter.first().split("/")[1];

			String s3Command = "";
			String mySQLCommand = "";
			if ("UPDATE".equals(action)) {
				s3Command = getS3Command(environment, first, second, isFolder);
				if (includeMySQL) {
					String f = first.split("/")[2];
					String s = second.split("/")[2];
					mySQLCommand = getMySQLCommand(f, s);
				}
			}

			report(getTabByEnvironment(environment), codeSystem, first, second, action, s3Command, mySQLCommand);
		}

		reportIssues(environment, beforeAfters);
	}

	private List<Pair<String, String>> renameManifestResourcePaths(Set<String> manifestResourcePaths) {
		if (manifestResourcePaths.isEmpty()) {
			return Collections.emptyList();
		}

		List<Pair<String, String>> beforeAfter = new ArrayList<>();
		for (String before : manifestResourcePaths) {
			if (!INCLUDE_SIMPLEX && before.contains("simplex")) {
				continue;
			}

			// Replace common terms, i.e. country codes
			String after = replaceCommonTerms(before);

			// Starts as expected
			String[] split = after.split("/");
			if (!split[2].startsWith(SNOMED_CT_UNDRSCR)) {
				after = split[0] + "/" + split[1] + "/" + SNOMED_CT_UNDRSCR + split[2] + "/" + split[3];
			}

			// Ends as expected
			split = after.split("/");
			if (!split[2].endsWith("_daily_build") && !split[2].contains("_" + RELEASES)) {
				after = split[0] + "/" + split[1] + "/" + split[2] + "/" + split[3] + "_" + RELEASES;
			}

			beforeAfter.add(Pair.of(before, after));
		}

		beforeAfter.sort(Comparator.comparing(Pair::first));
		return beforeAfter;
	}

	private String replaceCommonTerms(String before) {
		String after = before;

		// Country codes
		after = after.replace("australia", "au");
		after = after.replace("austria", "at");
		after = after.replace("belgium", "be");
		after = after.replace("denmark", "dk");
		after = after.replace("estonia", "ee");
		after = after.replace("french", "fr");
		after = after.replace("france", "fr");
		after = after.replace("ireland", "ie");
		after = after.replace("korea", "kr");
		after = after.replace("netherlands", "nl");
		after = after.replace("new_zealand", "nz");
		after = after.replace("norway", "no");
		after = after.replace("spanish", "es");
		after = after.replace("spain", "es");
		after = after.replace("sweden", "se");
		after = after.replace("switzerland", "ch");

		// Simplex
		after = after.replace("snomedctjm", "snomed_ct_jm_releases");
		after = after.replace("snomedctland", "snomed_ct_land_releases");
		after = after.replace("snomedctpt", "snomed_ct_pt_releases");
		after = after.replace("snomedct", SNOMED_CT_UNDRSCR);

		// Other
		after = after.replace("release", RELEASES);
		after = after.replace("releasess", RELEASES);

		return after;
	}

	private List<Pair<String, String>> renameBuildResourcePaths(Set<String> buildResourcePaths) {
		if (buildResourcePaths.isEmpty()) {
			return Collections.emptyList();
		}

		List<Pair<String, String>> beforeAfter = new ArrayList<>();
		for (String before : buildResourcePaths) {
			if (!INCLUDE_SIMPLEX && before.contains("simplex")) {
				continue;
			}

			// Replace common terms, i.e. country codes
			String after = replaceCommonTerms(before);

			// Starts as expected
			String[] split = after.split("/");
			if (!split[2].startsWith(SNOMED_CT_UNDRSCR)) {
				after = split[0] + "/" + split[1] + "/" + SNOMED_CT_UNDRSCR + split[2];
			}

			// Ends as expected
			split = after.split("/");
			if (!split[2].endsWith("_daily_build") && !split[2].contains("_" + RELEASES)) {
				after = split[0] + "/" + split[1] + "/" + split[2] + "_" + RELEASES;
			}

			beforeAfter.add(Pair.of(before, after));
		}

		beforeAfter.sort(Comparator.comparing(Pair::first));
		return beforeAfter;
	}

	private String getS3Command(String envName, String before, String after, boolean isFolder) {
		if (isFolder) {
			return String.format("aws s3 --recursive mv \"s3://%s/%s/%s/\" \"s3://%s/%s/%s/\"", BUCKET_NAME, envName, before, BUCKET_NAME, envName, after);
		} else {
			// Recursive and trailing slash not needed
			return String.format("aws s3 mv \"s3://%s/%s/%s\" \"s3://%s/%s/%s\"", BUCKET_NAME, envName, before, BUCKET_NAME, envName, after);
		}
	}

	private String getMySQLCommand(String before, String after) {
		return String.format("UPDATE product SET business_key = '%s' WHERE business_key = '%s';", after, before);
	}
}
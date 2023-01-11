package org.ihtsdo.termserver.scripting.reports.gmdn.runner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.ihtsdo.termserver.scripting.reports.gmdn.generator.GmdnContentDeltaGenerator;
import org.ihtsdo.termserver.scripting.reports.gmdn.generator.GmdnMonthlyUpdateReportParser;
import org.ihtsdo.termserver.scripting.reports.gmdn.utils.GmdnSFTPClient;
import org.ihtsdo.termserver.scripting.reports.gmdn.utils.GMDNZipFileUtils;

public class GmdnMonthlyContentReportRunner {

	enum RunMode {
		FULL("Full"),
		UPDATE_ONLY("UpdateOnly"),
		DIFF("Diff");
		final private String name;

		private RunMode(String name) {
			this.name = name;
		}
		
		public String getName() {
			return this.name();
		}
	}
	public static void main(String[] args) throws Exception {
		// run mode full, GMDN update report only, monthly report by comparing this month with last month
		if (args == null || args.length != 2) {
			System.out.println("Usage:" + "{runMode} {config_file_path} {skip_download");
			System.out.println("Full" + " is to run the tool to generate the monthly report using last two month's data. The most recent gmdnDataUpdate file is also downloaed.");
			System.out.println("UpdateOnly" + " is to download the GMDN update only");
			System.out.println("Diff" + " is to run diff report with the last two month's data only.");
			System.out.println("skip_download is optional when files exist locally already and the default value is false");
		} else {
			String runMode = args[0];
			Properties configs = loadConfig(args[1]);
			boolean skipDownLoad = false;
			if (args.length == 3 && Boolean.getBoolean(args[2])) {
				skipDownLoad = true;
			}
			String dataFileName = configs.getProperty("latestDataFileName");
			String updateReporName = configs.getProperty("gmdnDataUpateFileName");
			if (!skipDownLoad) {
				downloadGmdnFiles(configs, dataFileName, updateReporName);
			}
			
			String localDataDir = configs.getProperty("localDirectory");
			String monthlyUpdateFile = localDataDir + "/" + "";
			String previousDataFileName = configs.getProperty("lastMonthDataFile");
			String currentDataFileName = localDataDir + "/" + configs.getProperty("");
			//unzip downloaded files
			unzip(localDataDir, dataFileName, updateReporName);
			if (runMode.equals(RunMode.FULL.getName())) {
				runGMDNUpdateReport(monthlyUpdateFile);
				runGmdnDiffReport(previousDataFileName, currentDataFileName);
			} else if (runMode.equals(RunMode.UPDATE_ONLY.getName())) {
				runGMDNUpdateReport(monthlyUpdateFile);
			} else if (runMode.equals(RunMode.DIFF.getName())) {
				runGmdnDiffReport(previousDataFileName, currentDataFileName);
			} else {
				System.out.println("Invalid mode:" + runMode);
			}
		}
		
	}
	
	private static void unzip(String localDataDir, String ... fileToUnzip) throws IOException {
		for (String file : fileToUnzip) {
			GMDNZipFileUtils.extractZipFile(new File(file), localDataDir);
		}
	}

	private static Properties loadConfig(String configFilePath) throws FileNotFoundException, IOException {
		Properties configs = new Properties();
		configs.load(new FileInputStream(new File(configFilePath)));
		return configs;
	}

	private static void downloadGmdnFiles(Properties configs, String ... filenames) throws FileNotFoundException, IOException {
		GmdnSFTPClient client = new GmdnSFTPClient(configs);
		client.downloadFile(filenames);
	}
	private static void runGmdnDiffReport(String previousDataFileName, String currentDataFileName) throws Exception {
		GmdnContentDeltaGenerator generator = new GmdnContentDeltaGenerator();
		generator.generateDeltaReport(previousDataFileName, currentDataFileName);
		
	}
	private static void runGMDNUpdateReport(String monthlyUpdateFile) {
		GmdnMonthlyUpdateReportParser parser = new GmdnMonthlyUpdateReportParser();
		parser.parseGmdnUpdateReport(monthlyUpdateFile);
		
	}

}

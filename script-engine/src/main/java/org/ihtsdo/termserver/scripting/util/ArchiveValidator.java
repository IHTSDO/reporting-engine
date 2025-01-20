package org.ihtsdo.termserver.scripting.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.snomed.otf.scheduler.domain.JobRun;
import org.snomed.otf.script.Script;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveValidator extends Script {

	private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveValidator.class);

	public static void main(String[] args) throws IOException, TermServerScriptException {
		if (args.length == 0) {
			LOGGER.info("Usage:  ArchiveValidator <archive location");
			System.exit(1);
		}
		
		validateArchiveZip(new File(args[0]));
	}
	
	private static void validateArchiveZip(File archive) throws IOException, TermServerScriptException {
		ZipInputStream zis = new ZipInputStream(new FileInputStream(archive));
		//ZipInputStream zis = new ZipInputStream(new FileInputStream(archive), Charset.forName("CP437"));
		//ZipInputStream zis = new ZipInputStream(new FileInputStream(archive), Charset.forName("windows-1252"));
		//ZipInputStream zis = new ZipInputStream(new FileInputStream(archive), Charset.forName("ISO-8859-1"));
		//ZipInputStream zis = new ZipInputStream(new FileInputStream(archive), Charset.forName("UTF-16BE"));
		//ZipInputStream zis = new ZipInputStream(new FileInputStream(archive), Charset.forName("US-ASCII"));
		ZipEntry ze = zis.getNextEntry();
		try {
			while (ze != null) {
				if (!ze.isDirectory()) {
					Path path = Paths.get(ze.getName());
					loadFile(path, zis);
				}
				ze = zis.getNextEntry();
			}
		}  finally {
			try{
				zis.closeEntry();
				zis.close();
			} catch (Exception e){} //Well, we tried.
		}
	}
	
	private static void loadFile(Path path, InputStream is)  {
		try {
			String fileName = path.getFileName().toString();
			LOGGER.info("Checking " + fileName);
			BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
			String line;
			boolean isHeaderLine = true;
			while ((line = br.readLine()) != null) {
				if (!isHeaderLine) {
					String[] lineItems = line.split(FIELD_DELIMITER);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Failed to load " + path, e);
		}
	}

	@Override
	public boolean isOffline() {
		return false;
	}

	@Override
	public JobRun getJobRun() {
		return null;
	}

	@Override
	public String getReportName() {
		return null;
	}

	@Override
	public String detectReleaseBranch() {
		return null;
	}

	@Override
	public String getEnv() {
		return null;
	}

}

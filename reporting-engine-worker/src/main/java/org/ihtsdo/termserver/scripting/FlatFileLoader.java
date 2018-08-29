package org.ihtsdo.termserver.scripting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;

public class FlatFileLoader implements RF2Constants {
	
	Map<String, String[]> componentMap = new HashMap<>();
	
	public static void println (String msg) {
		System.out.println (msg);
	}
	
	public String[] get(String id) {
		return componentMap.get(id);
	}

	public void loadArchive(File archive) throws TermServerScriptException, SnowOwlClientException {
		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(archive));
			ZipEntry ze = zis.getNextEntry();
			try {
				while (ze != null) {
					if (!ze.isDirectory()) {
						Path p = Paths.get(ze.getName());
						String fileName = p.getFileName().toString();
						println("Loading " + fileName);
						loadFile(zis);
					}
					ze = zis.getNextEntry();
				}
			} finally {
				try{
					zis.closeEntry();
					zis.close();
				} catch (Exception e){} //Well, we tried.
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to import " + archive.getName(), e);
		}
	}
	
	public void loadFile(ZipInputStream zis) throws IOException, TermServerScriptException, SnowOwlClientException {
		BufferedReader br = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				String id = lineItems[IDX_ID];
				componentMap.put(id,  lineItems);
			} else {
				isHeaderLine = false;
			}
		}
	}
	
}

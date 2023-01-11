package org.ihtsdo.termserver.scripting.reports.gmdn.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;

public class GMDNZipFileUtils {
	
public static void extractZipFile(final File file, final String outputDir) throws IOException {
		
		try (ZipFile zipFile = new ZipFile(file)) {
			final Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				final ZipEntry entry = entries.nextElement();
				final File entryDestination = new File(outputDir,  entry.getName());
				entryDestination.getParentFile().mkdirs();
				if (entry.isDirectory()) {
					entryDestination.mkdirs();
				} else {
					InputStream in = null;
					Writer writer = null;
					try {
						in = zipFile.getInputStream(entry);
						writer = new FileWriter(entryDestination);
						IOUtils.copy(in, writer, "UTF-8");
						} finally {
							IOUtils.closeQuietly(in);
							IOUtils.closeQuietly(writer);
						}
				}
			}
		}
	}
}

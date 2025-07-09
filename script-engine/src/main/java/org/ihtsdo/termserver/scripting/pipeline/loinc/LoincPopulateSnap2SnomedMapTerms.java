package org.ihtsdo.termserver.scripting.pipeline.loinc;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.Part;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;

public class LoincPopulateSnap2SnomedMapTerms extends LoincScript {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoincPopulateSnap2SnomedMapTerms.class);

	public static void main(String[] args) throws TermServerScriptException, IOException {
		LoincPopulateSnap2SnomedMapTerms report = new LoincPopulateSnap2SnomedMapTerms();
		try {
			report.runStandAlone = false;
			report.getGraphLoader().setExcludedModules(new HashSet<>());
			report.getArchiveManager().setRunIntegrityChecks(false);
			report.init(args);
			report.loadProjectSnapshot(false);
			report.runReport();
		} finally {
			report.finish();
		}
	}

	private void runReport() throws TermServerScriptException, IOException {
		loadLoincParts();
		try (BufferedReader reader = Files.newBufferedReader(getInputFile().toPath());
		     BufferedWriter writer = Files.newBufferedWriter(Paths.get("amalgamatqed_map_mkii.tsv"))) {

			String line;
			boolean isFirstLine = true;
			while ((line = reader.readLine()) != null) {
				String[] columns = line.split("\t", -1); // -1 keeps trailing empty strings

				if (columns.length < 6) {
					LOGGER.error("Skipping malformed line: {}", line);
					continue;
				}

				if (!isFirstLine) {
					// Lookup for column 1 → result into column 2
					String key1 = columns[0];
					Part part = partMap.get(key1);
					if (part == null) {
						LOGGER.error("Part not found for {}", key1);
						columns[1] = "TBC";
					} else {
						columns[1] = part.getPartName();
					}

					// Lookup for column 5 → result into column 6
					String key5 = columns[2];
					String value5 = gl.getConcept(key5).getPreferredSynonym();
					columns[3] = value5;
				} else {
					isFirstLine = false;
				}

				// Join and write updated line
				String updatedLine = String.join("\t", columns);
				writer.write(updatedLine);
				writer.newLine();
			}
		}
	}

	@Override
	protected void importPartMap() throws TermServerScriptException {

	}

	@Override
	public TemplatedConcept getAppropriateTemplate(ExternalConcept externalConcept) throws TermServerScriptException {
		return null;
	}
}

package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExportType;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExtractType;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * Reports all concepts that have been defined (stated) using one or more 
 * Fully Defined Parents
 */
public class ValidateLateralityReport extends TermServerScript{
	
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	GraphLoader gl = GraphLoader.getGraphLoader();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		ValidateLateralityReport report = new ValidateLateralityReport();
		try {
			report.init(args);
			report.loadProjectSnapshot(true);  //Load FSNs only
			List<Component> lateralizable = report.processFile();
			report.validateLaterality(lateralizable);
		} catch (Exception e) {
			info("Failed to validate laterality due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void validateLaterality(List<Component> lateralizable) throws TermServerScriptException {
		//For all concepts, if it is lateralized, check that concept is listed in our
		//set of lateralizable concepts.
		Concept laterality = gl.getConcept("272741003");
		Concept side = gl.getConcept("182353008");
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive()) {
				List<Relationship> lateralized = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP,  laterality, ActiveState.ACTIVE);
				if (lateralized.size() > 0 && lateralized.get(0).getTarget().equals(side)) {
					if (!lateralizable.contains(c)) {
						report (c);
					}
				}
			}
		}
	}

	protected void report (Concept c) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA + 
						c.getEffectiveTime() + COMMA_QUOTE +
						c.getDefinitionStatus() + QUOTE;
		writeToReportFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException, SnowOwlClientException {
		super.init(args);
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		//String reportFilename = "changed_relationships_" + project.getKey().toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		String reportFilename = getScriptName() + "_" + project.getKey().toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		info ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToReportFile ("Concept, FSN, EffectiveTime, Definition_Status,SemanticTag");
	}

	@Override
	protected List<Concept> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return Collections.singletonList(gl.getConcept(lineItems[0]));
	}
}

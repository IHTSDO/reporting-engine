package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
public class HierarchyConceptsNotInFileReport extends TermServerScript{
	
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	GraphLoader gl = GraphLoader.getGraphLoader();
	String publishedArchive;
	String file2;
	String file2Purpose = "UsedToDefineConcepts";
	String hierarchy = "49062001"; // |Device (physical object)|
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		HierarchyConceptsNotInFileReport report = new HierarchyConceptsNotInFileReport();
		try {
			report.init(args);
			report.loadProjectSnapshot(true);  //Load FSNs only
			List<Component> conceptsInFile = report.processFile();
			List<Component> conceptsInFile2 = new ArrayList<Component>();
			if (report.file2 != null) {
				conceptsInFile2 = report.processFile(new File(report.file2));
			}
			report.inHierarchyAndNotFile(conceptsInFile, conceptsInFile2);
		} catch (Exception e) {
			info("Failed to validate laterality due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void inHierarchyAndNotFile(List<Component> conceptsInFile, List<Component> conceptsInFile2) throws TermServerScriptException {
		//For all active concepts in the report hierarchy, report if the concept is not
		//also in the supplied file
		Concept sourceHierarchy = gl.getConcept(hierarchy);
		Set<Concept> sourceConcepts = filterActive(sourceHierarchy.getDescendents(NOT_SET));
		info ("Active source concepts number " + sourceConcepts.size());
		
		for (Concept c : sourceConcepts) {
			if (!conceptsInFile.contains(c)) {
				report (c, conceptsInFile2.contains(c));
			}
		}
	}
	

	private Set<Concept> filterActive(Set<Concept> fullSet) {
		Set <Concept> activeConcepts = new HashSet<Concept>();
		for (Concept thisConcept : fullSet ) {
			if (thisConcept.isActive()) {
				activeConcepts.add(thisConcept);
			}
		}
		return activeConcepts;
	}


	protected void report (Concept c, boolean inFile2) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA + 
						c.getEffectiveTime() + COMMA_QUOTE +
						c.getDefinitionStatus() + QUOTE_COMMA +
						(inFile2 ? "YES":"NO") ;
		writeToReportFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException, SnowOwlClientException {
		super.init(args);
		
		for (int x=0; x<args.length; x++) {
			if (args[x].equals("-z")) {
				publishedArchive = args[++x];
			}
			
			if (args[x].equals("-f2")) {
				file2 = args[++x];
			}
		}
		
		print ("Concepts in which Hierarchy? [" + hierarchy + "]: ");
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			hierarchy = response;
		}
		
		print ("What header for existing in 2nd file? [" + file2Purpose + "]: ");
		response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			file2Purpose = response;
		}
		
	}

	@Override
	protected List<Concept> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return Collections.singletonList(gl.getConcept(lineItems[0]));
	}
}

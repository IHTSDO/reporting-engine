package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * Reports all concepts that have been defined (stated) using one or more 
 * Fully Defined Parents
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HierarchyConceptsNotInFileReport extends TermServerScript{

	private static final Logger LOGGER = LoggerFactory.getLogger(HierarchyConceptsNotInFileReport.class);

	String publishedArchive;
	String file2;
	String file2Purpose = "UsedToDefineConcepts";
	String hierarchy = "49062001"; // |Device (physical object)|
	
	public static void main(String[] args) throws TermServerScriptException {
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
			LOGGER.error("Failed to produce report", e);
		} finally {
			report.finish();
		}
	}

	private void inHierarchyAndNotFile(List<Component> conceptsInFile, List<Component> conceptsInFile2) throws TermServerScriptException {
		//For all active concepts in the report hierarchy, report if the concept is not
		//also in the supplied file
		Concept sourceHierarchy = gl.getConcept(hierarchy);
		Set<Concept> sourceConcepts = filterActive(sourceHierarchy.getDescendants(NOT_SET));
		LOGGER.info("Active source concepts number " + sourceConcepts.size());
		
		for (Concept c : sourceConcepts) {
			if (!conceptsInFile.contains(c)) {
				report(c, conceptsInFile2.contains(c));
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


	protected void report(Concept c, boolean inFile2) throws TermServerScriptException {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA + 
						c.getEffectiveTime() + COMMA_QUOTE +
						c.getDefinitionStatus() + QUOTE_COMMA +
						(inFile2 ? "YES":"NO") ;
		writeToReportFile(line);
	}
	
	protected void init(String[] args) throws TermServerScriptException {
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
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return Collections.singletonList(gl.getConcept(lineItems[0]));
	}
}

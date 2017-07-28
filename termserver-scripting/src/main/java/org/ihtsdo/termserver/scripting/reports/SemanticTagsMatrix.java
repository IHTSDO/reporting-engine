package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Generates a 2D matrix of how many semantic tags are used in each top level hierarchy
 * Note use of int array rather than MultiSet because it initialises to 0 so we have an column 
 * entry whether or not a semantic tag gets used in a hierarchy.
 */
public class SemanticTagsMatrix extends TermServerReport{
	
	List<Concept> topLevelHierarchies;
	Map<String, int[]> tagToUsageMap;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		SemanticTagsMatrix report = new SemanticTagsMatrix();
		try {
			report.init(args);
			report.loadProjectSnapshot(true);  //Load FSNs only
			report.getTopLevelHierarchies();
			report.listSemanticTags();
			report.outputResultsXY();
			report.initialiseAlternativeFile();
			report.outputResultsYX();
			report.outputWithoutCounts();
		} catch (Exception e) {
			println("Failed to validate laterality due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void getTopLevelHierarchies() throws TermServerScriptException {
		Concept rootConcept = gl.getConcept(SCTID_ROOT_CONCEPT.toString());
		topLevelHierarchies = new ArrayList<Concept>(rootConcept.getDescendents(IMMEDIATE_CHILD));
		tagToUsageMap = new TreeMap<String, int[]>();
	}

	private void listSemanticTags() throws TermServerScriptException {
		//Work through all top level hierarchies and list semantic tags along with their counts
		for (int h=0; h < topLevelHierarchies.size(); h++) {
			Set<Concept> descendents = topLevelHierarchies.get(h).getDescendents(NOT_SET);
			println (topLevelHierarchies.get(h) + " - total: " + descendents.size());
			String topTag = SnomedUtils.deconstructFSN(topLevelHierarchies.get(h).getFsn())[1];
			for (Concept c : descendents) {
				String tag = SnomedUtils.deconstructFSN(c.getFsn())[1];
				checkForAnomoly(topTag, tag, c);
				//Have we seen this tag before?
				int[] usage = tagToUsageMap.get(tag);
				if (usage == null) {
					usage = new int[topLevelHierarchies.size()];
					tagToUsageMap.put(tag, usage);
				}
				usage[h] += 1;
			}
		}
	}
	
	private void checkForAnomoly(String topTag, String thisTag, Concept c) {
		//Some hierarchies only expect to use a single semantic tag
		if (topTag.equals("(product)") && thisTag.equals("(substance)")) {
			println ("Anomaly found in " + topTag + " hierarchy: " + c);
		}
		
		if (topTag.equals("(substance)") && thisTag.equals("(product)")) {
			println ("Anomaly found in " + topTag + " hierarchy: " + c);
		}
	}

	//Set up arrays for each top level hierarchy to capture semantic tag counts 
	private void outputResultsXY() {
		StringBuffer headerRow = new StringBuffer("SemanticTag");
		for (Concept c : topLevelHierarchies) {
			headerRow.append(COMMA)
				.append(SnomedUtils.deconstructFSN(c.getFsn())[0]);
		}
		writeToFile(headerRow.toString());
		//Each row is a semantic tag
		for (String tag : tagToUsageMap.keySet()) {
			String row = Arrays.toString(tagToUsageMap.get(tag));
			row = tag + "," + row.substring(1, row.length() -1);  //Take off the square brackets
			writeToFile(row);
		}
	}
	
	private void initialiseAlternativeFile() throws IOException {
		//We need a 2nd output file to put the results with the dimensions reversed
		String reportFilename = getScriptName() + "_" + project.toLowerCase() + "_" + currentTimeStamp + "_reversed_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		
		StringBuffer headerRow = new StringBuffer("Hierarchy");
		for (String tag : tagToUsageMap.keySet()) {
			headerRow.append(COMMA)
				.append(tag);
		}
		writeToFile (headerRow.toString());
	}
	
	private void outputResultsYX() throws IOException {
		//Each row in the file is going to be a hierarchy
		for (int h=0; h < topLevelHierarchies.size(); h++) {
			String hierarchy = SnomedUtils.deconstructFSN(topLevelHierarchies.get(h).getFsn())[0];
			StringBuffer row = new StringBuffer(hierarchy);
			for (String tag : tagToUsageMap.keySet()) {
				row.append(COMMA);
				int[] usage = tagToUsageMap.get(tag);
				row.append(usage[h]);
			}
			writeToFile(row.toString());
		}
	}

	private void outputWithoutCounts() {
		//Each row is a semantic tag
		for (String tag : tagToUsageMap.keySet()) {
			StringBuffer row = new StringBuffer(tag).append(":");
			int[] usage = tagToUsageMap.get(tag);
			int hierarchiesFeatured = 0;
			for (int i=0; i<usage.length; i++) {
				if (usage[i] > 0) {
					row.append(hierarchiesFeatured > 0 ? ", ":" ");
					row.append(SnomedUtils.deconstructFSN(topLevelHierarchies.get(i).getFsn())[0]);
					hierarchiesFeatured++;
				}
			}
			println (row.toString());
		}
		
	}
	
}

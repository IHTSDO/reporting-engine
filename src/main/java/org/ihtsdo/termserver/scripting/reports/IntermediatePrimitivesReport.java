package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Reports concepts that are primitive, and have both fully defined ancestors and descendants 
 * */
public class IntermediatePrimitivesReport extends TermServerScript{
	
	List<Concept> topLevelHierarchies;
	CharacteristicType targetCharType = CharacteristicType.STATED_RELATIONSHIP;
	//CharacteristicType targetCharType = CharacteristicType.INFERRED_RELATIONSHIP;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		IntermediatePrimitivesReport report = new IntermediatePrimitivesReport();
		try {
			report.init(args);
			report.loadProjectSnapshot(true);  //just FSNs
			report.getTopLevelHierarchies();
			report.reportIntermediatePrimitives();
		} catch (Exception e) {
			println("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}
	
	private void getTopLevelHierarchies() throws TermServerScriptException {
		Concept rootConcept = gl.getConcept(SCTID_ROOT_CONCEPT.toString());
		topLevelHierarchies = new ArrayList<Concept>(rootConcept.getDescendents(IMMEDIATE_CHILD));
		//Sort by FSN
		Collections.sort(topLevelHierarchies, new Comparator<Concept>() {
			@Override
			public int compare(Concept c1, Concept c2) {
				return c1.getFsn().compareTo(c2.getFsn());
			}
		});
	}

	private void reportIntermediatePrimitives() throws TermServerScriptException {
		int rowsReported = 0;
		println ("Scanning all concepts...");
		//Work through all top level hierarchies and list semantic tags along with their counts
		for (Concept thisHierarchy : topLevelHierarchies) {
			int hierarchyIpCount = 0;
			Set<Concept> descendents = thisHierarchy.getDescendents(NOT_SET, targetCharType, ActiveState.ACTIVE);
			for (Concept c : descendents) {
				if (c.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
					hierarchyIpCount += checkConceptForIntermediatePrimitive(c);
				}
			}
			println(simpleName(thisHierarchy.getConceptId()) + ": " + hierarchyIpCount + " / " + descendents.size());
			rowsReported += hierarchyIpCount;
		}
		addSummaryInformation("Concepts checked", gl.getAllConcepts().size());
		addSummaryInformation("Rows reported", rowsReported);
	}

	private int checkConceptForIntermediatePrimitive(Concept c) throws TermServerScriptException {
		//Do we have both ancestor and descendant fully defined concepts?
		Set<Concept> descendants = c.getDescendents(NOT_SET, targetCharType, ActiveState.ACTIVE);
		boolean hasFdDescendants = containsFdConcept(descendants);
		if (hasFdDescendants && containsFdConcept(c.getAncestors(NOT_SET, targetCharType, ActiveState.ACTIVE, false))) {
			//This is an intermediate primitive, but does it have immediately close SD concepts?
			boolean hasImmediateSDParent = containsFdConcept(c.getParents(targetCharType));
			boolean hasImmediateSDChild = containsFdConcept(c.getChildren(targetCharType));
			boolean hasAllParentsSD = hasAllParentsSD(c);
			boolean hasAllSDChildren = hasAllSDChildren(c);
			report (c, hasImmediateSDParent, hasImmediateSDChild, hasAllParentsSD, hasAllSDChildren);
			return 1;
		}
		return 0;
	}

	private boolean hasAllParentsSD(Concept c) {
		List<Concept> parents = c.getParents(targetCharType);
		for (Concept parent : parents) {
			if (parent.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
				return false;
			}
		}
		return true;
	}
	
	private boolean hasAllSDChildren(Concept c) {
		List<Concept> children = c.getChildren(targetCharType);
		for (Concept child : children) {
			if (child.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
				return false;
			}
		}
		return true;
	}

	private boolean containsFdConcept(Collection<Concept> concepts) {
		for (Concept c : concepts) {
			if (c.isActive() && c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				return true;
			}
		}
		return false;
	}

	protected void report (Concept c, boolean hasImmediateSDParent, boolean hasImmediateSDChild, boolean hasAllParentsSD, boolean hasAllSDChildren) throws TermServerScriptException {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA_QUOTE + 
						SnomedUtils.deconstructFSN(c.getFsn())[1] + QUOTE_COMMA_QUOTE + 
						(hasImmediateSDParent?"Yes":"No") + QUOTE_COMMA_QUOTE + 
						(hasImmediateSDChild?"Yes":"No") + QUOTE_COMMA_QUOTE + 
						(hasAllParentsSD?"Yes":"No") + QUOTE_COMMA_QUOTE + 
						(hasAllSDChildren?"Yes":"No") + QUOTE ;
		writeToReportFile(line);
	}
	
	private String simpleName(String sctid) throws TermServerScriptException {
		Concept c = gl.getConcept(sctid);
		return SnomedUtils.deconstructFSN(c.getFsn())[0];
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException, SnowOwlClientException {
		super.init(args);
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String charType = (targetCharType.equals(CharacteristicType.STATED_RELATIONSHIP)?"Stated":"Inferred");
		String reportFilename = getScriptName() + "_" + charType + "_" + project.getKey().toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToReportFile ("Concept, FSN, semanticTag, hasImmediateSDParent, hasImmediateSDChild, hasAllParentsSD, hasAllSDChildren");
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}
}

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
import java.util.TreeMap;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.HistoricalAssociation;
import org.ihtsdo.termserver.scripting.domain.InactivationIndicatorEntry;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Reports concepts that are primitive, and have both fully defined ancestors and descendants 
 * */
public class IntermediatePrimitivesReport extends TermServerScript{
	
	List<Concept> topLevelHierarchies;
	
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
			Set<Concept> descendents = thisHierarchy.getDescendents(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE);
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
		Set<Concept> descendants = c.getDescendents(NOT_SET);
		boolean hasFdDescendants = containsFdConcept(descendants);
		if (hasFdDescendants && containsFdConcept(c.getAncestors(NOT_SET))) {
			//This is an intermediate primitive, but does it have immediately close FD concepts?
			boolean hasImmediateFDParent = containsFdConcept(c.getParents(CharacteristicType.INFERRED_RELATIONSHIP));
			boolean hasImmediateFDChild = containsFdConcept(c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP));
			report (c, hasImmediateFDParent, hasImmediateFDChild);
			return 1;
		}
		return 0;
	}

	private boolean containsFdConcept(Collection<Concept> concepts) {
		for (Concept c : concepts) {
			if (c.isActive() && c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				return true;
			}
		}
		return false;
	}

	protected void report (Concept c, boolean hasImmediateFDParent, boolean hasImmediateFDChild) throws TermServerScriptException {
		
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA_QUOTE + 
						SnomedUtils.deconstructFSN(c.getFsn())[1] + QUOTE_COMMA_QUOTE + 
						(hasImmediateFDParent?"Yes":"No") + QUOTE_COMMA_QUOTE + 
						(hasImmediateFDChild?"Yes":"No") + QUOTE ;
		writeToFile(line);
	}
	
	private String simpleName(String sctid) throws TermServerScriptException {
		Concept c = gl.getConcept(sctid);
		return SnomedUtils.deconstructFSN(c.getFsn())[0];
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException {
		super.init(args);
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String reportFilename = getScriptName() + "_" + project.toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToFile ("Concept, FSN, semanticTag, immediateParentFD, immediateChildFD");
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}
}

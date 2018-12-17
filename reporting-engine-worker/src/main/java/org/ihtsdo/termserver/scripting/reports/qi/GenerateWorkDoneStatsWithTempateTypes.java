package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.*;
import java.util.*;

import org.apache.commons.io.Charsets;
import org.ihtsdo.termserver.scripting.ArchiveManager;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.io.Files;

/**
 * QI
 * Report changes made in this authoring cycle for specific sub-hierarchies.
 */
public class GenerateWorkDoneStatsWithTempateTypes extends TermServerReport {
	
	List<Concept> subHierarchies;
	Map<Concept, List<Concept>> exclusionMap = new HashMap<>();
	InitialAnalysis ipReport;
	int modifiedSince = 20180131;
	
	String [] co_occurrantWords = new String[] { " and ", " with ", " in " };
	Concept[] co_occurrantTypeAttrb;
	Concept[] complexTypeAttrbs;
	
	enum TemplateType {SIMPLE, PURE_CO, COMPLEX, COMPLEX_NO_MORPH, NONE};
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		GenerateWorkDoneStatsWithTempateTypes report = new GenerateWorkDoneStatsWithTempateTypes();
		try {
			ReportSheetManager.targetFolderId = "1YoJa68WLAMPKG6h4_gZ5-QT974EU9ui6"; //QI / Stats
			report.additionalReportColumns = "FSN, SemTag, Depth, Counted elsewhere, Simple, modified, Pure, modified, Complex, modified, ComplexNoMorph, modified, None, modified, Total";
			ArchiveManager.getArchiveManager(report).populateHierarchyDepth = true;
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postLoadInit();
			report.generateWorkDoneStats();
		} catch (Exception e) {
			info("Failed to produce MissingAttributeReport due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException, IOException {
		subHierarchies = new ArrayList<>();
		
		info ("Loading " + inputFile);
		if (!inputFile.canRead()) {
			throw new TermServerScriptException ("Cannot read: " + inputFile);
		}
		
		List<String> lines = Files.readLines(inputFile, Charsets.UTF_8);
		for (String line : lines) {
			if (!line.trim().isEmpty()) {
				String[] concepts = line.split(COMMA);
				Concept concept = gl.getConcept(concepts[0]);
				subHierarchies.add(concept);
				
				if (concepts.length > 1) {
					List<Concept> exclusions = new ArrayList<Concept>();
					for (int idx = 1; idx < concepts.length; idx++) {
						exclusions.add(gl.getConcept(concepts[idx]));
					}
					exclusionMap.put(concept, exclusions);
				}
			}
		} 
		//subHierarchies.addAll(ROOT_CONCEPT.getDescendents(IMMEDIATE_CHILD));
		//subHierarchies.add(CLINICAL_FINDING);
		co_occurrantTypeAttrb =  new Concept[] {
				gl.getConcept("47429007") //|Associated with (attribute)|
		};
		
		complexTypeAttrbs = new Concept[] {
			gl.getConcept("42752001"), //|Due to (attribute)|
			gl.getConcept("726633004"), //|Temporally related to (attribute)|
			gl.getConcept("255234002"), //|After (attribute)|
			gl.getConcept("288556008"), //|Before (attribute)|
			gl.getConcept("371881003"), //|During (attribute)|
			gl.getConcept("363713009"), //|Has interpretation (attribute)|
			gl.getConcept("363714003")  //|Interprets (attribute)|
		};
		postInit();
		ipReport = new InitialAnalysis(this);
	}

	private void generateWorkDoneStats() throws TermServerScriptException {
		ipReport.setQuiet(true);
		Set<Concept> alreadyAccountedFor = new HashSet<>();
		for (Concept subHierarchyStart : subHierarchies) {
			int[] templateTypeTotal = new int[TemplateType.values().length];
			int[] templateTypeModified = new int[TemplateType.values().length];
			debug ("Analysing subHierarchy: " + subHierarchyStart);
			Set<Concept> subHierarchy = new HashSet<>(gl.getDescendantsCache().getDescendentsOrSelf(subHierarchyStart)); //
			removeExclusions(subHierarchyStart, subHierarchy);
			int total = subHierarchy.size();
			subHierarchy.removeAll(alreadyAccountedFor);
			int withRemovals = subHierarchy.size();
			int countedElsewhere = total - withRemovals;
			for (Concept c : subHierarchy) {
				TemplateType type = getTemplateType(c);
				templateTypeTotal[type.ordinal()]++;
				//Has this concept been modified in the stated form since X?
				if (isModified(c, CharacteristicType.STATED_RELATIONSHIP)) {
					templateTypeModified[type.ordinal()]++;
				}
			}
			
			report (subHierarchyStart, subHierarchyStart.getDepth(),
					countedElsewhere, 
					templateTypeTotal[0] , templateTypeModified[0],
					templateTypeTotal[1] , templateTypeModified[1],
					templateTypeTotal[2] ,templateTypeModified[2],
					templateTypeTotal[3] , templateTypeModified[3],
					templateTypeTotal[4] , templateTypeModified[4],
					total);
			alreadyAccountedFor.addAll(subHierarchy);
		}
	}

	private void removeExclusions(Concept subHierarchyStart, Set<Concept> subHierarchy) throws TermServerScriptException {
		//Do we have exclusions for this subHierarchy?
		List<Concept> theseExclusions = exclusionMap.get(subHierarchyStart);
		if (theseExclusions != null) {
			for (Concept thisExclusion : theseExclusions) {
				subHierarchy.removeAll(gl.getDescendantsCache().getDescendentsOrSelf(thisExclusion));
			}
		}
	}

	private TemplateType getTemplateType(Concept c) throws TermServerScriptException {
		//Zero case, do we in fact have no attributes?
		if (countAttributes(c, CharacteristicType.INFERRED_RELATIONSHIP) == 0) {
			return TemplateType.NONE;
		}
		
		//Firstly, if we have any of the complex attribute types 
		if (SnomedUtils.getTargets(c, complexTypeAttrbs, CharacteristicType.INFERRED_RELATIONSHIP).size() > 0) {
			//Do we have an associated morphology
			if (SnomedUtils.getTargets(c, new Concept[] {ASSOC_MORPH}, CharacteristicType.INFERRED_RELATIONSHIP).size() > 0 ) {
				return TemplateType.COMPLEX;
			} else {
				return TemplateType.COMPLEX_NO_MORPH;
			}
		}
		
		//Do we have a pure co-occurrent type
		if (SnomedUtils.getTargets(c, co_occurrantTypeAttrb, CharacteristicType.INFERRED_RELATIONSHIP).size() > 0) {
			return TemplateType.PURE_CO;
		}
		
		//Do we have a simple co-occurrent word?
		for (String word : co_occurrantWords) {
			if (c.getFsn().contains(word)) {
				return TemplateType.PURE_CO;
			}
		}
		
		//Otherwise we'll assume it's simple
		return TemplateType.SIMPLE;
	}

	private boolean isModified(Concept c, CharacteristicType charType) throws TermServerScriptException {
		for (Relationship r : c.getRelationships(charType, ActiveState.BOTH)) {
			//Exclude IS_A relationships
			if (r.getType().equals(IS_A)) {
				continue;
			}
			
			if (r.getEffectiveTime() == null || Integer.parseInt(r.getEffectiveTime()) > modifiedSince) {
				return true;
			}
		}
		return false;
	}
	
}

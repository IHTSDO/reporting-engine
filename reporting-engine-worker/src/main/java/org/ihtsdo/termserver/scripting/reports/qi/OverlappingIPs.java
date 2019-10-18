package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.TermServerScriptException;

import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * QI-61
 * Obtain a number of intermediate primitive reports, and work out what the overlap is
 * */
public class OverlappingIPs extends TermServerReport {
	
	/* TODO ON REQUEST: Modify this code to work with ECL rather than subhierarchies
	Map<String, Concept> shortNames = new HashMap<>();
	Map<Concept, List<Concept>> subHierarchyIpMap = new HashMap<>();
	List<Concept> subHierarchies;
	InitialAnalysis intermediatePrimitivesReport;
	Set<Concept> allIPs = new HashSet<>();

	public static void main(String[] args) throws TermServerScriptException, IOException {
		OverlappingIPs report = new OverlappingIPs();
		try {
			ReportSheetManager.targetFolderId = "1m7MVhMePldYrNjOvsE_WTAYcowZ4ps50"; //Team Drive: Content Reporting Artefacts / QI / Initial Analysis
			report.init(args);
			report.loadProjectSnapshot(false);  //just FSNs
			report.postLoadInit();
			report.run();
		} catch (Exception e) {
			info("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException {
		super.init(args);
		intermediatePrimitivesReport = new InitialAnalysis(this);
		intermediatePrimitivesReport.setQuiet(true);
	}
	
	private void postLoadInit() throws TermServerScriptException {
		subHierarchies = new ArrayList<>();
		
		/*shortNames.put("virus", gl.getConcept("34014006"));
		subHierarchies.add(shortNames.get("virus")); // |Viral disease (disorder)|
		
		shortNames.put("bacteria", gl.getConcept("87628006"));
		subHierarchies.add(shortNames.get("bacteria")); // |Bacterial infectious disease (disorder)|
		
		shortNames.put("protozoa", gl.getConcept("95896000"));
		subHierarchies.add(shortNames.get("protozoa")); // |Protozoan infection (disorder)| 
		
		shortNames.put("fungus", gl.getConcept("3218000"));
		subHierarchies.add(shortNames.get("fungus"));  // |Mycosis (disorder)|
		
		shortNames.put("wound", gl.getConcept("416462003"));
		subHierarchies.add(shortNames.get("wound")); //  |Wound (disorder)|
		
		shortNames.put("fracture", gl.getConcept("125605004"));
		subHierarchies.add(shortNames.get("fracture")); //  |Fracture of bone (disorder)|
		
		
		String reportName = shortNames.keySet().stream().collect(Collectors.joining(" + "));
		reportName += " IP overlap";
		setReportName(reportName);
		additionalReportColumns = "FSN, Overlap";
		getReportManager().initialiseReportFiles(new String[] {"SCTID, FSN, Overlap"});
	}


	private void run() throws TermServerScriptException {
		info ("Calculating IP Overlap in " + subHierarchies.size() + " sub-hierarchies");
		startTimer();
		for (Concept subHierarchy : subHierarchies) {
			info ("Identifying IPs in " + subHierarchy);
			intermediatePrimitivesReport.setSubHierarchy(subHierarchy.getConceptId());
			intermediatePrimitivesReport.reportConceptsAffectedByIntermediatePrimitives();
			List<Concept> ips = new ArrayList<>(intermediatePrimitivesReport.intermediatePrimitives.keySet());
			subHierarchyIpMap.put(subHierarchy, ips);
			allIPs.addAll(ips);
		}
		
		//Now work out which ips exist in more than one list
		for (Concept ip : allIPs) {
			String overlapStr = "";
			int overlapCnt = 0;
			for (Map.Entry<String, Concept> shortEntry : shortNames.entrySet()) {
				if (subHierarchyIpMap.get(shortEntry.getValue()).contains(ip)) {
					incrementSummaryInformation("Overlaps identified");
					overlapCnt++;
					overlapStr += ((overlapCnt > 1)?", ":"") + shortEntry.getKey();
				}
			}
			if (overlapCnt > 1) {
				report (ip, overlapStr);
			}
		}
	}
	*/
}

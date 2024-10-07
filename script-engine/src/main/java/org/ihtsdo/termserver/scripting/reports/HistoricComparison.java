package org.ihtsdo.termserver.scripting.reports;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * INFRA-3429
 * How many IPs were added as IP
 * How many SD concepts became IP
 * How many IPs became SD
 * How many IPs were inactivated)
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistoricComparison extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(HistoricComparison.class);

	public static final String START_RELEASE = "Start Release";
	public static final String END_RELEASE = "End Release";
	private String endRelease;
	Map<Long, HistoricData> histDataMap = new HashMap<>();
	DecimalFormat df = new DecimalFormat("##.###%");
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(START_RELEASE, "20170731");
		TermServerScript.run(HistoricComparison.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc Reports
		projectName = run.getMandatoryParamValue(START_RELEASE);
		endRelease = run.getParamValue(END_RELEASE, "MAIN");
		reportNullConcept = false;
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {"", 
												"SCTID, FSN, Issue"};
		String[] tabNames = new String[] {	"Summary Counts", 
											"Detail"};
		super.postInit(tabNames, columnHeadings);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(START_RELEASE).withType(JobParameter.Type.STRING).withMandatory().withDefaultValue(false)
				.add(END_RELEASE).withType(JobParameter.Type.STRING)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Find new clone")
				.withDescription("List all concepts with one semantic tag that have lexical equivalents in another tag, optionally ignoring some text")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		LOGGER.info("Populating historic data");
		populateHistoricData();
		
		//Now load in the end release
		project = new Project(endRelease, "MAIN");
		getArchiveManager().loadSnapshot(true); //FSNs only
		
		//Now compare our current data to the historic
		doHistoricComparison();
	}

	private void populateHistoricData() throws TermServerScriptException {
		Set<Concept> allKnownIps = identifyIntermediatePrimitives(gl.getAllConcepts(), CharacteristicType.INFERRED_RELATIONSHIP);
		Set<Concept> allKnownStatedIps = identifyIntermediatePrimitives(gl.getAllConcepts(), CharacteristicType.STATED_RELATIONSHIP);
		
		int activeConcepts = 0;
		int ipCount = 0;
		int statedIpCount = 0;
		int sdCount = 0;
		for (Concept c : gl.getAllConcepts()) {
			HistoricData hd = new HistoricData();
			hd.isSD = c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED);
			hd.isIP = allKnownIps.contains(c);
			hd.isStatedIP = allKnownStatedIps.contains(c);
			hd.isActive = c.isActive();
			histDataMap.put(Long.parseLong(c.getConceptId()), hd);
			
			if (hd.isActive) {
				activeConcepts++;
				if (hd.isIP) {
					ipCount++;
				}
				if (hd.isStatedIP) {
					statedIpCount++;
				}
				if (hd.isSD) {
					sdCount++;
				}
			}
		}
		
		report(null, project.getKey() + " Total Active Concepts: ", activeConcepts);
		report(null, project.getKey() + " Intermediate Primitives: ", ipCount);
		report(null, project.getKey() + " Percentage IPs: ", perc(ipCount, activeConcepts));
		report(null, project.getKey() + " Stated Intermediate Primitives: ", statedIpCount);
		report(null, project.getKey() + " Sufficiently defined: ", sdCount);
		report(null, project.getKey() + " Percentage SDs: ", perc(sdCount, activeConcepts));
		report(null, "");
	}

	private void doHistoricComparison() throws TermServerScriptException {
		int ipAdded = 0;
		int sdBecameIp = 0;
		int ipBecameSD = 0;
		int ipInactivated = 0;
		int activeConcepts = 0;
		int pBecameIp = 0;
		int pStoppedBeingIp = 0;
		int sdCount = 0;
		
		//Work out a superset of all possible concepts
		Set<Long> allKnownConceptIds = gl.getAllConcepts().stream()
				.map(c -> Long.parseLong(c.getConceptId()))
				.collect(Collectors.toSet());
		allKnownConceptIds.addAll(histDataMap.keySet());
		
		Set<Concept> allKnownIps = identifyIntermediatePrimitives(gl.getAllConcepts());
		Set<Concept> allKnownStatedIps = identifyIntermediatePrimitives(gl.getAllConcepts(), CharacteristicType.STATED_RELATIONSHIP);
		
		for (Long sctId : allKnownConceptIds) {
			Concept c = gl.getConcept(sctId);
			boolean isSD = c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED);
			//Did this concept exist historically?
			HistoricData hd = histDataMap.get(sctId);
			//Is this concept now an IP?
			boolean isIP = allKnownIps.contains(c);
			//Is it new, or existing?
			if (hd == null) {
				//has it been added as an IP?
				if (isIP) {
					ipAdded++;
					//TODO Call traceability to find out who created this concept
				}
			} else {
				//Did it become an IP over time?
				if (hd.isSD && isIP) {
					sdBecameIp++;
					report(SECONDARY_REPORT, c, "SD made intermediate primitive");
				}
				
				//Did an IP become SD?
				if (hd.isIP && isSD) {
					ipBecameSD++;
				}
				
				//Was it inactivated
				if (hd.isIP && !c.isActiveSafely()) {
					ipInactivated++;
				}
				
				//Was it primitive but not an IP and it became an IP?
				if (!hd.isSD && !hd.isIP && isIP) {
					report(SECONDARY_REPORT, c, "P made intermediate primitive");
					pBecameIp ++;
				}
				
				//Was it IP and just stopped being so - eg child SDs removed?
				if (c.isActiveSafely() && hd.isIP && !isSD && !isIP) {
					pStoppedBeingIp ++;
				}
			}
			
			if (c.isActiveSafely()) {
				activeConcepts++;
				if (isSD) {
					sdCount++;
				}
			}
		}
		report(null, endRelease + " Total Active Concepts: ", activeConcepts);
		report(null, endRelease + " Intermediate Primitives: ", allKnownIps.size());
		report(null, endRelease + " Percentage IPs: ", perc(allKnownIps.size(), activeConcepts));
		report(null, endRelease + " Stated Intermediate Primitives: ", allKnownStatedIps.size());
		report(null, endRelease + " Sufficiently defined: ", sdCount);
		report(null, endRelease + " Percentage SDs: ", perc(sdCount, activeConcepts));
		report(null, "");
		report(null, "Comparsion between " + projectName + " and " + endRelease);
		report(null, "IPs were added as IP", ipAdded);
		report(null, "SD concepts became IP", sdBecameIp);
		report(null, "IPs became SD", ipBecameSD);
		report(null, "IPs were inactivated", ipInactivated);
		report(null, "Primitive non-IP became IP", pBecameIp);
		report(null, "Primitive IP stopped being IP", pStoppedBeingIp);
	}

	private String perc (int numerator, int denominator) {
		double percent = (numerator / (double)denominator);
		return df.format(percent);
	}

	class HistoricData {
		boolean isIP = false;
		boolean isStatedIP = false;
		boolean isSD = false;
		boolean isActive = false;
	}
}

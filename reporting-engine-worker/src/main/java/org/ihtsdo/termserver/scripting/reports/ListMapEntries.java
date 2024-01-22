package org.ihtsdo.termserver.scripting.reports;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListMapEntries extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(ListMapEntries.class);
	private static String COMPACT = "Compact";
	private static String MAP_CONCEPT = "Map Concept";
	private static String REVERSE_MAP = "Reverse Map";
	private static String MAP_TARGET = "mapTarget";
	private static int MAX_CELL_SIZE = 49900;
	
	protected Concept mapConcept;
	protected boolean compact = false;
	protected boolean reverseMap = false;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(MAP_CONCEPT, "447562003 |SNOMED CT to ICD-10 extended map|");
		params.put(COMPACT, "true");
		params.put(REVERSE_MAP, "true");
		TermServerReport.run(ListMapEntries.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager(true).setLoadOtherReferenceSets(true);
		getArchiveManager(true).setPopulateReleasedFlag(true); //This forces a delta import - needed because we don't yet save 'Other' refset members to disk.
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		
		mapConcept = gl.getConcept(jobRun.getMandatoryParamValue(MAP_CONCEPT));
		compact = jobRun.getMandatoryParamBoolean(COMPACT);
		reverseMap = jobRun.getMandatoryParamBoolean(REVERSE_MAP);
		
		String[] columnHeadings = new String[] {
				(reverseMap?"Map Target, Count, Concept, RefsetMember" : "SCTID, FSN, MAP"), 
				"SCTID, FSN, Refset Member"};
		String[] tabNames = new String[] {
				"Map",
				"No Map Target"};
		super.postInit(tabNames, columnHeadings, false);

	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(MAP_CONCEPT).withType(JobParameter.Type.CONCEPT).withMandatory()
				.add(COMPACT).withType(JobParameter.Type.BOOLEAN).withMandatory().withDefaultValue("false")
				.add(REVERSE_MAP).withType(JobParameter.Type.BOOLEAN).withMandatory().withDefaultValue("false")
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("List selected concepts with preferred terms")
				.withDescription("This report lists map entries for the specified map.  Optionally concise (one row per entry) and optionally reversed, where the map target is the left hand column.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		if (reverseMap) {
			reportMapReversed();
		} else {
			reportMap();
		}
	}

	private void reportMapReversed() throws TermServerScriptException {
		Map<String, List<RefsetMember>> reverseMap = generateReverseMap();
		for (String mapTarget : reverseMap.keySet()) {
			List<RefsetMember> members = reverseMap.get(mapTarget);
			if (compact) {
				String concepts = members.stream()
						.map(rm -> gl.getConceptSafely(rm.getReferencedComponentId()).toString())
						.collect(Collectors.joining("\n"));
				if (concepts.length() > MAX_CELL_SIZE) {
					concepts = concepts.substring(0, MAX_CELL_SIZE) + "...";
					LOGGER.warn("List of concepts truncated for mapTarget {}", mapTarget);
				}
				String memberStr = members.stream()
						.map(rm -> rm.toString())
						.collect(Collectors.joining("\n"));
				if (memberStr.length() > MAX_CELL_SIZE) {
					memberStr = memberStr.substring(0, MAX_CELL_SIZE) + "...";
					LOGGER.warn("List of members truncated for mapTarget {}", mapTarget);
				}
				report(PRIMARY_REPORT,mapTarget,members.size(),concepts,memberStr);
			} else {
				for (RefsetMember rm : members) {
					report(PRIMARY_REPORT,mapTarget, gl.getConcept(rm.getReferencedComponentId()), rm);
				}
			}
		}
		
	}

	private Map<String, List<RefsetMember>> generateReverseMap() throws TermServerScriptException {
		LOGGER.info("Generating reverse map");
		Map<String, List<RefsetMember>> reverseMap = new TreeMap<>();
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			for (RefsetMember rm : c.getOtherRefsetMembers()) {
				if (rm.isActive() && rm.getRefsetId().equals(mapConcept.getId())) {
					String mapTarget = rm.getField(MAP_TARGET);
					if (StringUtils.isEmpty(mapTarget)) {
						report(SECONDARY_REPORT, c, rm);
						continue;
					}
					List<RefsetMember> entries = reverseMap.get(mapTarget);
					if (entries == null) {
						entries = new ArrayList<>();
						reverseMap.put(mapTarget, entries);
					}
					entries.add(rm);
				}
			}
		}
		LOGGER.info("Reverse map generated for {} targets", reverseMap.size());
		return reverseMap;
	}

	private void reportMap() throws TermServerScriptException {
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			for (RefsetMember rm : c.getOtherRefsetMembers()) {
				if (rm.isActive() && rm.getRefsetId().equals(mapConcept.getId())) {
					report(c, rm.getField(MAP_TARGET), rm);
				}
			}
		}
	}

}

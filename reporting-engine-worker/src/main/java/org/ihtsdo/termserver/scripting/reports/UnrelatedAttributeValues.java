package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.google.common.util.concurrent.AtomicLongMap;

/**
 * RP-462 Report to find unrelated attribute values
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnrelatedAttributeValues extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(UnrelatedAttributeValues.class);

	private static String ATTRIBUTE_TYPE = "Attribute Type";
	private AtomicLongMap<Concept> grandParentLeagueChart = AtomicLongMap.create();
	private Concept targetAttributeType;
	private double sufficentRelationships;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "<< 64572001 |Disease (disorder)|");
		params.put(ATTRIBUTE_TYPE, "363713009 |Has interpretation (attribute)|");
		TermServerReport.run(UnrelatedAttributeValues.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		runStandAlone = false; //We need a proper path lookup for MS projects
		super.init(run);
	}
	
	@Override
	public void postInit() throws TermServerScriptException {
		targetAttributeType = gl.getConcept(jobRun.getMandatoryParamValue(ATTRIBUTE_TYPE), false, true);
		subsetECL = jobRun.getMandatoryParamValue(ECL);
		String[] columnHeadings = new String[] {"Sctid, FSN, SemTag, Connection Count",
				"SCTID, FSN, SemTag, Attribute Type, Attribute Value, Most Popular Grandparent, Count, Detail",
				};
		String[] tabNames = new String[] {	"Most Popular GrandParents",
				"Concepts with unpopular grandparents"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.STRING).withMandatory()
				.add(ATTRIBUTE_TYPE).withType(JobParameter.Type.CONCEPT).withMandatory()
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Unrelated Attributes")
				.withDescription("This report lists concepts featuring the specified attribute type where the value used is unrelated (more distant than first cousin) to the other values.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		analyseSubset();
		reportMostPopularGrandParents();
		long totalRelationships = grandParentLeagueChart.sum();
		sufficentRelationships = (int)(totalRelationships * 0.05d);
		LOGGER.info ("Lower limit of popularity would be " + sufficentRelationships + " out of total available: " + totalRelationships);
		reportOutliers();
	}

	private void analyseSubset() throws TermServerScriptException {
		Concept[] types = new Concept[] {targetAttributeType};
		for (Concept c : findConcepts(subsetECL)) {
			Set<Concept> relevantValues = SnomedUtils.getTargets(c, types, CharacteristicType.INFERRED_RELATIONSHIP);
			for (Concept value : relevantValues) {
				//For all my parents and grandparents, populate the league chart
				for (Concept grandParent : getGrandParents(value)) {
					grandParentLeagueChart.getAndIncrement(grandParent);
				}
			}
		}
	}

	private void reportMostPopularGrandParents() throws TermServerScriptException {
		List<Map.Entry<Concept, Long>> sorted = 
			new ArrayList<>(grandParentLeagueChart.asMap().entrySet());
		Collections.sort(sorted, Collections.reverseOrder(Map.Entry.comparingByValue()));
		for (Map.Entry<Concept, Long> entry : sorted) {
			report(entry.getKey(), entry.getValue());
		}
	}
	
	private void reportOutliers() throws TermServerScriptException {
		Concept[] types = new Concept[] {targetAttributeType};
		for (Concept c : findConcepts(subsetECL)) {
			Set<Concept> relevantValues = SnomedUtils.getTargets(c, types, CharacteristicType.INFERRED_RELATIONSHIP);
			for (Concept value : relevantValues) {
				//For all my parents and grandparents, populate the league chart
				Concept mostPopularGrandParent = null;
				for (Concept grandParent : getGrandParents(value)) {
					//Is this grandParent sufficiently well connected?
					long connections = grandParentLeagueChart.get(grandParent);
					if (mostPopularGrandParent == null || grandParentLeagueChart.get(grandParent) > grandParentLeagueChart.get(mostPopularGrandParent)) {
						mostPopularGrandParent = grandParent;
					}
					
					if (grandParentLeagueChart.get(mostPopularGrandParent) < sufficentRelationships) {
						report (SECONDARY_REPORT, c, targetAttributeType, value, grandParent, connections);
						countIssue(c);
					}
				}
			}
		}
		
	}

	private Set<Concept> getGrandParents(Concept c) {
		Set<Concept> grandParents = new HashSet<>();
		for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			for (Concept gParent : parent.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
				grandParents.add(gParent);
			}
		}
		return grandParents;
	}


}

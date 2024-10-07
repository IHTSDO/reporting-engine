package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.service.TraceabilityService;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;
import java.util.stream.Collectors;

public class ListConceptComponents extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(ListConceptComponents.class);

	public static String CONCEPT_IDS = "Concept Ids";
	TraceabilityService traceabilityService;
	List<String> conceptIdsOfInterest;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(CONCEPT_IDS,
				"821000172102");
		TermServerScript.run(ListConceptComponents.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc
		
		if (!StringUtils.isEmpty(run.getParamValue(CONCEPT_IDS))) {
			conceptIdsOfInterest = Arrays.stream(run.getMandatoryParamValue(CONCEPT_IDS).split(",",-1))
					.map(s -> s.trim())
					.collect(Collectors.toList());
		}
		
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] tabNames = new String[] {
				"Concept Components",
		};
		String[] columnHeadings = new String[] {
				"SCTID, FSN, SemTag, Component Type, Parent Component, Component Details,",
		};
		postInit(tabNames, columnHeadings);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(CONCEPT_IDS).withType(JobParameter.Type.CONCEPT_LIST).withMandatory()
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("List Concept Components")
				.withDescription("This report lists all related components of the specified concepts.")
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		if (conceptIdsOfInterest == null || conceptIdsOfInterest.isEmpty()) {
			throw new TermServerScriptException("Please specify concept ids to process.");
		}
		
		for (String conceptId : conceptIdsOfInterest) {
			Concept c = gl.getConcept(conceptId);
			if (c == null) {
				report(PRIMARY_REPORT, conceptId, "Not found at " + project.getKey());
			} else {
				reportComponents(c);
				countIssue(c);
			}
		}
		LOGGER.info("Job complete");
	}

	private void reportComponents(Concept c) throws TermServerScriptException {
		Set<Component> componentsReported = new HashSet<>();
		componentsReported.add(c);
		report(PRIMARY_REPORT, c, c.getComponentType(), c, c);
		//First report the descriptions so we can group the langrefset entries together.
		//Keep a note of those components reported, and then we can avoid reporting them again
		for (Description d : c.getDescriptions()) {
			report(PRIMARY_REPORT, c, d.getComponentType(), c, d.getType() + " " + d);
			componentsReported.add(d);
			reportSubComponents(d, componentsReported);
		}
		reportSubComponents(c, componentsReported);
	}

	private void reportSubComponents(Component c, Set<Component> componentsReported) throws TermServerScriptException {
		for (Component component : SnomedUtils.getAllComponents(c)) {
			if (componentsReported.contains(component)) {
				continue;
			}
			//If component is a description, we need to pad to align
			if (c instanceof Description) {
 				report(PRIMARY_REPORT, "", "", "", component.getComponentType(), c, component);
			} else {
				report(PRIMARY_REPORT, c, "", component.getComponentType(), c, component);
			}
			componentsReported.add(component);
		}
	}

}

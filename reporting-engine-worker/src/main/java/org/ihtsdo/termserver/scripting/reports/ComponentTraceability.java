package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.UnknownComponent;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.service.TraceabilityService;
import org.ihtsdo.termserver.scripting.service.MultiDetailTraceabilityService;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComponentTraceability extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(ComponentTraceability.class);

	public static final String COMPONENT_IDS = "Component Ids";
	TraceabilityService traceabilityService;
	List<String> componentIds;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(COMPONENT_IDS,
				"34a60d4c-7db7-5e84-bcb7-f5bac882de6b");
		TermServerScript.run(ComponentTraceability.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc

		if (!StringUtils.isEmpty(run.getParamValue(COMPONENT_IDS))) {
			componentIds = Arrays.stream(run.getMandatoryParamValue(COMPONENT_IDS).split(",",-1))
					.map(String::trim)
					.toList();
		}
		
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] tabNames = new String[] {
				"Component Traceability",
		};
		String[] columnHeadings = new String[] {
				"Id, Type, Action, Is Superseded, Created Date, Promoted Date, Task, Author, Highest Promoted Branch, Concept, Component",
		};
		postInit(tabNames, columnHeadings);
		
		traceabilityService = new MultiDetailTraceabilityService(jobRun, this);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(COMPONENT_IDS).withType(JobParameter.Type.STRING).withMandatory()
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Component Traceability")
				.withDescription("This report lists traceability for the specified (comma separated list of) components.   Note that this report does work at the per-component level, so a request for concept traceability would only return results if the concept has had its definition status modified.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		if (componentIds == null || componentIds.isEmpty()) {
			throw new TermServerScriptException("Please specify component ids for which traceability should be reported.");
		}
		
		for (String componentId : componentIds) {
			Component c = gl.getComponent(componentId);
			if (c == null) {
				report(PRIMARY_REPORT, componentId, "Not found in Snapshot build from " + project.getKey());
				c = new UnknownComponent(componentId, ComponentType.UNKNOWN);
			}
			
			int rowsReported = traceabilityService.populateTraceabilityAndReport(PRIMARY_REPORT, c, (Object)null);
			if (rowsReported == 0) {
				report(PRIMARY_REPORT, componentId, "No traceability data recovered");
			}
			countIssue(null, rowsReported);
		}
		traceabilityService.tidyUp();
		LOGGER.info("Job complete");
	}
	
}

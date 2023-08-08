package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.UnknownComponent;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.service.TraceabilityService;
import org.ihtsdo.termserver.scripting.service.MultiDetailTraceabilityService;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComponentTraceability extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(ComponentTraceability.class);

	public static String COMPONENT_IDS = "Component Ids";
	TraceabilityService traceabilityService;
	List<String> componentIds;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		/*params.put(COMPONENT_IDS, 
				"4020081000202112,2758751000202110,2759271000202119,4018121000202118," +
				"3995431000202112,3773311000202113,2758991000202119,2759281000202117," +
				"4256851000202116,3804831000202115,3773331000202115,2037241000202116," +
				"3992211000202110,4118411000202111,3816761000202114,2990661000202119," +
				"4255341000202118,2205741000202116,3805921000202113,2990651000202117," +
				"2037151000202114,3765291000202116,4255351000202115,2758741000202112," +
				"3909031000202116,4019741000202118,2202321000202118,3815361000202119," +
				"2990591000202114,4255441000202114,4257961000202112,3337151000202119," +
				"4258031000202119,2778641000202116,4256311000202111,2990711000202112," +
				"4257621000202113,2177271000202117,3773321000202117,3766611000202115," +
				"4251151000202119,3910121000202111,3992221000202119,4204801000202116," +
				"3700231000202113,4257611000202117");*/
		/*params.put(COMPONENT_IDS, "85197d34-bcc9-44b7-b024-b8cfda2c4bfa,86592bf0-bac6-4ea1-824f-b61cc5a065f9," +
				"6f33c8d8-b280-48a0-9574-eb2489db77df,95ccfa99-173d-4908-bde4-107cb778b9e6,"+
				"6c733651-1956-49ed-a2f2-ee7627290695");
		params.put(COMPONENT_IDS, "8041000146102");*/
		params.put(COMPONENT_IDS, 
				"979d5ca4-7d1e-4f25-a0ec-d95c4c7d3792,52f237d0-820b-4a42-8cc8-60ba26c8f4ab," +
				"00327e0e-8241-45f3-ab79-3d10ff8959e0,569211c3-cc6b-4d6a-afdd-e57071dd5dbb," +
				"5096040c-befe-4eb1-be22-21a7b385d106,715af76b-6a4c-4606-abf7-8d244f16062b," +
				"8c7e1b29-2d02-4127-a5e0-29b5ed343319,c2a1be7a-b423-4eea-8968-f496bb5c0059," +
				"309fa44d-f5a4-4e82-a4d6-26577f266e66,8df03fd0-ba92-4172-bd5d-79ff50088a17");
		TermServerReport.run(ComponentTraceability.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc
		
		if (!StringUtils.isEmpty(run.getParamValue(COMPONENT_IDS))) {
			componentIds = Arrays.stream(run.getMandatoryParamValue(COMPONENT_IDS).split(",",-1))
					.map(s -> s.trim())
					.collect(Collectors.toList());
		}
		
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] tabNames = new String[] {
				"Component Traceability",
		};
		String[] columnHeadings = new String[] {
				"Id, Type, Action, Created Date, Promoted Date, Task, Author, Highest Promoted Branch, Concept, Component",
		};
		postInit(tabNames, columnHeadings, false);
		
		traceabilityService = new MultiDetailTraceabilityService(jobRun, this);
		//Do not set a search path because we want to know about all activity, not just
		//that which has been promoted.
		//traceabilityService.setBranchPath(project.getKey());
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
	
	public void runJob() throws TermServerScriptException {
		if (componentIds == null || componentIds.isEmpty()) {
			throw new TermServerScriptException("Please specify component ids for which traceability should be reported.");
		}
		
		for (String componentId : componentIds) {
			Component c = gl.getComponent(componentId);
			if (c == null) {
				report(PRIMARY_REPORT, componentId, "Not found at " + project.getKey());
				c = new UnknownComponent(componentId, ComponentType.UNKNOWN);
			}
			
			int rowsReported = traceabilityService.populateTraceabilityAndReport(PRIMARY_REPORT, c, (Object)null);
			countIssue(null, rowsReported);
		}
		traceabilityService.tidyUp();
		LOGGER.info ("Job complete");
	}
	
}

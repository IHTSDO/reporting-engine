package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
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

public class ComponentTraceability extends TermServerReport implements ReportClass {
	
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
		params.put(COMPONENT_IDS, "85197d34-bcc9-44b7-b024-b8cfda2c4bfa,86592bf0-bac6-4ea1-824f-b61cc5a065f9" +
				"6f33c8d8-b280-48a0-9574-eb2489db77df,95ccfa99-173d-4908-bde4-107cb778b9e6"+
				"6c733651-1956-49ed-a2f2-ee7627290695");
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
				"Id, Type, Action, Created Date, Promoted Date, Task, Author, Highest Promoted Branch, Component",
		};
		postInit(tabNames, columnHeadings, false);
		
		traceabilityService = new MultiDetailTraceabilityService(jobRun, this);
		traceabilityService.setBranchPrefixFilter(project.getKey());
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(COMPONENT_IDS).withType(JobParameter.Type.STRING)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Component Traceability")
				.withDescription("This report lists traceability for the specified (comma separated list of) components.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		for (String componentId : componentIds) {
			Component c = gl.getComponent(componentId);
			if (c == null) {
				report(PRIMARY_REPORT, componentId, "Not found at " + project.getKey());
			} else {
				traceabilityService.populateTraceabilityAndReport(PRIMARY_REPORT, c, (Object)null);
			}
		}
		traceabilityService.tidyUp();
		info ("Job complete");
	}
	
}

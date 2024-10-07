package org.ihtsdo.termserver.scripting.reports.qi;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Template;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * See https://confluence.ihtsdotools.org/display/IAP/Quality+Improvements+2018
 * Update: https://confluence.ihtsdotools.org/pages/viewpage.action?pageId=61155633
 * CDI-52 Update report to successfully run against projects with concrete values.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TemplateList extends AllKnownTemplates implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(TemplateList.class);
	
	private static final String DEFAULT_TEMPLATE_SERVICE_URL = "https://dev-snowstorm.ihtsdotools.org/template-service";

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(SERVER_URL, DEFAULT_TEMPLATE_SERVICE_URL);
		TermServerScript.run(TemplateList.class, args, params);
	}

	@Override
	public void init (JobRun jobRun) throws TermServerScriptException {
		additionalReportColumns = "Domain, SemTag, Template Name / QI Path, Source, Documentation";
		super.init(jobRun);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {"Template Domain, Template Name / QI Path, Source, Documentation", 
												"Template Name, Invalid Reason"};
		String[] tabNames = new String[] {	"Template List", 
											"Invalid Templates"};
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SERVER_URL)
					.withType(JobParameter.Type.HIDDEN)
					.withMandatory()
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.QI))
				.withName("Template List")
				.withDescription("Lists all known templates")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		//Weed out invalid templates ie where the domain no longer exists
		for (String subSetECL : new ArrayList<>(domainTemplates.keySet())) {
			//Does the ECL identify any concepts?
			try {
				if (findConcepts(subSetECL).isEmpty()) {
					List<Template> invalidTemplates= domainTemplates.get(subSetECL);
					for (Template t : invalidTemplates) {
						report(SECONDARY_REPORT, t.getName(), "Template domain/subset did not identify any concepts" , t.getDomain());
					}
					domainTemplates.remove(subSetECL);
				}
			} catch (Exception e) {
				List<Template> invalidTemplates= domainTemplates.get(subSetECL);
				for (Template t : invalidTemplates) {
					report(SECONDARY_REPORT, t.getName(), "Exception while recovering domain/subset: " + e.getMessage() , t.getDomain());
				}
				domainTemplates.remove(subSetECL);
			}
		}
		
		//We're going to sort by the top level domain and the domain's FSN
		Comparator<Entry<String, List<Template>>> comparator = this::compare;
		Map<String, List<Template>> sortedDomainTemplates = domainTemplates.entrySet().stream()
				.sorted(comparator)
				.collect(Collectors.toMap(Entry::getKey, Entry::getValue, (k, v) -> k, LinkedHashMap::new));
		
		//Work through all domains
		for (Map.Entry<String, List<Template>> entry : sortedDomainTemplates.entrySet()) {
			String domainStr = entry.getKey();
			for (Template template : entry.getValue()) {
				try {
					//Did this template come from QI or the Template Service
					report(PRIMARY_REPORT, domainStr, template.getName(), template.getSource(), template.getDocumentation());
				} catch (Exception e) {
					LOGGER.error ("Exception while processing template " + domainStr, e);
					report(SECONDARY_REPORT, template, e);
				}
			}
		}
	}
	
	private int compare(Entry<String, List<Template>> entry1, Entry<String, List<Template>> entry2) {
		try {
			//First sort on top level hierarchy
			Collection<Concept> c1 = findConcepts(entry1.getKey());
			Collection<Concept> c2 = findConcepts(entry2.getKey());
			
			Concept top1 = SnomedUtils.getHighestAncestorBefore(c1.iterator().next(), ROOT_CONCEPT);
			Concept top2 = SnomedUtils.getHighestAncestorBefore(c2.iterator().next(), ROOT_CONCEPT);
			
			if (top1.equals(top2)) {
				return top1.getFsn().compareTo(top2.getFsn());
			}
		} catch (Exception e) {
			//ignore
		}

		return -1;
	}

}

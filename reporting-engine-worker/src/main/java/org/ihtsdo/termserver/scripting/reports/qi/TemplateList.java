package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;

/**
 * See https://confluence.ihtsdotools.org/display/IAP/Quality+Improvements+2018
 * Update: https://confluence.ihtsdotools.org/pages/viewpage.action?pageId=61155633
 */
public class TemplateList extends AllKnownTemplates implements ReportClass {
	final static String defaultTemplateServiceUrl = "https://authoring.ihtsdotools.org/template-service";
	
	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException {
		Map<String, String> params = new HashMap<>();
		params.put(SERVER_URL, defaultTemplateServiceUrl);
		TermServerReport.run(TemplateList.class, args, params);
	}
	
	public void init (JobRun jobRun) throws TermServerScriptException {
		additionalReportColumns = "Domain, SemTag, Template Name / QI Path, Source, Documentation";
		super.init(jobRun);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SERVER_URL)
					.withType(JobParameter.Type.HIDDEN)
					.withMandatory()
				.build();
		return new Job( new JobCategory(JobType.REPORT, JobCategory.QI),
						"Template List",
						"Lists all known templates",
						params,
						Job.ProductionStatus.PROD_READY);
	}

	
	public void runJob() throws TermServerScriptException {
		
		//Check all of our domain points are still active concepts, or we'll have trouble with them!
		Set<String> invalidTemplateDomains = domainTemplates.keySet().stream()
			.filter(d -> !gl.getConceptSafely(d).isActive())
			.collect(Collectors.toSet());
		
		for (String invalidTemplateDomain : invalidTemplateDomains) {
			List<Template> templates = domainTemplates.get(invalidTemplateDomain);
			for (Template t : templates) {
				warn ("Inactive domain " + gl.getConcept(invalidTemplateDomain) + ": " + t.getName());
			}
			domainTemplates.remove(invalidTemplateDomain);
		}
		
		//We're going to sort by the top level domain and the domain's FSN
		Comparator<Entry<String, List<Template>>> comparator = (e1, e2) -> compare(e1, e2);

		Map<String, List<Template>> sortedDomainTemplates = domainTemplates.entrySet().stream()
				.sorted(comparator)
				.collect(Collectors.toMap(Entry::getKey, Entry::getValue, (k, v) -> k, LinkedHashMap::new));
		
		//Work through all domains
		for (Map.Entry<String, List<Template>> entry : sortedDomainTemplates.entrySet()) {
			String domainStr = entry.getKey();
			for (Template template : entry.getValue()) {
				try {
					Concept domain = gl.getConcept(domainStr);
					//Did this template come from QI or the Template Service
					report (domain, template.getName(), template.getSource(), template.getDocumentation());
				} catch (Exception e) {
					error ("Exception while processing template " + domainStr, e);
				}
			}
		}
	}
	
	private int compare(Entry<String, List<Template>> entry1, Entry<String, List<Template>> entry2) {
		//First sort on top level hierarchy
		Concept c1 = gl.getConceptSafely(entry1.getKey());
		Concept c2 = gl.getConceptSafely(entry2.getKey());
		
		Concept top1 = SnomedUtils.getHighestAncestorBefore(c1, ROOT_CONCEPT);
		Concept top2 = SnomedUtils.getHighestAncestorBefore(c2, ROOT_CONCEPT);
		
		if (top1.equals(top2)) {
			//In the same major hierarchy, sort on the domain fsn
			return c1.getFsn().compareTo(c2.getFsn());
		} else {
			return top1.getFsn().compareTo(top2.getFsn());
		}
	}

}

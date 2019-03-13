package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.DescendentsCache;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.template.TemplateUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;

/**
 * See https://confluence.ihtsdotools.org/display/IAP/Quality+Improvements+2018
 * Update: https://confluence.ihtsdotools.org/pages/viewpage.action?pageId=61155633
 */
public class AllTemplateCompliance extends AllKnownTemplates implements ReportClass {
	
	Set<Concept> alreadyCounted = new HashSet<>();
	Map<Concept, Integer> outOfScopeCache = new HashMap<>();

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		Map<String, String> params = new HashMap<>();
		params.put(SERVER_URL, "https://authoring.ihtsdotools.org/template-service");
		TermServerReport.run(AllTemplateCompliance.class, args, params);
	}
	
	public void init (JobRun jobRun) throws TermServerScriptException {
		additionalReportColumns = "Domain, SemTag, Hierarchy (Total), Total Concepts in Domain, OutOfScope - Domain, OutOfScope - Hierarchy, Counted Elsewhere, Template Compliant, Templates Considered";
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
						"All Templates Compliance Stats",
						"For every domain which has one or more templates, determine how many concepts comply to that template(s).",
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
			try {
				Concept domain = gl.getConcept(domainStr);
				List<Template> templates = entry.getValue();
				info ("Examining " + domain + " against " + templates.size() + " templates");
				examineDomain(domain, templates);
			} catch (Exception e) {
				error ("Exception while processing domain " + domainStr, e);
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

	private void examineDomain(Concept domain, List<Template> templates) throws TermServerScriptException {
		DescendentsCache cache = gl.getDescendantsCache();
		
		if (domain.getConceptId().equals("34014006")) {
			//debug ("Debug here");
		}
		Set<Concept> subHierarchy = new HashSet<>(cache.getDescendentsOrSelf(domain));  //Clone as we need to modify
		int domainSize = subHierarchy.size();
		Concept topLevelConcept = SnomedUtils.getHighestAncestorBefore(domain, ROOT_CONCEPT);
		Set<Concept> topLevelHierarchy = cache.getDescendentsOrSelf(topLevelConcept);
		int topLevelHierarchySize = topLevelHierarchy.size();
		
		//How much of the top level hierarchy is out of scope due to have no model?
		//Cache this, it's expensive!
		
		Integer outOfScope = outOfScopeCache.get(topLevelConcept);
		if (outOfScope == null) {
			outOfScope = topLevelHierarchy.stream()
					.filter(c -> countAttributes(c, CharacteristicType.INFERRED_RELATIONSHIP) == 0)
					.collect(Collectors.toSet()).size();
			outOfScopeCache.put(topLevelConcept, outOfScope);
		}

		//Now how many of these are we removing because they have no model?
		Set<Concept> noModel = subHierarchy.stream()
				.filter(c -> countAttributes(c, CharacteristicType.INFERRED_RELATIONSHIP) == 0)
				.collect(Collectors.toSet());
		int noModelSize = noModel.size();
		subHierarchy.removeAll(noModel);
		
		//And how many concepts have we already matched to a template?
		int beforeRemoval = subHierarchy.size();
		subHierarchy.removeAll(alreadyCounted);
		int countedElsewhere = beforeRemoval - subHierarchy.size();
		
		Set<Concept> templateMatches = new HashSet<>();
		//Now lets see how many we can match to a template
		nextConcept:
		for (Concept c : subHierarchy) {
			for (Template t : templates) {
				if (TemplateUtils.matchesTemplate(c, t, cache, CharacteristicType.INFERRED_RELATIONSHIP)) {
					templateMatches.add(c);
					continue nextConcept;
				}
			}
		}
		//Now remember that we've reported all these
		alreadyCounted.addAll(templateMatches);
		String topHierarchyText = topLevelConcept.getPreferredSynonym() + " (" + topLevelHierarchySize + ")";
		String templatesConsidered = templates.stream().map(t -> t.getName()).collect(Collectors.joining(",\n"));
		
		//Domain/SemTag, Hierarchy (Total), Total Concepts in Domain, OutOfScope - Domain, OutOfScope - Hierarchy, Counted Elsewhere, Template Compliant, Templates Considered";
		report(domain, topHierarchyText, domainSize, noModelSize, outOfScope, countedElsewhere, templateMatches.size(), templatesConsidered);
	}
	
}

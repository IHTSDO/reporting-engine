package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.DescendantsCache;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.template.TemplateUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 * See https://confluence.ihtsdotools.org/display/IAP/Quality+Improvements+2018
 * Update: https://confluence.ihtsdotools.org/pages/viewpage.action?pageId=61155633
 * Update: RP-139
 */
public class AllTemplateCompliance extends AllKnownTemplates implements ReportClass {
	
	Set<Concept> alreadyCounted = new HashSet<>();
	Map<Concept, Integer> outOfScopeCache = new HashMap<>();
	int totalTemplateMatches = 0;

	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(SERVER_URL, "https://authoring.ihtsdotools.org/template-service");
		
		/*List<String> argList = new ArrayList<>(Arrays.asList(args));
		argList.add("-p");
		argList.add("SnomedCT_InternationalRF2_PRODUCTION_20170731T150000Z.zip");
		TermServerReport.run(AllTemplateCompliance.class, argList.toArray(args), params);
		
		argList.remove(argList.size() - 1);
		argList.add("SnomedCT_InternationalRF2_Production_20180131T120000Z.zip");
		TermServerReport.run(AllTemplateCompliance.class, argList.toArray(args), params);*/
		
		TermServerReport.run(AllTemplateCompliance.class,args, params);
	}
	
	/*public void init(JobRun run) throws TermServerScriptException {
		commonInit(run, true);  //Single template mode
		populateTemplateFromTS("Fracture dislocation of [body structure] (disorder) - v1.0");
		super.init(run);
	}*/
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {"Subset ECL, Hierarchy (Total), Total Concepts in Domain, OutOfScope - Domain, OutOfScope - Hierarchy, Counted Elsewhere, Template Compliant, Templates Considered", 
												"KPI, Count",
												"Template Name, Issue"};
		String[] tabNames = new String[] {	"Template Compliance", 
											"Summary Stats / KPIs",
											"Template issues"};
		super.postInit(tabNames, columnHeadings, false);
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
				.withName("All Templates Compliance Stats")
				.withDescription("For every domain which has one or more templates, determine how many concepts comply to that template(s).")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	
	public void runJob() throws TermServerScriptException {
		
		//Check all of our domain points are still active concepts, or we'll have trouble with them!
		Set<String> invalidTemplateDomains = domainTemplates.keySet().stream()
			.filter(ecl -> findConceptsSafely(ecl, toString(domainTemplates.get(ecl))).size() == 0)
			.collect(Collectors.toSet());
		
		for (String invalidTemplateDomain : invalidTemplateDomains) {
			List<Template> templates = domainTemplates.get(invalidTemplateDomain);
			for (Template t : templates) {
				String msg = "Inactive or Non-existent domain: " + invalidTemplateDomain;
				warn (msg + " in template: " + t.getName());
				report(TERTIARY_REPORT, t.getName(), msg);
			}
			domainTemplates.remove(invalidTemplateDomain);
		}
		
		//Work through all subsets
		for (Map.Entry<String, List<Template>> entry : domainTemplates.entrySet()) {
			String subsetECL = entry.getKey();
			try {
				List<Template> templates = entry.getValue();
				info ("Examining subset defined by '" + subsetECL + "' against " + templates.size() + " templates");
				examineSubset(subsetECL, templates);
			} catch (Exception e) {
				error ("Exception while processing domain " + subsetECL, e);
			}
		}
		reportKPIs();
	}
	
	private String toString(List<Template> templates) {
		return templates.stream()
				.map(t -> t.getName())
				.collect(Collectors.joining(", "));
	}

	private void reportKPIs() throws TermServerScriptException {
		//Total number of active concepts
		long activeConcepts = gl.getAllConcepts().stream()
							.filter(c -> c.isActive())
							.count();
		report (SECONDARY_REPORT, "Total number of active concepts", activeConcepts);
		long inScope = calculateInScopeConcepts();
		long outOfScope = activeConcepts - inScope;
		report (SECONDARY_REPORT, "Out of scope (No model or non-clinical concepts, metadata, etc)", outOfScope);
		report (SECONDARY_REPORT, "In Scope (should conform to a template)", inScope);
		report (SECONDARY_REPORT, "Actually conform", totalTemplateMatches);
		
		DecimalFormat df = new DecimalFormat("##.##%");
		double percentConformance = (totalTemplateMatches / (double)inScope);
		String formattedPercentConformance = df.format(percentConformance);
		report (SECONDARY_REPORT, "% of 'in scope' concepts that conform to a template", formattedPercentConformance);
	}

	private long calculateInScopeConcepts() throws TermServerScriptException {
		info ("Obtaining count of concepts that are 'in scope'");
		int inScopeCount = 0;
		Concept[] inScope = new Concept[] { BODY_STRUCTURE, CLINICAL_FINDING,
											PHARM_BIO_PRODUCT, PROCEDURE,
											SITN_WITH_EXP_CONTXT, SPECIMEN,
											OBSERVABLE_ENTITY, EVENT, 
											PHARM_DOSE_FORM};
		Set<Concept> allInScopeConcepts = new HashSet<>();
		//We'll create a set to avoid double counting concepts in multiple TLHs
		for (Concept subHierarchy : inScope) {
			Set<Concept> concepts = gl.getDescendantsCache()
					.getDescendentsOrSelf(subHierarchy)
					.stream()
					.filter(c -> inModuleScope(c))
					.collect(Collectors.toSet());
			info(subHierarchy + " contains " + concepts.size() + " concepts (in target module).");
			allInScopeConcepts = ImmutableSet.copyOf(Iterables.concat(allInScopeConcepts, concepts));
		}
		//Now only count those concepts that have some non-ISA inferred attributes
		for (Concept c : allInScopeConcepts) {
			if (SnomedUtils.countAttributes(c, CharacteristicType.INFERRED_RELATIONSHIP) > 0) {
				inScopeCount++;
			}
		}
		return inScopeCount;
	}

	private void examineSubset(String ecl, List<Template> templates) throws TermServerScriptException {
		DescendantsCache cache = DescendantsCache.getDescendentsCache();
		Collection<Concept> subset = findConcepts(ecl);
		if (subset.size() == 0) {
			warn ("No concepts found in subset defined by '" + ecl + "' skipping");
			return;
		}
		int subsetSize = subset.size();
		Concept randomConcept = subset.iterator().next();
		Concept topLevelConcept = SnomedUtils.getHighestAncestorBefore(randomConcept, ROOT_CONCEPT);
		Set<Concept> topLevelHierarchy = cache.getDescendentsOrSelf(topLevelConcept);
		int topLevelHierarchySize = topLevelHierarchy.size();
		
		//How much of the top level hierarchy is out of scope due to have no model, or Oprhanet
		//Cache this, it's expensive!
		Integer outOfScope = outOfScopeCache.get(topLevelConcept);
		if (outOfScope == null) {
			outOfScope = topLevelHierarchy.stream()
					.filter(c -> countAttributes(c, CharacteristicType.INFERRED_RELATIONSHIP) == 0 ||
							gl.isOrphanetConcept(c))
					.collect(Collectors.toSet()).size();
			outOfScopeCache.put(topLevelConcept, outOfScope);
		}

		//Now how many of these are we removing because they have no model?
		//OR Orphanet
		Set<Concept> noModel = subset.stream()
				.filter(c -> countAttributes(c, CharacteristicType.INFERRED_RELATIONSHIP) == 0 ||
						gl.isOrphanetConcept(c))
				.collect(Collectors.toSet());
		int noModelSize = noModel.size();
		subset.removeAll(noModel);
		
		//And how many concepts have we already matched to a template?
		int beforeRemoval = subset.size();
		subset.removeAll(alreadyCounted);
		int countedElsewhere = beforeRemoval - subset.size();
		
		Set<Concept> templateMatches = new HashSet<>();
		//Now lets see how many we can match to a template
		nextConcept:
		for (Concept c : subset) {
			for (Template t : templates) {
				if (TemplateUtils.matchesTemplate(c, t, this, CharacteristicType.INFERRED_RELATIONSHIP)) {
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
		report(PRIMARY_REPORT, ecl, topHierarchyText, subsetSize + " (" + subset.size() + " available)", noModelSize, outOfScope, countedElsewhere, templateMatches.size(), templatesConsidered);
		totalTemplateMatches += templateMatches.size();
	}
	
	/*private boolean containsTemplate(List<Template> templates, String templateName) {
		for (Template template : templates) {
			if (template.getName().equals(templateName)) {
				return true;
			}
		}
		return false;
	}*/

	protected boolean inModuleScope(Component c) {
		if (project.getKey().equals("MAIN")) {
			return true;
		}
		//Do we have a default module id ie for a managed service project?
		if (project.getMetadata() != null && project.getMetadata().getDefaultModuleId() != null) {
			return c.getModuleId().equals(project.getMetadata().getDefaultModuleId());
		}
		return true;
	}
	
}

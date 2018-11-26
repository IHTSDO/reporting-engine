package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.client.TemplateServiceClient;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.template.DescendentsCache;
import org.ihtsdo.termserver.scripting.template.TemplateUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.authoringtemplate.domain.ConceptTemplate;
import org.snomed.authoringtemplate.domain.logical.LogicalTemplate;
import org.snomed.otf.scheduler.domain.*;

/**
 * See https://confluence.ihtsdotools.org/display/IAP/Quality+Improvements+2018
 * Update: https://confluence.ihtsdotools.org/pages/viewpage.action?pageId=61155633
 */
public class TemplateCompliance extends TermServerReport implements ReportClass {
	
	final static String templateServiceUrl = "https://dev-authoring.ihtsdotools.org/template-service";
	TemplateServiceClient tsc;
	Map<String, List<Template>> domainTemplates = new HashMap<>();
	Set<Concept> alreadyCounted = new HashSet<>();

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		TermServerReport.run(TemplateCompliance.class, args, null);
	}
	

	@Override
	public Job getJob() {
		String[] parameterNames = new String[] {};
		return new Job( new JobCategory(JobType.REPORT, JobCategory.QI),
						"SNOMEDCT Template Compliance",
						"For every domain which has a known template, determine how many concepts comply to that template.",
						new JobParameters(parameterNames),
						Job.ProductionStatus.HIDEME);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		
		tsc = new TemplateServiceClient(templateServiceUrl, "dev-ims-ihtsdo=b3xnn4IWTRv0liESEcB3LA00");
		ReportSheetManager.targetFolderId = "1YoJa68WLAMPKG6h4_gZ5-QT974EU9ui6"; //QI / Stats
		additionalReportColumns = "Domain, SemTag, Hierarchy (Total), Total Concepts in Domain, OutOfScope - Domain, OutOfScope - Hierarchy, Counted Elsewhere, Template Compliant, Templates Considered";
		
		subHierarchyStr = "125605004";  // QI-5 |Fracture of bone (disorder)|
		String[] templateNames = new String[] {	"templates/fracture/Fracture of Bone Structure.json",
										"templates/fracture/Fracture Dislocation of Bone Structure.json",
										//"templates/fracture/Pathologic fracture of bone due to Disease.json",
										//"templates/fracture/Pathologic fracture morphology of bone structure co-occurrent and due to Neoplasm of bone.json",
										//"templates/fracture/Traumatic abnormality of spinal cord structure co-occurrent and due to fracture morphology of vertebral bone structure.json",
										//"Injury of finding site due to birth trauma.json"
										};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr =  "128294001";  // QI-9 |Chronic inflammatory disorder (disorder)
		templateNames = new String[] {"templates/Chronic Inflammatory Disorder.json"}; 
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr =  "126537000";  //QI-14 |Neoplasm of bone (disorder)|
		templateNames = new String[] {	"templates/Neoplasm of Bone.json",
										"templates/fracture/Pathologic fracture morphology of bone structure co-occurrent and due to Neoplasm of bone.json"};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr =  "34014006"; //QI-15 |Viral disease (disorder)|
		templateNames = new String[] {	"templates/Infection caused by Virus.json",
										"templates/Infection of bodysite caused by virus.json"};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "87628006";  //QI-16 |Bacterial infectious disease (disorder)|
		templateNames = new String[] {	"templates/Infection caused by Bacteria.json",
										"templates/Infection of bodysite caused by bacteria.json"};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "95896000";  //QI-19  |Protozoan infection (disorder)|
		templateNames = new String[] {"templates/Infection caused by Protozoa with optional bodysite.json"};
		populateTemplates(subHierarchyStr, templateNames);
			
		subHierarchyStr = "125666000";  //QI-33  |Burn (disorder)|
		templateNames = new String[] {
				"templates/burn/Burn of body structure.json",
				"templates/burn/Epidermal burn of body structure.json",
				"templates/burn/Partial thickness burn of body structure.json",
				"templates/burn/Full thickness burn of body structure.json",
				"templates/burn/Deep partial thickness burn of body structure.json",
				"templates/burn/Superficial partial thickness burn of body structure.json"};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "74627003";  //QI-48 |Diabetic Complication|
		templateNames = new String[] {	"templates/Complication co-occurrent and due to Diabetes Melitus.json",
										"templates/Complication co-occurrent and due to Diabetes Melitus - Minimal.json"};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "8098009";	// QI-45 |Sexually transmitted infectious disease (disorder)| 
		templateNames = new String[] {	"templates/Sexually transmitted Infection with optional bodysite.json"};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "283682007"; // QI-39 |Bite - wound (disorder)|
		templateNames = new String[] {	"templates/bite/bite of bodysite caused by bite event.json", 
										"templates/bite/bite of bodysite caused by bite event with infection.json"};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "3218000"; //QI-67 |Mycosis (disorder)|
		templateNames = new String[] {	"templates/Infection caused by Fungus.json"};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "17322007"; //QI-68 |Parasite (disorder)|
		templateNames = new String[] {	"templates/Infection caused by Parasite.json"};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "416886008"; //QI-106 |Closed wound| 
		templateNames = new String[] {	"templates/wound/wound of bodysite.json"
				//"templates/wound/closed wound of bodysite.json"
				};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "125643001"; //QI-107 |Open wound| 
		templateNames = new String[] {	"templates/wound/wound of bodysite.json"
				//"templates/wound/open wound of bodysite.json"
				};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "128545000"; //QI-75 |Hernia of abdominal wall (disorder)|
		//subHierarchyStr = "773623000";
		templateNames = new String[] {	"templates/Hernia of abdominal wall.json"};
		populateTemplates(subHierarchyStr, templateNames);
		populateTemplatesFromTS();
		super.init(run);
	}
	
	public void runJob() throws TermServerScriptException {
		
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
	
	private void populateTemplatesFromTS() throws TermServerScriptException {
		try {
			Character id = 'A';
			for (ConceptTemplate ct : tsc.getAllTemplates()) {
				String templateName = ct.getName();
				//Skip all LOINC
				if (templateName.toUpperCase().contains("LOINC") || templateName.toUpperCase().contains("OUTDATED")) {
					continue;
				}
				
				LogicalTemplate lt = tsc.parseLogicalTemplate(ct);
				Template template = new Template(id++, lt, templateName);
				//Have we seen this domain before?
				String domain = ct.getDomain().replaceAll("\\<", "");
				info ("Loading " + domain + " template " + templateName + " from TS");
				List<Template> templates = domainTemplates.get(domain);
				if (templates == null) {
					templates = new ArrayList<>();
					domainTemplates.put(domain, templates);
				}
				templates.add(template);
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to load templates from TS", e);
		}
		
	}

	private void populateTemplates(String domainStr, String[] templateNames) throws TermServerScriptException {
		
			List<Template> templates = new ArrayList<>();
			char id = 'A';
			for (int x = 0; x < templateNames.length; x++, id++) {
				try {
					LogicalTemplate lt = tsc.loadLogicalTemplate(templateNames[x]);
					templates.add(new Template(id, lt, templateNames[x]));
				} catch (Exception e) {
					throw new TermServerScriptException("Unable to load " + domainStr + " template " + templateNames[x] + " from local resources", e);
				}
			}
			domainTemplates.put(domainStr, templates);
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
		Set<Concept> subHierarchy = new HashSet<>(cache.getDescendentsOrSelf(domain));  //Clone as we need to modify
		int domainSize = subHierarchy.size();
		Concept topLevelConcept = SnomedUtils.getHighestAncestorBefore(domain, ROOT_CONCEPT);
		Set<Concept> topLevelHierarchy = cache.getDescendentsOrSelf(topLevelConcept);
		int topLevelHierarchySize = topLevelHierarchy.size();
		//How much of the top level hierarchy is out of scope?
		int outOfScope = topLevelHierarchy.stream()
				.filter(c -> countAttributes(c, CharacteristicType.INFERRED_RELATIONSHIP) == 0)
				.collect(Collectors.toSet()).size();
		
		
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

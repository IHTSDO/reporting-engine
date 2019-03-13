package org.ihtsdo.termserver.scripting.reports.qi;

import java.util.*;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TemplateServiceClient;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.Template;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.authoringtemplate.domain.ConceptTemplate;
import org.snomed.authoringtemplate.domain.logical.LogicalTemplate;
import org.snomed.otf.scheduler.domain.JobRun;

public abstract class AllKnownTemplates extends TermServerReport {
	
	TemplateServiceClient tsc;
	Map<String, List<Template>> domainTemplates = new HashMap<>();
	
	public static final String TEMPLATE_SERVICE = "Template Service";
	public static final String QI = "QI Project";

	public void init (JobRun run) throws TermServerScriptException {
		String templateServiceUrl = run.getMandatoryParamValue(SERVER_URL);
		tsc = new TemplateServiceClient(templateServiceUrl, run.getAuthToken());
		ReportSheetManager.targetFolderId = "1YoJa68WLAMPKG6h4_gZ5-QT974EU9ui6"; //QI / Stats
		
		subHierarchyStr = "125605004";  // QI-5 |Fracture of bone (disorder)|
		String[] templateNames = new String[] {	"templates/fracture/Fracture of Bone Structure.json",
										"templates/fracture/Fracture Dislocation of Bone Structure.json",
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
		templateNames = new String[] {	"templates/infection/Infection caused by Virus.json",
										"templates/infection/Infection of bodysite caused by virus.json"};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "87628006";  //QI-16 |Bacterial infectious disease (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by Bacteria.json",
										"templates/infection/Infection of bodysite caused by bacteria.json"};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "95896000";  //QI-19  |Protozoan infection (disorder)|
		templateNames = new String[] {"templates/infection/Infection caused by Protozoa with optional bodysite.json"};
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
		templateNames = new String[] {	"templates/Complication due to Diabetes Melitus.json"};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "8098009";	// QI-45 |Sexually transmitted infectious disease (disorder)| 
		templateNames = new String[] {	"templates/Sexually transmitted Infection with optional bodysite.json"};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "283682007"; // QI-39 |Bite - wound (disorder)|
		templateNames = new String[] {	"templates/bite/bite of bodysite caused by bite event.json", 
										"templates/bite/bite of bodysite caused by bite event with infection.json"};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "3218000"; //QI-67 |Mycosis (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by Fungus.json"};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "17322007"; //QI-68 |Parasite (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by Parasite.json"};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "416886008"; //QI-106 |Closed wound| 
		templateNames = new String[] {	"templates/wound/wound of bodysite.json" };
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "125643001"; //QI-107 |Open wound| 
		templateNames = new String[] {	"templates/wound/wound of bodysite.json" };
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "432119003 |Aneurysm (disorder)|"; //QI-143 
		templateNames = new String[] {	"templates/Aneurysm of Cardiovascular system.json" };
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "<<40733004|Infectious disease|"; //QI-153
		templateNames = new String[] {	"templates/infection/Infection NOS.json" };
		
		subHierarchyStr = "399963005 |Abrasion|"; //QI-147
		templateNames = new String[] {	"templates/wound/abrasion.json" ,
										"templates/Disorder due to birth trauma.json"};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "300935003"; //QI-147
		templateNames = new String[] {	"templates/Disorder due to birth trauma.json" };
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "52515009 |Hernia of abdominal cavity|"; //QI-172
		templateNames = new String[] {"templates/hernia/Hernia of Body Structure.json" };
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "312608009 |Laceration - injury|"; //QI-177
		templateNames = new String[] {	"templates/wound/laceration.json" };
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "3723001 |Arthritis (disorder)|"; //QI-123
		templateNames = new String[] {	"templates/Arthritis.json" };
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "428794004 |Fistula (disorder)|"; //QI-186
		templateNames = new String[] {	"templates/Fistula.json" };
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "441457006 |Cyst|"; //QI-181
		templateNames = new String[] {	"templates/Cyst.json" };
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "128477000 |Abscess (disorder)|"; //QI-213
		templateNames = new String[] {	"templates/Abscess.json",
										"templates/Abscess with Cellulitis.json"};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "416462003 |Wound (disorder)|"; //QI-209
		templateNames = new String[] {	"templates/wound/wound of bodysite.json",
										"templates/wound/wound of bodysite due to event.json"};
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "125670008 |Foreign body (disorder)|"; //QI-156
		templateNames = new String[] {	"templates/Foreign body.json" };
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "193570009 |Cataract (disorder)|"; //MQI-7
		templateNames = new String[] {	"templates/Cataract.json" };
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "429040005 |Ulcer (disorder)|"; //QI-248
		templateNames = new String[] {	"templates/Ulcer.json" };
		populateTemplates(subHierarchyStr, templateNames);
		
		subHierarchyStr = "109355002 |Carcinoma in situ (disorder)|"; //QI-231
		templateNames = new String[] {	"templates/Carcinoma in Situ.json" };
		populateTemplates(subHierarchyStr, templateNames);
		
		populateTemplatesFromTS();
		super.init(run);
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
				
				LogicalTemplate lt = tsc.parseLogicalTemplate(ct.getLogicalTemplate());
				Template template = new Template(id++, lt, templateName);
				//Have we seen this domain before?
				String domain = ct.getDomain().replaceAll("\\<", "");
				info ("Loading " + domain + " template " + templateName + " from TS");
				List<Template> templates = domainTemplates.get(domain);
				if (templates == null) {
					templates = new ArrayList<>();
					domainTemplates.put(domain, templates);
				}
				template.setSource(TEMPLATE_SERVICE);
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
				info ("Loading template: " + templateNames[x]);
				try {
					ConceptTemplate ct = tsc.loadLocalConceptTemplate(templateNames[x]);
					LogicalTemplate lt = tsc.parseLogicalTemplate(ct.getLogicalTemplate());
					Template template = new Template(id, lt, templateNames[x]);
					template.setSource(QI);
					template.setDocumentation(ct.getDocumentation());
					templates.add(template);
				} catch (Exception e) {
					throw new TermServerScriptException("Unable to load " + domainStr + " template " + templateNames[x] + " from local resources", e);
				}
			}
			domainTemplates.put(domainStr, templates);
	}
}

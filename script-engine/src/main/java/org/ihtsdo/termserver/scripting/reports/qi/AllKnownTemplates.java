package org.ihtsdo.termserver.scripting.reports.qi;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TemplateServiceClient;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Template;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.authoringtemplate.domain.ConceptTemplate;
import org.snomed.authoringtemplate.domain.logical.LogicalTemplate;
import org.snomed.otf.scheduler.domain.JobRun;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.apache.commons.lang.StringUtils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AllKnownTemplates extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(AllKnownTemplates.class);
	private Pattern sctidExtractorPattern;
	protected TemplateServiceClient tsc;
	protected Map<String, List<Template>> domainTemplates = new HashMap<>();
	protected boolean singleTemplateMode = false;

	public static final String TEMPLATE_SERVICE = "Template Service";
	public static final String QI = "QI Project";
	public static final String WOUND_OF_BODYSITE = "templates/wound/wound of bodysite.json";
	public static final String FINDING_OF_RANGE_OF_JOINT_MOVEMENT = "templates/Finding of range of joint movement.json";

	private Map<Template, Set<String>> templateToSctidMap;
	
	protected void commonInit(JobRun run, boolean singleTemplateMode) {
		this.singleTemplateMode = singleTemplateMode;
		String templateServiceUrl = run.getMandatoryParamValue(SERVER_URL);
		if (!templateServiceUrl.endsWith("/template-service")) {
			templateServiceUrl += "/template-service";
		}
		tsc = new TemplateServiceClient(templateServiceUrl, run.getAuthToken());
		sctidExtractorPattern = Pattern.compile("\\d{8,}");
		ReportSheetManager.setTargetFolderId(GFOLDER_QI_STATS);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		commonInit(run, singleTemplateMode);
		if (!singleTemplateMode) {
			populateTemplates("<< 125605004 |Fracture of bone (disorder)|",	"templates/fracture/Fracture of Bone Structure.json",
					"templates/fracture/Fracture Dislocation of Bone Structure.json"); // QI-5

			populateTemplates("<< 128294001 |Chronic inflammatory disorder (disorder)|",	"templates/Chronic Inflammatory Disorder.json"); // QI-9

			populateTemplates("<< 126537000 |Neoplasm of bone (disorder)|",	"templates/neoplasm/Neoplasm of Bone.json",
					"templates/fracture/Pathologic fracture morphology of bone structure co-occurrent and due to Neoplasm of bone.json"); //QI-14

			populateTemplates("<< 34014006","templates/infection/Infection caused by Virus.json",
					"templates/infection/Infection of bodysite caused by virus.json"); //QI-15 |Viral disease (disorder)|

			populateTemplates("<< 87628006","templates/infection/Infection caused by bacteria.json",
					"templates/infection/Infection of bodysite caused by bacteria.json"); //QI-16 |Bacterial infectious disease (disorder)|

			populateTemplates("<< 95896000","templates/infection/Infection caused by Protozoa with optional bodysite.json"); //QI-19  |Protozoan infection (disorder)|

			populateTemplates("<< 125666000 |Burn (disorder)|",	"templates/burn/Burn of body structure.json",
					"templates/burn/Epidermal burn of body structure.json",
					"templates/burn/Partial thickness burn of body structure.json",
					"templates/burn/Full thickness burn of body structure.json",
					"templates/burn/Deep partial thickness burn of body structure.json",
					"templates/burn/Superficial partial thickness burn of body structure.json"); //QI-33

			populateTemplates("<< 74627003 |Diabetic Complication|",	"templates/Complication due to Diabetes Melitus.json"); //QI-48

			populateTemplates("<< 8098009 |Sexually transmitted infectious disease (disorder)|",		"templates/Sexually transmitted Infection with optional bodysite.json"); // QI-45


			populateTemplates("<< 283682007 |Bite - wound (disorder)|",	"templates/bite/bite of bodysite caused by bite event.json",
					"templates/bite/bite of bodysite caused by bite event with infection.json"); // QI-39

			populateTemplates("<< 3218000 |Mycosis (disorder)|", "templates/infection/Infection caused by Fungus.json");  //QI-67

			populateTemplates("<< 17322007 |Parasite (disorder)|", "templates/infection/Infection caused by Parasite.json");  //QI-68

			populateTemplates("<< 416886008 |Closed wound|", WOUND_OF_BODYSITE );  //QI-106

			populateTemplates("<< 125643001 |Open wound| ", WOUND_OF_BODYSITE ); //QI-107

			populateTemplates("<< 432119003 |Aneurysm (disorder)|" , "templates/Aneurysm of Cardiovascular system.json" ); //QI-143

			populateTemplates("<< 40733004 |Infectious disease|", "templates/infection/Infection NOS.json"); //QI-153

			populateTemplates("<< 399963005 |Abrasion|", "templates/wound/abrasion.json" ,
					"templates/Disorder due to birth trauma.json"); //QI-147

			populateTemplates("<< 300935003", "templates/Disorder due to birth trauma.json" );  //QI-147

			populateTemplates("<< 52515009 |Hernia of abdominal cavity|" , "templates/hernia/Hernia of Body Structure.json" ); //QI-172

			populateTemplates("<< 312608009 |Laceration - injury|" , "templates/wound/laceration.json" ); //QI-177

			populateTemplates("<< 3723001 |Arthritis (disorder)|" , "templates/Arthritis.json" ); //QI-123

			populateTemplates("<< 428794004 |Fistula (disorder)|" , "templates/Fistula.json" ); //QI-186

			populateTemplates("<< 441457006 |Cyst|" , "templates/Cyst.json" ); //QI-181

			populateTemplates("<< 128477000 |Abscess (disorder)|", "templates/Abscess.json",
					"templates/Abscess with Cellulitis.json"); //QI-213

			populateTemplates("<< 416462003 |Wound (disorder)|", WOUND_OF_BODYSITE,
					"templates/wound/wound of bodysite due to event.json"); //QI-209

			populateTemplates("<< 125670008 |Foreign body (disorder)|" , 	"templates/Foreign body.json" ); //QI-156

			populateTemplates("<< 193570009 |Cataract (disorder)" , 	"templates/Cataract.json"); //MQI-7

			populateTemplates("<< 429040005 |Ulcer (disorder)|" , 	"templates/Ulcer.json" ); //QI-248

			populateTemplates("<< 109355002 |Carcinoma in situ (disorder)|" , 	"templates/neoplasm/Carcinoma in Situ.json" ); //QI-231

			populateTemplates("<< 247441003 |Erythema|" , 	"templates/Erythema of body structure.json" ); //QI-240

			populateTemplates("<< 445505000 |Contracture of joint of spine (disorder)|", FINDING_OF_RANGE_OF_JOINT_MOVEMENT ); //QI-???

			populateTemplates("<< 7890003 |Contracture of joint (disorder)|", 	"templates/Contracture of joint minus.json" ); //QI-261

			populateTemplates("<< 125667009 |Contusion (disorder)|", "templates/wound/contusion.json"); //QI-244

			populateTemplates("<< 85828009 |Autoimmune disease (disorder)|", 	"templates/Autoimune.json" ); //QI-297

			populateTemplates("<< 298180004 |Finding of range of joint movement (finding)|  MINUS <<  7890003 |Contracture of joint (disorder)|", FINDING_OF_RANGE_OF_JOINT_MOVEMENT ); //QI-???

			populateTemplates("<< 417893002|Deformity|", "templates/Deformity - disorder.json",
					"templates/Deformity - finding.json"); //QI-278

			populateTemplates("<< 118616009 |Neoplastic disease of uncertain behavior (disorder)|", "templates/neoplasm/Neoplastic Disease.json"); //QI-253

			populateTemplates("<< 20376005 |Benign neoplastic disease|", 	"templates/neoplasm/Benign Neoplastic Disease.json"); //QI-272

			populateTemplates("<< 233776003 |Tracheobronchial disorder|", 	"templates/Tracheobronchial.json" ); //QI-268

			populateTemplates("<< 298180004 |Finding of range of joint movement (finding)| MINUS << 7890003 |Contracture of joint (disorder)|", FINDING_OF_RANGE_OF_JOINT_MOVEMENT ); //QI-284

			populateTemplates("<< 400006008 |Hamartoma (disorder)|", 	"templates/Harmartoma.json" ); //QI-296

			populateTemplates("<<362975008 |Degenerative disorder (disorder)|: 116676008 |Associated morphology (attribute)| = << 32693004 |Demyelination (morphologic abnormality)|", "templates/Degenerative disorder.json"); //QI-331

			populateTemplates("<< 362975008 |Degenerative disorder (disorder)|: 116676008 |Associated morphology (attribute)| = << 46595003 |Deposition (morphologic abnormality)| ", "templates/Degenerative disorder.json"); //QI-373, QI-376, QI-400, QI-324, QI-337

			populateTemplates("<< 131148009|Bleeding|",  "templates/Bleeding - disorder.json"); //QI-319

			populateTemplates("<< 131148009|Bleeding|",  "templates/Bleeding - finding.json"); //QI-319

			populateTemplates("<< 74627003 | Diabetic complication (disorder) |", "templates/Complication due to Diabetes Melitus.json"); //QI-426

			populateTemplates(null, "templates/Adverse Reaction.json");

			populateTemplates("<< 128462008 |Secondary malignant neoplastic disease (disorder)|", 	"templates/Secondary malignant neoplasm.json"); //QI-382

			populateTemplates("<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 125671007 |Rupture (morphologic abnormality)|", 	"templates/Traumatic rupture of joint.json"); //QI-498

			populateTemplates("<<  64572001 |Disease (disorder)|  :  116676008 |Associated morphology (attribute)|  = <<  708528005 |Narrowing (morphologic abnormality)|", 	"templates/morphologies/Narrowing.json"); //QI-507

			populateTemplates("<< 64572001 |Disease (disorder)|  :  116676008 |Associated morphology (attribute)|  = <<  79644001 |Pigment alteration (morphologic abnormality)|", 	"templates/Pigmentation.json"); //QI-518

			populateTemplates("<< 64572001 |Disease (disorder)|  :  116676008 |Associated morphology (attribute)|  = <<  68790008 |Amyloid deposition (morphologic abnormality)|", 	"templates/Amyloid.json"); //QI-225

			populateTemplates("<<  64572001 |Disease (disorder)|  :  116676008 |Associated morphology (attribute)|  = <<  6574001 |Necrosis (morphologic abnormality)|", 	"templates/Necrosis.json"); //QI-530

			populateTemplates("<< 372087000 |Primary malignant neoplasm (disorder)|", 	"templates/neoplasm/primary malignant neoplasm.json"); //QI-383

			populateTemplates("<< 64572001 |Disease (disorder)|  :  116676008 |Associated morphology (attribute)| = << 107666005 |Fluid disturbance (morphologic abnormality)|", 	"templates/morphologies/Fluid disturbance.json"); //QI-525

			populateTemplates("<< 419199007 |Allergy to substance (disorder)|", "templates/Allergy to Substance.json"); //QI-609

			populateTemplates("<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 449735000 |Structural change due to ischemia (morphologic abnormality)|", 	"templates/morphologies/Structural change due to ischemia.json"); //QI-544

			populateTemplates("<< 404684003 |Clinical finding (finding)| : 116676008 |Associated morphology (attribute)| = << 112639008 |Protrusion (morphologic abnormality)|", 	"templates/morphologies/Protrusion.json"); //QI-556

			populateTemplates("< 128139000 |Inflammatory disorder (disorder)| ", "templates/inflammatory/General inflammatory disorder.json" ); //QI-619

			populateTemplates("<< 363346000 |Malignant neoplastic disease (disorder)|  MINUS (<< 372087000 |Primary malignant neoplasm (disorder)|  OR <<  128462008 |Secondary malignant neoplastic disease (disorder)| ) ", 	"templates/neoplasm/Malignant Neoplasm.json");

			populateTemplates("<< 64229006 |Traumatic lesion during delivery (disorder)| ", "templates/Traumatic lesion.json" ); //QI-631

			populateTemplates("<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 41010001 |Maturation defect (morphologic abnormality)|",  "templates/morphologies/Maturation defect.json" ); //QI-565

			populateTemplates("<<404684003 |Clinical finding (finding)| : 116676008 |Associated morphology (attribute)| = << 25723000 |Dysplasia (morphologic abnormality)|",  "templates/morphologies/Dysplasia.json" ); //QI-???

			populateTemplates("(<<404684003 |Clinical finding (finding)| : 116676008 |Associated morphology (attribute)| = ( << 4147007 |Mass (morphologic abnormality)| MINUS <<416939005 |Proliferative mass (morphologic abnormality)| ) )",  "templates/morphologies/Mass.json");

			populateTemplates("<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)|  = <<  30217000 |Proliferation (morphologic abnormality)|",  "templates/morphologies/Proliferation.json" ); //QI-???

			populateTemplates("<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 76197007 |Hyperplasia (morphologic abnormality)|",  "templates/morphologies/Hyperplasia.json" ); //QI-???

			populateTemplates("<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 56246009 |Hypertrophy (morphologic abnormality)|",  "templates/morphologies/Hypertrophy.json" ); //QI-???

			populateTemplates("<< 55342001 |Neoplastic disease (disorder)|",  "templates/neoplasm/Neoplastic Disease.json" ); //QI-???

			populateTemplates("<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 308492005 |Contusion - lesion (morphologic abnormality)|",  "templates/morphologies/Contusion.json" ); //QI-???

			populateTemplates("<< 40275004 |Contact dermatitis (disorder)|",  "templates/Contact Dermatitis.json" ); //QI-???

			populateTemplates("< 238575004 |Allergic contact dermatitis (disorder)|",  "templates/Allergic Contact Dermatitis.json" ); //QI-???

			populateTemplates("<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 31883006 |Fused structure (morphologic abnormality)|",  "templates/morphologies/Fused.json" ); //QI-???

			populateTemplates("<< 66091009 |Congenital disease (disorder)|| : 116676008 |Associated morphology (attribute)| = << 399984000 |Abnormal shape (morphologic abnormality)|",  "templates/morphologies/Abnormal Shape.json" ); //QI-???

			populateTemplates("(<< 404684003 |Clinical finding (finding)| : 116676008 |Associated morphology (attribute)| = << 1806006 |Eruption (morphologic abnormality)|) MINUS (<< 64572001 |Disease (disorder)|)",  "templates/morphologies/Eruption - finding.json"); //QI-???

			populateTemplates("<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 1806006 |Eruption (morphologic abnormality)|",  "templates/morphologies/Eruption - disorder.json" ); //QI-???

			populateTemplates("<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 36191001 |Perforation (morphologic abnormality)|",  "templates/morphologies/Perforation.json" ); //QI-???

			populateTemplates("(<< 404684003 |Clinical finding (finding)| : 116676008 |Associated morphology (attribute)| = << 107682006 |Fibrosis AND/OR repair abnormality | ) MINUS ( << 64572001 |Disease (disorder)| )", "templates/morphologies/Fibrosis - finding.json"); //QI-???

			populateTemplates("<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 107682006 |Fibrosis AND/OR repair abnormality |  ",  "templates/morphologies/Fibrosis - disorder.json" ); //QI-???

			populateTemplates("<< 371136004 |Disorder of tooth development (disorder)| ",  "templates/pathological process/Developmental.json" ); //QI-???

			populateTemplates("(<< 371521007 |Disorder of bone development (disorder)|) MINUS ( << 19579005 |Juvenile osteochondritis (disorder)| )",  "templates/pathological process/Developmental.json" ); //QI-???

			populateTemplates("<<  609520005 |Disorder of fetal structure (disorder)|",  "templates/Disorder of fetal structure.json" ); //QI-???

			populateTemplates("<<  40445007 |Heart valve regurgitation (disorder)|",  "templates/Heart valve insufficiency.json" ); //QI-???

			populateTemplates("<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 76093008 |Anterior displacement (morphologic abnormality)|", "templates/morphologies/Anterior displacement.json");

			populateTemplates(null, "templates/Traumatic injury.json");

			populateTemplates(null, "templates/morphologies/Posterior displacement.json");

			populateTemplates(null, "templates/morphologies/Separation.json");

			populateTemplates(null, "templates/morphologies/Retention.json");

			populateTemplates(null, "templates/procedures/MRI.json");

			populateTemplates(null, "templates/morphologies/Calculus.json");

			populateTemplates(null, "templates/morphologies/Lateral displacement.json");

			populateTemplates(null, "templates/morphologies/Medial displacement.json");

			populateTemplates(null, "templates/Poisoning caused by substance or product.json");

			populateTemplates(null, "templates/morphologies/Compression.json");

			populateTemplates(null, "templates/morphologies/Enlargement.json" );

			populateTemplates(null, "templates/drugs/MP only.json",
					"templates/drugs/MP containing.json",
					"templates/drugs/MPF containing.json",
					"templates/drugs/MPF only.json",
					"templates/drugs/CD precise discrete.json",
					"templates/drugs/CD precise continuous.json");

			populateTemplates(null, "templates/procedures/CT Guided.json");

			populateTemplates(null, "templates/morphologies/Misalignment.json");

			populateTemplates(null, "templates/morphologies/Generic Morphologies.json");

			populateTemplates(null, "templates/morphologies/Recession CF.json");

			populateTemplates(null, "templates/morphologies/Recession.json");

			populateTemplates(null, "templates/Poisoning.json");

			populateTemplates(null, "templates/procedures/Stoma.json");

			populateTemplates(null, "templates/procedures/CT of Body Structure with contrast.json");

			populateTemplates(null, "templates/procedures/CT of Body Structure.json");

			populateTemplates(null, "templates/procedures/CT Guided.json");

			populateTemplates(null, "templates/Muscular dystrophy.json");

			populateTemplates(null, "templates/Craniofacial cleft.json");

			populateTemplates(null, "templates/Measurement.json");

			populateTemplates(null, "templates/procedures/Anthrotomy.json");

			populateTemplates(null, "templates/procedures/Angiography.json");

			populateTemplates(null, "templates/Allergic Disease.json");

			populateTemplates(null, "templates/procedures/Needle Biopsy.json");

			populateTemplates(null, "templates/procedures/Imaging Guided Biopsy.json");

			populateTemplates(null, "templates/procedures/Excision of cyst.json");

			populateTemplates(null, "templates/Substance abuse.json");

			populateTemplates(null, "templates/morphologies/Malposition.json");

			populateTemplates(null, "templates/Drug dependence.json");

			populateTemplates(null, "templates/morphologies/Dislocation.json");

			populateTemplates(null, "templates/procedures/InsertionOfStent.json");

			populateTemplates(null, "templates/Toxic due to plant.json");

			populateTemplates(null, "templates/procedures/Intubation.json");

			populateTemplates(null, "templates/finding/Measurement Finding.json");

			populateTemplates(null, "templates/procedures/Endoscopy.json");

			populateTemplates(null, "templates/procedures/Intubation.json");

			populateTemplates(null, "templates/procedures/Periodontal.json");
			populateTemplates(null,"templates/finding/Measurement Finding.json");
			populateTemplates(null,"templates/procedures/Endoscopy.json");
			populateTemplates(null,"templates/procedures/Periodontal.json");
			populateTemplates(null,"templates/procedures/Exteriorization.json");
			populateTemplates(null,"templates/procedures/Construction of stoma.json");
			populateTemplates(null,"templates/procedures/Radiotherapy.json");
			populateTemplates(null,"templates/procedures/Drainage.json");



			//Do this one last to pick up whatever is left under Disease
			populateTemplates("<< 64572001 |Disease (disorder)|", "templates/Disease.json");
			
			populateTemplatesFromTS();
		}
		super.init(run);
	}
	
	public void populateTemplateFromTS(String templateName) throws TermServerScriptException {
		try {
			populateTemplate('A', tsc.loadLogicalTemplate(templateName));
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to load template " + templateName + " from TS", e);
		}
	}
	
	private void populateTemplatesFromTS() throws TermServerScriptException {
			Character id = 'A';
			for (ConceptTemplate ct : tsc.getAllTemplates()) {
				populateTemplate(id++, ct);
			}
	}

	private void populateTemplate(char id, ConceptTemplate ct) throws TermServerScriptException {
		try {
			String templateName = ct.getName();
			//Skip all LOINC
			if (templateName.toUpperCase().contains("LOINC") || templateName.toUpperCase().contains("OUTDATED")) {
				return;
			}
			
			LogicalTemplate lt = tsc.parseLogicalTemplate(ct.getLogicalTemplate());
			Template template = new Template(id, lt, templateName);
			LOGGER.info("Loading template {} from TS to run against subset: {}", templateName, ct.getDomain());
			
			if (ct.getDomain() == null) {
				LOGGER.warn("TS template {} does not say what domain it applies to", templateName);
			}
			//Have we seen this subset before?
			List<Template> templates = domainTemplates.computeIfAbsent(ct.getDomain(), k -> new ArrayList<>());
			template.setSource(TEMPLATE_SERVICE);
			templates.add(template);
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to load template " + ct + " from TS", e);
		}
	}

	private void populateTemplates(String ecl, String... templateNames) throws TermServerScriptException {
			char id = 'A';
			for (int x = 0; x < templateNames.length; x++, id++) {
				LOGGER.info("Loading template: {}", templateNames[x]);
				try {
					ConceptTemplate ct = tsc.loadLocalConceptTemplate(templateNames[x]);
					LogicalTemplate lt = tsc.parseLogicalTemplate(ct.getLogicalTemplate());
					Template template = new Template(id, lt, templateNames[x]);
					template.setSource(QI);
					template.setDocumentation(ct.getDocumentation());
					template.setDomain(ct.getDomain());
					
					//Is the ECL part of the template?
					if (StringUtils.isEmpty(template.getDomain())) {
						template.setDomain(ecl);
					}
					
					List<Template> templates = domainTemplates.get(template.getDomain());
					if (templates == null) {
						templates = new ArrayList<>();
						domainTemplates.put(template.getDomain(), templates);
					}
					templates.add(template);
				} catch (Exception e) {
					throw new TermServerScriptException("Unable to load " + ecl + " template " + templateNames[x] + " from local resources", e);
				}
			}
	}
	
	public List<Template> listTemplatesUsingConcept (Concept c) {
		List<Template> templatesUsingConcept = new ArrayList<>();
		//To save us parsing each template for each concept checked, we'll 
		//set up a map of templates to SCTIDs
		for (Map.Entry<Template, Set<String>> entry : getTemplateToSctidMap().entrySet()) {
			Set<String> sctidsUsedInTemplate = entry.getValue();
			if (sctidsUsedInTemplate.contains(c.getId())) {
				templatesUsingConcept.add(entry.getKey());
			}
		}
		return templatesUsingConcept;
	}

	private Map<Template, Set<String>> getTemplateToSctidMap() {
		if (templateToSctidMap == null) {
			templateToSctidMap = new HashMap<>();
			for (Map.Entry<String, List<Template>> entry : domainTemplates.entrySet()) {
				Set<String> idsUsedInDomain = extractSctids(entry.getKey());
				for (Template t : entry.getValue()) {
					Set<String> sctidsUsedInTemplate = extractSctids(t.getSource());
					//Add those used in the domain as well
					sctidsUsedInTemplate.addAll(idsUsedInDomain);
					templateToSctidMap.put(t, sctidsUsedInTemplate);
				}
			}
		}
		return templateToSctidMap;
	}

	private Set<String> extractSctids(String source) {
		Set<String> sctIds = new HashSet<>();
		Matcher matcher = sctidExtractorPattern.matcher(source);
		
		// Find and add each matching number to the result set
		while (matcher.find()) {
			sctIds.add(matcher.group());
		}
		return sctIds;
	}
}

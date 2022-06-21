package org.ihtsdo.termserver.scripting.reports.qi;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TemplateServiceClient;
import org.ihtsdo.termserver.scripting.domain.Template;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.authoringtemplate.domain.ConceptTemplate;
import org.snomed.authoringtemplate.domain.logical.LogicalTemplate;
import org.snomed.otf.scheduler.domain.JobRun;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.springframework.util.StringUtils;

public abstract class AllKnownTemplates extends TermServerReport {
	
	TemplateServiceClient tsc;
	Map<String, List<Template>> domainTemplates = new HashMap<>();
	
	public static final String TEMPLATE_SERVICE = "Template Service";
	public static final String QI = "QI Project";

	public void init (JobRun run) throws TermServerScriptException {
		String templateServiceUrl = run.getMandatoryParamValue(SERVER_URL);
		if (!templateServiceUrl.endsWith("/template-service")) {
			templateServiceUrl += "/template-service";
		}
		tsc = new TemplateServiceClient(templateServiceUrl, run.getAuthToken());
		ReportSheetManager.targetFolderId = "1YoJa68WLAMPKG6h4_gZ5-QT974EU9ui6"; //QI / Stats
		
		String subsetECL = "<< 125605004";  // QI-5 |Fracture of bone (disorder)|
		String[] templateNames = new String[] {	"templates/fracture/Fracture of Bone Structure.json",
										"templates/fracture/Fracture Dislocation of Bone Structure.json"};
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 128294001";  // QI-9 |Chronic inflammatory disorder (disorder)
		templateNames = new String[] {"templates/Chronic Inflammatory Disorder.json"}; 
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 126537000";  //QI-14 |Neoplasm of bone (disorder)|
		templateNames = new String[] {	"templates/neoplasm/Neoplasm of Bone.json",
										"templates/fracture/Pathologic fracture morphology of bone structure co-occurrent and due to Neoplasm of bone.json"};
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 34014006"; //QI-15 |Viral disease (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by Virus.json",
										"templates/infection/Infection of bodysite caused by virus.json"};
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 87628006";  //QI-16 |Bacterial infectious disease (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by bacteria.json",
										"templates/infection/Infection of bodysite caused by bacteria.json"};
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 95896000";  //QI-19  |Protozoan infection (disorder)|
		templateNames = new String[] {"templates/infection/Infection caused by Protozoa with optional bodysite.json"};
		populateTemplates(subsetECL, templateNames);
			
		subsetECL = "<< 125666000";  //QI-33  |Burn (disorder)|
		templateNames = new String[] {
				"templates/burn/Burn of body structure.json",
				"templates/burn/Epidermal burn of body structure.json",
				"templates/burn/Partial thickness burn of body structure.json",
				"templates/burn/Full thickness burn of body structure.json",
				"templates/burn/Deep partial thickness burn of body structure.json",
				"templates/burn/Superficial partial thickness burn of body structure.json"};
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 74627003";  //QI-48 |Diabetic Complication|
		templateNames = new String[] {	"templates/Complication due to Diabetes Melitus.json"};
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 8098009";	// QI-45 |Sexually transmitted infectious disease (disorder)| 
		templateNames = new String[] {	"templates/Sexually transmitted Infection with optional bodysite.json"};
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 283682007"; // QI-39 |Bite - wound (disorder)|
		templateNames = new String[] {	"templates/bite/bite of bodysite caused by bite event.json", 
										"templates/bite/bite of bodysite caused by bite event with infection.json"};
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 3218000"; //QI-67 |Mycosis (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by Fungus.json"};
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 17322007"; //QI-68 |Parasite (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by Parasite.json"};
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 416886008"; //QI-106 |Closed wound| 
		templateNames = new String[] {	"templates/wound/wound of bodysite.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 125643001"; //QI-107 |Open wound| 
		templateNames = new String[] {	"templates/wound/wound of bodysite.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 432119003 |Aneurysm (disorder)|"; //QI-143 
		templateNames = new String[] {	"templates/Aneurysm of Cardiovascular system.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< <<40733004|Infectious disease|"; //QI-153
		templateNames = new String[] {	"templates/infection/Infection NOS.json" };
		
		subsetECL = "<< 399963005 |Abrasion|"; //QI-147
		templateNames = new String[] {	"templates/wound/abrasion.json" ,
										"templates/Disorder due to birth trauma.json"};
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 300935003"; //QI-147
		templateNames = new String[] {	"templates/Disorder due to birth trauma.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 52515009 |Hernia of abdominal cavity|"; //QI-172
		templateNames = new String[] {"templates/hernia/Hernia of Body Structure.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 312608009 |Laceration - injury|"; //QI-177
		templateNames = new String[] {	"templates/wound/laceration.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 3723001 |Arthritis (disorder)|"; //QI-123
		templateNames = new String[] {	"templates/Arthritis.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 428794004 |Fistula (disorder)|"; //QI-186
		templateNames = new String[] {	"templates/Fistula.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 441457006 |Cyst|"; //QI-181
		templateNames = new String[] {	"templates/Cyst.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 128477000 |Abscess (disorder)|"; //QI-213
		templateNames = new String[] {	"templates/Abscess.json",
										"templates/Abscess with Cellulitis.json"};
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 416462003 |Wound (disorder)|"; //QI-209
		templateNames = new String[] {	"templates/wound/wound of bodysite.json",
										"templates/wound/wound of bodysite due to event.json"};
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 125670008 |Foreign body (disorder)|"; //QI-156
		templateNames = new String[] {	"templates/Foreign body.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 193570009 |Cataract (disorder)|"; //MQI-7
		templateNames = new String[] {	"templates/Cataract.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 429040005 |Ulcer (disorder)|"; //QI-248
		templateNames = new String[] {	"templates/Ulcer.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 109355002 |Carcinoma in situ (disorder)|"; //QI-231
		templateNames = new String[] {	"templates/neoplasm/Carcinoma in Situ.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 247441003 |Erythema|"; //QI-240
		templateNames = new String[] {	"templates/Erythema of body structure.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 445505000 |Contracture of joint of spine (disorder)|";
		templateNames = new String[] {	"templates/Finding of range of joint movement.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 7890003 |Contracture of joint (disorder)|"; //QI-261
		templateNames = new String[] {	"templates/Contracture of joint minus.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 125667009 |Contusion (disorder)|"; //QI-244 
		templateNames = new String[] {	"templates/wound/contusion.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 85828009 |Autoimmune disease (disorder)|"; //QI-297
		templateNames = new String[] {	"templates/Autoimune.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 298180004 |Finding of range of joint movement (finding)|  MINUS <<  7890003 |Contracture of joint (disorder)|";
		templateNames = new String[] {	"templates/Finding of range of joint movement.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 417893002|Deformity|"; //QI-278
		templateNames = new String[] {	"templates/Deformity - disorder.json",
				"templates/Deformity - finding.json"};
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 118616009 |Neoplastic disease of uncertain behavior (disorder)|"; //QI-253 |Neoplastic disease of uncertain behavior| 
		templateNames = new String[] {	"templates/neoplasm/Neoplastic Disease.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 20376005 |Benign neoplastic disease|"; //QI-272
		templateNames = new String[] {	"templates/neoplasm/Benign Neoplastic Disease.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 233776003 |Tracheobronchial disorder|"; //QI-268
		templateNames = new String[] {	"templates/Tracheobronchial.json" };
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 298180004 |Finding of range of joint movement (finding)| MINUS << 7890003 |Contracture of joint (disorder)|"; //QI-284
		templateNames = new String[] {	"templates/Finding of range of joint movement.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 400006008 |Hamartoma (disorder)|"; //QI-296
		templateNames = new String[] {	"templates/Harmartoma.json" };
		populateTemplates(subsetECL, templateNames);
		
		//QI-331, QI-353, QI-352, QI-329
		subsetECL = "<<362975008 |Degenerative disorder (disorder)|: 116676008 |Associated morphology (attribute)| = << 32693004 |Demyelination (morphologic abnormality)|";
		templateNames = new String[] {	"templates/Degenerative disorder.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 417893002|Deformity|"; //QI-279
		templateNames = new String[] {	"templates/Deformity - finding.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 417893002|Deformity|"; //QI-279
		templateNames = new String[] {	"templates/Deformity - disorder.json"};
		populateTemplates(subsetECL, templateNames);

		//QI-373, QI-376, QI-400, QI-324, QI-337
		subsetECL = "<<362975008 |Degenerative disorder (disorder)|: 116676008 |Associated morphology (attribute)| = << 46595003 |Deposition (morphologic abnormality)| ";
		templateNames = new String[] {	"templates/Degenerative disorder.json"};
		populateTemplates(subsetECL, templateNames);

		//Replaced with production template Congenital [morphology] of [body structure] 
		/*subsetECL = "<< 276654001 |Congenital malformation (disorder)|"; //QI-287
		templateNames = new String[] {	"templates/Congenital Malformation.json"};
		populateTemplates(subsetECL, templateNames);*/
		
		subsetECL = "<< 131148009|Bleeding|"; //QI-319
		templateNames = new String[] { "templates/Bleeding - disorder.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 131148009|Bleeding|"; //QI-319
		templateNames = new String[] { "templates/Bleeding - finding.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 74627003 | Diabetic complication (disorder) |"; //QI-426
		templateNames = new String[] {	"templates/Complication due to Diabetes Melitus.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<<  282100009 |Adverse reaction caused by substance (disorder)|"; //QI-406
		templateNames = new String[] {	"templates/Adverse Reaction.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 128462008 |Secondary malignant neoplastic disease (disorder)|"; //QI-382
		templateNames = new String[] {	"templates/Secondary malignant neoplasm.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 125671007 |Rupture (morphologic abnormality)|"; //QI-498
		templateNames = new String[] {	"templates/Traumatic rupture of joint.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<<  64572001 |Disease (disorder)|  :  116676008 |Associated morphology (attribute)|  = <<  708528005 |Narrowing (morphologic abnormality)|"; //QI-507
		templateNames = new String[] {	"templates/morphologies/Narrowing.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 64572001 |Disease (disorder)|  :  116676008 |Associated morphology (attribute)|  = <<  79644001 |Pigment alteration (morphologic abnormality)|"; //QI-518
		templateNames = new String[] {	"templates/Pigmentation.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 64572001 |Disease (disorder)|  :  116676008 |Associated morphology (attribute)|  = <<  68790008 |Amyloid deposition (morphologic abnormality)|"; //QI-225
		templateNames = new String[] {	"templates/Amyloid.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<<  64572001 |Disease (disorder)|  :  116676008 |Associated morphology (attribute)|  = <<  6574001 |Necrosis (morphologic abnormality)|"; //QI-530
		templateNames = new String[] {	"templates/Necrosis.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 372087000 |Primary malignant neoplasm (disorder)|"; //QI-383
		templateNames = new String[] {	"templates/neoplasm/primary malignant neoplasm.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 64572001 |Disease (disorder)|  :  116676008 |Associated morphology (attribute)| = << 107666005 |Fluid disturbance (morphologic abnormality)|"; //QI-525
		templateNames = new String[] {	"templates/morphologies/Fluid disturbance.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 282100009 |Adverse reaction caused by substance (disorder)|";
		templateNames = new String[] {	"templates/Adverse Reaction.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 419199007 |Allergy to substance (disorder)|";  //QI-609
		templateNames = new String[] {	"templates/Allergy to Substance.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 449735000 |Structural change due to ischemia (morphologic abnormality)|"; //QI-544
		templateNames = new String[] {	"templates/morphologies/Structural change due to ischemia.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 404684003 |Clinical finding (finding)| : 116676008 |Associated morphology (attribute)| = << 112639008 |Protrusion (morphologic abnormality)|"; //QI-556
		templateNames = new String[] {	"templates/morphologies/Protrusion.json"};
		populateTemplates(subsetECL, templateNames);

		subsetECL = "< 128139000 |Inflammatory disorder (disorder)| "; //QI-619
		templateNames = new String[] {	"templates/inflammatory/General inflammatory disorder.json" };
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 363346000 |Malignant neoplastic disease (disorder)|  MINUS (<< 372087000 |Primary malignant neoplasm (disorder)|  OR <<  128462008 |Secondary malignant neoplastic disease (disorder)| ) "; //QI-387
		templateNames = new String[] {	"templates/neoplasm/Malignant Neoplasm.json" };
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 64229006 |Traumatic lesion during delivery (disorder)| "; //QI-631
		templateNames = new String[] {	"templates/Traumatic lesion.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 41010001 |Maturation defect (morphologic abnormality)|"; //QI-565
		templateNames = new String[] { "templates/morphologies/Maturation defect.json" };
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<<404684003 |Clinical finding (finding)| : 116676008 |Associated morphology (attribute)| = << 25723000 |Dysplasia (morphologic abnormality)|";
		templateNames = new String[] { "templates/morphologies/Dysplasia.json" };
		populateTemplates(subsetECL, templateNames);

		subsetECL = "(<<404684003 |Clinical finding (finding)| : 116676008 |Associated morphology (attribute)| = ( << 4147007 |Mass (morphologic abnormality)| MINUS <<416939005 |Proliferative mass (morphologic abnormality)| ) )";
		templateNames = new String[] { "templates/morphologies/Mass.json" };
		populateTemplates(subsetECL, templateNames);

		subsetECL = "<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)|  = <<  30217000 |Proliferation (morphologic abnormality)|";
		templateNames = new String[] { "templates/morphologies/Proliferation.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 76197007 |Hyperplasia (morphologic abnormality)|";
		templateNames = new String[] { "templates/morphologies/Hyperplasia.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 56246009 |Hypertrophy (morphologic abnormality)|";
		templateNames = new String[] { "templates/morphologies/Hypertrophy.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 55342001 |Neoplastic disease (disorder)|";
		templateNames = new String[] { "templates/neoplasm/Neoplastic Disease.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 308492005 |Contusion - lesion (morphologic abnormality)|";
		templateNames = new String[] { "templates/morphologies/Contusion.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 40275004 |Contact dermatitis (disorder)|";
		templateNames = new String[] { "templates/Contact Dermatitis.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "< 238575004 |Allergic contact dermatitis (disorder)|";
		templateNames = new String[] { "templates/Allergic Contact Dermatitis.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 31883006 |Fused structure (morphologic abnormality)|";
		templateNames = new String[] { "templates/morphologies/Fused.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 66091009 |Congenital disease (disorder)|| : 116676008 |Associated morphology (attribute)| = << 399984000 |Abnormal shape (morphologic abnormality)|";
		templateNames = new String[] { "templates/morphologies/Abnormal Shape.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "(<< 404684003 |Clinical finding (finding)| : 116676008 |Associated morphology (attribute)| = << 1806006 |Eruption (morphologic abnormality)|) MINUS (<< 64572001 |Disease (disorder)|)";
		templateNames = new String[] { "templates/morphologies/Eruption - finding.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 1806006 |Eruption (morphologic abnormality)|";
		templateNames = new String[] { "templates/morphologies/Eruption - disorder.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 36191001 |Perforation (morphologic abnormality)|";
		templateNames = new String[] { "templates/morphologies/Perforation.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "(<< 404684003 |Clinical finding (finding)| : 116676008 |Associated morphology (attribute)| = << 107682006 |Fibrosis AND/OR repair abnormality | ) MINUS ( << 64572001 |Disease (disorder)| )";
		templateNames = new String[] { "templates/morphologies/Fibrosis - finding.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 107682006 |Fibrosis AND/OR repair abnormality |  ";
		templateNames = new String[] { "templates/morphologies/Fibrosis - disorder.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 371136004 |Disorder of tooth development (disorder)| ";
		templateNames = new String[] { "templates/pathological process/Developmental.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "(<< 371521007 |Disorder of bone development (disorder)|) MINUS ( << 19579005 |Juvenile osteochondritis (disorder)| )";
		templateNames = new String[] { "templates/pathological process/Developmental.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<<  609520005 |Disorder of fetal structure (disorder)|";
		templateNames = new String[] { "templates/Disorder of fetal structure.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<<  40445007 |Heart valve regurgitation (disorder)|";
		templateNames = new String[] { "templates/Heart valve insufficiency.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = "<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 76093008 |Anterior displacement (morphologic abnormality)|";
		templateNames = new String[] { "templates/morphologies/Anterior displacement.json" };
		populateTemplates(subsetECL, templateNames);
		
		subsetECL = null;
		templateNames = new String[] { "templates/Traumatic injury.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/morphologies/Posterior displacement.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/morphologies/Separation.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/morphologies/Retention.json" };
		populateTemplates(null, templateNames);
		
		/*templateNames = new String[] { "templates/anatomy/Anatomical Parts.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/anatomy/Entity in region.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/anatomy/Hair follicle.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/anatomy/Region of region.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/anatomy/Skin of body.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/anatomy/Skin of part of body.json" };
		populateTemplates(null, templateNames);*/
		
		templateNames = new String[] { "templates/procedures/MRI.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/morphologies/Calculus.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/morphologies/Lateral displacement.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/morphologies/Medial displacement.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/Poisoning caused by substance or product.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/morphologies/Compression.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/morphologies/Enlargement.json" };	
		populateTemplates(null, templateNames);
		
		templateNames = new String[] {	"templates/drugs/MP only.json",
										"templates/drugs/MP containing.json",
										"templates/drugs/MPF containing.json",
										"templates/drugs/MPF only.json",
										"templates/drugs/CD precise discrete.json",
										"templates/drugs/CD precise continuous.json"};
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/procedures/CT Guided.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/morphologies/Misalignment.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/morphologies/Generic Morphologies.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/morphologies/Recession CF.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/morphologies/Recession.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/Poisoning.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/procedures/Stoma.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/procedures/CT of Body Structure with contrast.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/procedures/CT of Body Structure.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/procedures/CT Guided.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/Muscular dystrophy.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/Craniofacial cleft.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/Measurement.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/procedures/Anthrotomy.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/procedures/Angiography.json" };
		populateTemplates(null, templateNames);

		templateNames = new String[] { "templates/Allergic Disease.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/procedures/Needle Biopsy.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/procedures/Imaging Guided Biopsy.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/procedures/Excision of cyst.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/Substance abuse.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/morphologies/Malposition.json" };
		populateTemplates(null, templateNames);
		
		templateNames = new String[] { "templates/Drug dependence.json" };
		populateTemplates(null, templateNames);
		
		
		//Do this one last to pick up whatever is left under Disease
		subsetECL = "<< 64572001 |Disease (disorder)|"; 
		templateNames = new String[] {	"templates/Disease.json" };
		populateTemplates(subsetECL, templateNames);
		
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
				info ("Loading template " + templateName + " from TS to run against subset: " + ct.getDomain());
				
				if (ct.getDomain() == null) {
					warn("TS template " + templateName + " is not saying what domain it applies to");
				}
				//Have we seen this subset before?
				List<Template> templates = domainTemplates.get(ct.getDomain());
				if (templates == null) {
					templates = new ArrayList<>();
					domainTemplates.put(ct.getDomain(), templates);
				}
				template.setSource(TEMPLATE_SERVICE);
				templates.add(template);
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to load templates from TS", e);
		}
		
	}

	private void populateTemplates(String ecl, String[] templateNames) throws TermServerScriptException {
		
			char id = 'A';
			for (int x = 0; x < templateNames.length; x++, id++) {
				info ("Loading template: " + templateNames[x]);
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
}

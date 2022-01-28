package org.ihtsdo.termserver.scripting.template;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.ValidationFailure;

import org.ihtsdo.termserver.scripting.client.TemplateServiceClient;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.snomed.authoringtemplate.domain.ConceptTemplate;
import org.snomed.authoringtemplate.domain.logical.LogicalTemplate;
import org.snomed.otf.scheduler.domain.*;
import org.springframework.util.StringUtils;

/**
 * See https://confluence.ihtsdotools.org/display/IAP/Quality+Improvements+2018
 * Update: https://confluence.ihtsdotools.org/pages/viewpage.action?pageId=61155633
 */
public class MisalignedConcepts extends TemplateFix implements ReportClass {
	
	private Map<Concept, List<String>> conceptDiagnosticsInferred = new HashMap<>();
	private Map<Concept, List<String>> conceptDiagnosticsStated = new HashMap<>();
	public static final String INCLUDE_COMPLEX = "Include complex cases";
	public static final String INCLUDE_ORPHANET = "Include Orphanet";
	public static final String KNOWN_COMPLETE = "Skip Known Complete";
	public static final String KNOWN_COMPLETE_ECL = " <<2775001 OR <<3218000 OR <<3723001 OR <<5294002 OR <<7890003 OR <<8098009 OR <<17322007 OR <<20376005 OR <<34014006 OR <<40733004 OR <<52515009 OR <<85828009 OR <<87628006 OR <<95896000 OR <<109355002 OR <<118616009 OR <<125605004 OR <<125643001 OR <<125666000 OR <<125667009 OR <<125670008 OR <<126537000 OR <<128139000 OR <<128294001 OR <<128477000 OR <<128482007 OR <<131148009 OR <<193570009 OR <<233776003 OR <<247441003 OR <<276654001 OR <<283682007 OR <<298180004 OR <<307824009 OR <<312608009 OR <<362975008 OR <<399963005 OR <<399981008 OR <<400006008 OR <<400178008 OR <<416462003 OR <<416886008 OR <<417893002 OR <<419199007 OR <<428794004 OR <<429040005 OR <<432119003 OR <<441457006 OR <<419199007 OR <<282100009 OR <<55342001 OR <<128462008 OR <<363346000 OR <<372087000 OR <<785851003 OR <<308492005";
	
	List<Concept> alreadyReportAligned = new ArrayList<>();
	List<Concept> alreadyReportedExcluded = new ArrayList<>();
	
	public MisalignedConcepts() {
		super(null);
	}

	protected MisalignedConcepts(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "<<  302495003 |Arthrotomy (procedure)|");
		params.put(TEMPLATE, "71388002 |Procedure (procedure)| : [[~0..1]]{[[~1..1]]  363702006 |Has focus (attribute)|  = [[+id(<< 399269003 |Arthropathy (disorder)| )]]}[[~1..1]]{[[~1..1]]  260686004 |Method (attribute)|  = [[+id( << 129287005 |Incision - action (qualifier value)| )]],[[~1..1]]  405813007 |Procedure site - Direct (attribute)|  = [[+id(<<  39352004 |Joint structure (body structure)| )]],[[~0..1]]  260507000 |Access (attribute)| = [[+id(< 309795001 |Surgical access values (qualifier value)| )]]}[[~0..*]]{[[~1..1]]  260686004 |Method (attribute)|  = [[+id(<< 129284003 |Surgical action (qualifier value)| )]],[[~0..1]]  405813007 |Procedure site - Direct (attribute)|  = [[+id(<<  91723000 |Anatomical structure (body structure)| )]],[[~0..1]]  405814001 |Procedure site - Indirect (attribute)|  = [[+id(<<  91723000 |Anatomical structure (body structure)| )]],[[~0..1]]  363701004 |Direct substance (attribute)|  = [[+id (<< 105590001 |Substance (substance)| )]],[[~0..1]]  363699004 |Direct device (attribute)|  = [[+id(<  49062001 |Device (physical object)| )]],[[~0..1]]  363700003 |Direct morphology (attribute)|  = [[+id(<<  49755003 |Morphologically abnormal structure (morphologic abnormality)| )]],[[~0..1]]  363701004 |Direct substance (attribute)|  = [[+id(<< 261217004 |Substance (attribute)| )]]}");
		params.put(INCLUDE_COMPLEX, "false");
		params.put(INCLUDE_ORPHANET, "true");
		params.put(KNOWN_COMPLETE, "true");
		TermServerReport.run(MisalignedConcepts.class, args, params);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL)
					.withType(JobParameter.Type.ECL)
					.withMandatory()
				.add(INCLUDE_COMPLEX)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue(true)
				.add(INCLUDE_ORPHANET)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue(true)
				.add(KNOWN_COMPLETE)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue(true)
				.add(TEMPLATE)
					.withType(JobParameter.Type.TEMPLATE)
				.add(TEMPLATE2)
					.withType(JobParameter.Type.TEMPLATE)
				.add(TEMPLATE_NAME)
					.withType(JobParameter.Type.TEMPLATE_NAME)
				.add(SERVER_URL)
					.withType(JobParameter.Type.HIDDEN)
					.withMandatory()
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.QI))
				.withName("Template Compliance")
				.withDescription("This report lists concepts which match the specified selection (either a subhierarchy OR an ECL expression) "  + 
									"which DO NOT comply with the selected template. You can either select an existing published template, "  + 
									"or enter one yourself using template language or a known QI project internal path (eg /reporting-engine-worker/src/main/resources/templates/burn/Burn of body structure.json)")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT).withTag(MS)
				.build();
	}
	
	protected void init(JobRun jobRun) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1uywo1VGAIh7MMY7wCn2yEj312OQCjt9J"; // QI / Misaligned Concepts
		selfDetermining = true;
		reportNoChange = false;
		runStandAlone = true; 
		populateEditPanel = true;
		populateTaskDescription = true;
		summaryTabIdx = SECONDARY_REPORT;
		
		if (inclusionWords == null) {
			inclusionWords = new ArrayList<>();
		}
		if (exclusionWords == null) {
			exclusionWords = new ArrayList<>();
		}
		
		super.init(jobRun);
		
		//Have we been called via the reporting platform?
		if (appContext == null && jobRun.getParameters().getValue(ECL) == null) {
			localRunInit();
		} else {
			includeComplexTemplates = jobRun.getParameters().getMandatoryBoolean(INCLUDE_COMPLEX);
			includeOrphanet = jobRun.getParameters().getMandatoryBoolean(INCLUDE_ORPHANET);
			subsetECL = jobRun.getMandatoryParamValue(ECL);
			
			if (jobRun.getParameters().getMandatoryBoolean(KNOWN_COMPLETE)) {
				subsetECL = "( " + subsetECL + " ) MINUS ( " + KNOWN_COMPLETE_ECL + " )";
			}

			String templateServerUrl = jobRun.getMandatoryParamValue(SERVER_URL);
			//Do we have a template name to load, or some actual template language?
			String templateName = jobRun.getParamValue(TEMPLATE_NAME);
			String template = jobRun.getParamValue(TEMPLATE);
			String template2 = jobRun.getParamValue(TEMPLATE2);
			if (!StringUtils.isEmpty(templateName) && !StringUtils.isEmpty(template)) {
				throw new TermServerScriptException("Both published template name and template code were specified");
			} else if (StringUtils.isEmpty(templateName) && StringUtils.isEmpty(template)) {
				throw new TermServerScriptException("Neither published template name nor template code were specified");
			}
			
			tsc = new TemplateServiceClient(templateServerUrl, authenticatedCookie);
			
			if (!StringUtils.isEmpty(templateName)) {
				try {
					templates.add(loadTemplate('A',templateName));
				} catch (Exception e) {
					throw new TermServerScriptException("Unable to load template '" + templateName + "'from " + templateServerUrl, e);
				}
			} else {
				loadUserSpecifiedTemplate(template);
				if (template2 != null) {
					loadUserSpecifiedTemplate(template2);
				}
			}
			
			//Ensure our ECL matches more than 0 concepts before we import SNOMED - expensive!
			//TODO Now that we're working with morphologies this is getting expensive.
			//Query just for the count if 0 concepts resurfaces as an issue.
		}
	}

	private void loadUserSpecifiedTemplate(String template) throws TermServerScriptException {
		try {
			//This could be template language, json or a template name
			String templateName;
			LogicalTemplate logicalTemplate;
			if (template.startsWith("templates")) {
				ConceptTemplate ct = tsc.loadLocalConceptTemplate(template);
				logicalTemplate = tsc.parseLogicalTemplate(ct.getLogicalTemplate());
				templateName = template;
			} else if (template.startsWith("{") || template.startsWith("[")) {
				ConceptTemplate ct = tsc.loadLocalConceptTemplate("User Defined Template",IOUtils.toInputStream(template));
				logicalTemplate = tsc.parseLogicalTemplate(ct.getLogicalTemplate());
				templateName = "User supplied json block";
			} else {
				//We'll try to parse template language at this point
				logicalTemplate = tsc.parseLogicalTemplate(template);
				templateName = "User supplied template";
			} 
			char templateId = (char)('A' + templates.size());
			Template t = new Template (templateId, logicalTemplate, templateName);
			if (t.getDomain() == null) {
				t.setDomain(subsetECL);
			}
			templates.add(t);
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to load tempate '" + template + "'", e);
		}
		info ("Loaded user specified template: " + template);
	}

	protected void localRunInit() throws TermServerScriptException {
		/*
		subHierarchyECL = "<<125605004";  // QI-5 |Fracture of bone (disorder)|
		templateNames = new String[] {	"fracture/Fracture of Bone Structure.json",
										"fracture/Fracture Dislocation of Bone Structure.json",
										"fracture/Pathologic fracture of bone due to Disease.json",
										"fracture/Pathologic fracture morphology of bone structure co-occurrent and due to Neoplasm of bone.json",
										"fracture/Traumatic abnormality of spinal cord structure co-occurrent and due to fracture morphology of vertebral bone structure.json",
										//"Injury of finding site due to birth trauma.json"
										 };
		
		subHierarchyStr =  "128294001";  // QI-9 |Chronic inflammatory disorder (disorder)
		templateNames = new String[] {"templates/Chronic Inflammatory Disorder.json"}; 
		setExclusions(new String[] {"40733004|Infectious disease|"});
		exclusionWords.add("arthritis");
		
		subHierarchyStr =  "126537000";  //QI-14 |Neoplasm of bone (disorder)|
		templateNames = new String[] {	"templates/Neoplasm of Bone.json",
										"templates/Pathologic fracture morphology of bone structure co-occurrent and due to Neoplasm of bone.json"};
		
		subHierarchyStr =  "34014006"; //QI-15 |Viral disease (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by Virus.json",
										"templates/infection/Infection of bodysite caused by virus.json"};
		
		subHierarchyECL = "<<87628006";  //QI-16 |Bacterial infectious disease (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by bacteria.json"};
		
		subHierarchyECL = "<<95896000";  //QI-19  |Protozoan infection (disorder)|
		templateNames = new String[] {"templates/infection/Infection caused by Protozoa with optional bodysite.json"};
			
		subHierarchyECL = "<<125666000";  //QI-33  |Burn (disorder)|

		excludeHierarchies = new String[] { "426284001" } ; // |Chemical burn (disorder)| 
		templateNames = new String[] {
				"templates/burn/Burn of body structure.json",
				"templates/burn/Epidermal burn of body structure.json",
				"templates/burn/Partial thickness burn of body structure.json",
				"templates/burn/Full thickness burn of body structure.json",
				"templates/burn/Deep partial thickness burn of body structure.json",
				"templates/burn/Superficial partial thickness burn of body structure.json"};
		
		subHierarchyECL = "<<74627003";  //QI-48 |Diabetic Complication|
		templateNames = new String[] {	"templates/Complication due to Diabetes Melitus2.json"};
		includeComplexTemplates = true;
		
		subHierarchyECL = "<<8098009";	// QI-45 |Sexually transmitted infectious disease (disorder)| 
		templateNames = new String[] {	"templates/Sexually transmitted Infection with optional bodysite.json"};
		
		subHierarchyECL = "<<283682007"; // QI-39 |Bite - wound (disorder)|
		templateNames = new String[] {	"templates/bite/bite of bodysite caused by bite event.json", 
										"templates/bite/bite of bodysite caused by bite event with infection.json"};
		
		subHierarchyECL = "<<3218000"; //QI-67 |Mycosis (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by Fungus.json"};
		
		subHierarchyECL = "<<17322007"; //QI-68 |Parasite (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by Parasite.json"};
		
		subHierarchyECL = "<<416886008"; //QI-106 |Closed wound| 
		templateNames = new String[] {	"templates/wound/wound of bodysite.json"};
		
		subHierarchyECL = "<<125643001"; //QI-107 |Open wound| 
		templateNames = new String[] {	"templates/wound/wound of bodysite due to event.json" };
		exclusionWords.add("complication");
		exclusionWords.add("fracture");
		setExclusions(new String[] {"399963005 |Abrasion (disorder)|", "312608009 |Laceration - injury|"});
		includeDueTos = true;
		
		subHierarchyECL = "<<128545000"; //QI-75 |Hernia of abdominal wall (disorder)|
		//subHierarchyECL = "<<773623000";
		templateNames = new String[] {	"templates/Hernia of abdominal wall.json"};
		excludeHierarchies = new String[] { "236037000 |Incisional hernia (disorder)|" };
		exclusionWords = new ArrayList<String>();
		exclusionWords.add("gangrene");
		exclusionWords.add("obstruction");
		
		subHierarchyECL = "<<432119003 |Aneurysm (disorder)|"; //QI-143 
		templateNames = new String[] {	"templates/Aneurysm of Cardiovascular system.json" };
		
		subHierarchyECL = "<<40733004|Infectious disease|"; //QI-153
		templateNames = new String[] {	"templates/infection/Infection NOS.json" };
		setExclusions(new String[] {"87628006 |Bacterial infectious disease (disorder)|","34014006 |Viral disease (disorder)|",
				"3218000 |Mycosis (disorder)|","8098009 |Sexually transmitted infectious disease (disorder)|", 
				"17322007 |Disease caused by parasite (disorder)|", "91302008 |Sepsis (disorder)|"});
		exclusionWords.add("shock");
		
		subHierarchyECL = "<<399963005 |Abrasion|"; //QI-147
		templateNames = new String[] {	"templates/wound/abrasion.json" ,
										"templates/Disorder due to birth trauma.json"};
		setExclusions(new String[] {"118938008 |Disorder of mouth (disorder)|"};
		includeDueTos = true;
		
		subHierarchyECL = "300935003 OR 206203003 OR 276623000"; //QI-147
		templateNames = new String[] {	"templates/Disorder due to birth trauma.json" };
		includeDueTos = true;
		
		subHierarchyECL = "<< 52515009 |Hernia of abdominal cavity|"; //QI-172
		setExclusions(new String[] {"236037000 |Incisional hernia (disorder)|", 
									"372070002 |Gangrenous disorder (disorder)|",
									"309753005 |Hiatus hernia with obstruction (disorder)|"});
		templateNames = new String[] {"templates/hernia/Hernia of Body Structure.json" };
		exclusionWords.add("gangrene");
		exclusionWords.add("obstruction");
		exclusionWords.add("obstructed");
		
		subHierarchyECL = "<<312608009 |Laceration - injury|"; //QI-177
		templateNames = new String[] {	"templates/wound/laceration.json" };
		includeDueTos = true;
		
		subHierarchyECL = "<< 3723001 |Arthritis (disorder)|"; //QI-123
		templateNames = new String[] {	"templates/Arthritis.json" };
		
		subHierarchyECL = "<<428794004 |Fistula (disorder)|"; //QI-186
		templateNames = new String[] {	"templates/Fistula.json" };
		includeDueTos = true;
		
		subHierarchyECL = "<<441457006 |Cyst|"; //QI-181
		templateNames = new String[] {	"templates/Cyst.json" };
		
		subHierarchyECL = "<< 128477000 |Abscess (disorder)|"; //QI-213
		templateNames = new String[] {	"templates/Abscess.json",
										"templates/Abscess with Cellulitis.json"};
		
		subHierarchyECL = "<< 416462003 |Wound (disorder)|"; //QI-209
		setExclusions(new String[] {"125643001 |Open Wound|", 
									"416886008 |Closed Wound|",
									"312608009 |Laceration|",
									"283682007 |Bite Wound|",
									"399963005 |Abraision|",
									"125670008 |Foreign Body|",
									"125667009 |Contusion (disorder)|"});
		templateNames = new String[] {	"templates/wound/wound of bodysite.json",
										"templates/wound/wound of bodysite due to event.json"};
		includeDueTos = true;
		
		subHierarchyECL = "< 125670008 |Foreign body (disorder)|"; //QI-156
		templateNames = new String[] {	"templates/Foreign body.json" };
		
		subHierarchyECL = "<  193570009 |Cataract (disorder)|"; //MQI-7
		templateNames = new String[] {	"templates/Cataract.json" };
		includeComplexTemplates = true;
		
		subHierarchyECL = "< 429040005 |Ulcer (disorder)|"; //QI-248
		templateNames = new String[] {	"templates/Ulcer.json" };
		
		subHierarchyECL = "< 64572001 |Disease (disorder)|"; 
		templateNames = new String[] {	"templates/Disease.json" };
		
		subHierarchyECL = "<< 109355002 |Carcinoma in situ (disorder)|"; //QI-231
		templateNames = new String[] {	"templates/Carcinoma in Situ.json" };
		
		subHierarchyECL = "<< 247441003 |Erythema|"; //QI-240
		templateNames = new String[] {	"templates/Erythema of body structure.json" };
		inclusionWords.add("finding");
		
		subHierarchyECL = "<< 445505000 |Contracture of joint of spine (disorder)|";
		templateNames = new String[] {	"templates/Finding of range of joint movement.json" };
		
		subHierarchyECL = "< 64572001 |Disease (disorder)|"; 
		templateNames = new String[] {	"templates/Disease.json" };
		
		subHierarchyECL = "< 7890003 |Contracture of joint (disorder)|"; //QI-261
		templateNames = new String[] {	"templates/Contracture of joint minus.json" };
		includeComplexTemplates = true;
		
		subHierarchyECL = "<< 125667009 |Contusion (disorder)|"; //QI-244 
		templateNames = new String[] {	"templates/wound/contusion.json" };
		
		subHierarchyECL = "< 85828009 |Autoimmune disease (disorder)|"; //QI-297
		templateNames = new String[] {	"templates/Autoimune.json" };
		
		subHierarchyECL = "<< 298180004 |Finding of range of joint movement (finding)|  MINUS <<  7890003 |Contracture of joint (disorder)|";
		templateNames = new String[] {	"templates/Finding of range of joint movement.json" };
		includeComplexTemplates = true;
		
		subHierarchyECL = "<< 417893002|Deformity|"; //QI-278
		templateNames = new String[] {	"templates/Deformity - disorder.json",
				"templates/Deformity - finding.json"};
		
		subHierarchyECL = "< 128139000 |Inflammatory disorder (disorder)| : [0..0] 370135005 |Pathological process (attribute)| = << 472963003 |Hypersensitivity process (qualifier value)|"; //QI-370
		templateNames = new String[] {	"templates/Inflammatory Disorder.json",
					"templates/Infectious Inflammatory Disorder.json"};
		inputFile = new File ("resources/inflammatory_exclusions.txt");
		excludeSdMultiRG = true;
		
		subHierarchyECL = "<< 131148009|Bleeding|"; //QI-319
		//templateNames = new String[] { "templates/Bleeding - disorder.json"};
		//inclusionWords.add("disorder");
		templateNames = new String[] { "templates/Bleeding - finding.json"};
		inclusionWords.add("finding");
		
		subHierarchyECL = "<<  129156001 |Traumatic dislocation of joint (disorder)|";
		templateNames = new String[] { "templates/Traumatic dislocation of joint.json",
				"templates/fracture/Fracture Dislocation of Bone Structure.json"};
		includeComplexTemplates = true;
		
		subHierarchyECL = "<< 5294002 |Developmental disorder (disorder)| MINUS << 276654001 |Congenital malformation (disorder)|";
		templateNames = new String[] { "templates/Developmental disorder.json"};
		
		subsetECL = "(<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 30217000 |Proliferation (morphologic abnormality)|) MINUS (<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 400177003 |Neoplasm and/or hamartoma (morphologic abnormality)|)";
		templateNames = new String[] { "templates/Proliferation.json"};
		
		subsetECL = "<< 7895008 |Poisoning caused by drug AND/OR medicinal substance (disorder)| OR << 441952005 |Poisoning caused by chemical substance (disorder)|";
		templateNames = new String[] { "templates/Poisoning.json"};
		
		templateNames = new String[] { "templates/drugs/MP only.json" };
		*/
		//templateNames = new String[] { "templates/procedures/CT Guided.json" };
		
		super.init((String[])null);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"TASK_KEY, TASK_DESC, SCTID, FSN, CONCEPT_TYPE, SEVERITY, ACTION_TYPE, DefnStatus, TemplateMatched, IsComplex, IsOrphanet, Template Diagnostic",
				"Report Metadata, , ", 
				"SCTID, FSN, SEMTAG, SEVERITY, ACTION_TYPE, IsComplex, IsOrphanet, Template Diagnostic",
				"SCTID, FSN, SEMTAG, SEVERITY, ACTION_TYPE, IsComplex, IsOrphanet, Template Diagnostic",
				"SCTID, FSN, SemTag, Reason", 
				"SCTID, FSN, SemTag, Template Aligned"};
		String[] tabNames = new String[] {
				"Misaligned Inferred",
				"Metadata",
				"Misaligned Stated",
				"Misaligned Both",
				"Excluded Concepts",
				"Aligned Concepts"};
		super.postInit(tabNames, columnHeadings, false);
		outputMetaData();
	}
	
	@Override
	protected int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		report(task, concept);
		return CHANGE_MADE;
	}

	private void report(Task t, Concept c) throws TermServerScriptException {
		//Collect the diagnostic information about why this concept didn't match any templates as a string
		String diagnosticStr = "No diagnostic information available";
		if (conceptDiagnosticsInferred.get(c) != null) {
			diagnosticStr = String.join("\n", conceptDiagnosticsInferred.get(c));
		}
		report (t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, isComplex(c)?"Y":"N", gl.isOrphanetConcept(c)?"Y":"N", diagnosticStr);
	}
	
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
	
		List<Component> inferredMisaligned = identifyComponentsToProcess(CharacteristicType.INFERRED_RELATIONSHIP);
		
		//Inferred misaligned will be processed into tasks, so still need to report on stated and both
		List<Component> statedMisaligned = identifyComponentsToProcess(CharacteristicType.STATED_RELATIONSHIP);
		reportMisaligned(statedMisaligned, TERTIARY_REPORT, conceptDiagnosticsStated);
		
		statedMisaligned.retainAll(inferredMisaligned);
		reportMisaligned(statedMisaligned, QUATERNARY_REPORT, conceptDiagnosticsInferred);
		
		//We'll continue to put the inferred view misaligned concepts into tasks
		return inferredMisaligned;
	}

	private void reportMisaligned(List<Component> misaligned, int tabIdx, Map<Concept, List<String>> conceptDiagnostics) throws TermServerScriptException {
		for (Component thisMisaligned : misaligned) {
			Concept c = (Concept) thisMisaligned;
			String diagnosticStr = "No diagnostic information available";
			if (conceptDiagnostics.get(c) != null) {
				diagnosticStr = String.join("\n", conceptDiagnostics.get(c));
			}
			report (tabIdx, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, isComplex(c)?"Y":"N", gl.isOrphanetConcept(c)?"Y":"N", diagnosticStr);
		}
	}

	protected List<Component> identifyComponentsToProcess(CharacteristicType charType) throws TermServerScriptException {
		Map<Concept, List<String>> conceptDiagnostics = charType.equals(CharacteristicType.INFERRED_RELATIONSHIP) ? conceptDiagnosticsInferred : conceptDiagnosticsStated;
		//Start with the whole subHierarchy and remove concepts that match each of our templates
		//This will be so much quicker for MS once we can filter on Module Id first, rather than post-process
		Collection<Concept> potentiallyMisaligned = findConcepts(subsetECL).stream()
				.filter(c -> inModuleScope(c))
				.collect(Collectors.toList());
		
		if (potentiallyMisaligned == null || potentiallyMisaligned.size() == 0) {
			throw new TermServerScriptException("Failed to find any concepts matching ECL: '" + subsetECL + "' (in target module)");
		}
		
		//potentiallyMisaligned = Collections.singleton(gl.getConcept("70542004"));
		
		Set<Concept> ignoredConcepts = new HashSet<>();
		
		//Remove all exclusions before we look for matches
		for (Concept c : potentiallyMisaligned) {
			if (whiteListedConcepts.contains(c) && !alreadyReportedExcluded.contains(c)) {
				incrementSummaryInformation(WHITE_LISTED_COUNT);
				report (QUINARY_REPORT, c, "White listed");
				ignoredConcepts.add(c);
				alreadyReportedExcluded.add(c);
				continue;
			}
			if (alreadyReportedExcluded.contains(c) || isExcluded(c, QUINARY_REPORT)) {
				ignoredConcepts.add(c);
			}
		}
		potentiallyMisaligned.removeAll(ignoredConcepts);
		
		//Find matches against all templates
		for (Template template : templates) {
			Set<Concept> matches = findTemplateMatches(template, potentiallyMisaligned, null, QUINARY_REPORT, charType);
			incrementSummaryInformation("Matched templates",matches.size());
			for (Concept match : matches) {
				if (!alreadyReportAligned.contains(match)) {
					//Which template did we match?
					char templateId = conceptToTemplateMap.get(match).getId();
					report (SENARY_REPORT, match, templateId);
					alreadyReportAligned.add(match);
				}
			}
			potentiallyMisaligned.removeAll(matches);
			int beforeCount = potentiallyMisaligned.size();
			potentiallyMisaligned.removeAll(exclusions);
			int afterCount = potentiallyMisaligned.size();
			addSummaryInformation("Excluded due to subHierarchy rules - " + charType, (beforeCount - afterCount));
		}
		
		//Record diagnostics for all concepts that failed to align to a template
		for (Concept c : potentiallyMisaligned) {
			List<String> diagnostics = new ArrayList<String>();
			conceptDiagnostics.put(c, diagnostics);
			String msg = "Cardinality mismatch: " +  (StringUtils.isEmpty(c.getIssues())?" N/A" : c.getIssues());
			diagnostics.add(msg);
			diagnostics.add("Relationship Group mismatches:");
			for (RelationshipGroup g : c.getRelationshipGroups(charType)) {
				//is this group purely inferred?  Add an indicator if so 
				String purelyInferredIndicator = groupPurelyInferred(c,g)?"^":"";
				msg = "    " + purelyInferredIndicator + g;
				diagnostics.add(msg);
			}
			
			if (charType.equals(CharacteristicType.INFERRED_RELATIONSHIP)) {
				incrementSummaryInformation("Concepts identified as not matching any template in inferred view");
				countIssue(c);
			}
		}
		return asComponents(potentiallyMisaligned);
	}

	//return true if this inferred group does not have a stated counterpart
	private boolean groupPurelyInferred(Concept c, RelationshipGroup ig) {
		for (RelationshipGroup sg : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			if (ig.equals(sg)) {
				return false;
			}
		}
		return true;
	}
	
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

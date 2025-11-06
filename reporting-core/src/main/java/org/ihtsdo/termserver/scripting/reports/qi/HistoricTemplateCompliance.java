package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TransitiveClosure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.template.TemplateUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

/**
 * See https://confluence.ihtsdotools.org/display/IAP/Quality+Improvements+2018
 * Update: https://confluence.ihtsdotools.org/pages/viewpage.action?pageId=61155633
 * Update: RP-139
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistoricTemplateCompliance extends AllKnownTemplates implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(HistoricTemplateCompliance.class);

	Set<Concept> alreadyCounted = new HashSet<>();
	Map<Concept, Integer> outOfScopeCache = new HashMap<>();
	int totalTemplateMatches = 0;
	private static final String dataDir = "historic-data/";
	private static final String PREV_DATA = "Previous Data";
	public static final String THIS_RELEASE = "This Release";
	boolean comparePreviousData = false;
	
	Map<Concept, AlignedConcept> alignedConceptMap = new HashMap<>();
	Map<String, TemplateData> templateDataMap = new HashMap<>();
	Map<String, HierarchyData> hierarchyDataMap = new HashMap<>();
	 
	private static String dataFileId;


	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(SERVER_URL, "https://authoring.ihtsdotools.org/template-service");
	
		//params.put(THIS_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_20200731T120000Z.zip");
		//TermServerScript.run(HistoricTemplateCompliance.class,args, params);
		
		//params.put(PREV_DATA, dataFileId);
		params.put(PREV_DATA, "SnomedCT_InternationalRF2_PRODUCTION_20200731T120000Z.zip");
		params.put(THIS_RELEASE, "prod_main_2021-01-31_20201124120000.zip");
		TermServerScript.run(HistoricTemplateCompliance.class,args, params);
	}

	@Override
	public void init(JobRun run) throws TermServerScriptException {
		projectName = run.getParamValue(THIS_RELEASE);
		run.setProject(projectName);
		dataFileId = run.getParamValue(PREV_DATA);
		comparePreviousData = !StringUtils.isEmpty(run.getParamValue(PREV_DATA));
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {"SCTID, Hierarchy, Inactivated (prev align), No longer Aligns, Still Aligns, Newly Aligns", 
												"Template Name, Change Identified, Change Aligned, Currently Aligned, Out of Possible..."};
		String[] tabNames = new String[] {	"Concept Alignment", 
											"Template Alignment"};
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SERVER_URL)
					.withType(JobParameter.Type.HIDDEN)
					.withMandatory()
				.add(PREV_DATA)
					.withType(JobParameter.Type.STRING)
				.build();

		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.QI))
				.withName("Historic Templates Compliance Stats")
				.withDescription("For every domain which has one or more templates, determine how many concepts comply to that template(s) historically.")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	
	public void runJob() throws TermServerScriptException {
		//Check all of our domain points are still active concepts, or we'll have trouble with them!
		Set<String> invalidTemplateDomains = domainTemplates.keySet().stream()
			.filter(ecl -> findConceptsSafely(ecl).size() == 0)
			.collect(Collectors.toSet());
		
		for (String invalidTemplateDomain : invalidTemplateDomains) {
			List<Template> templates = domainTemplates.get(invalidTemplateDomain);
			for (Template t : templates) {
				LOGGER.warn ("Inactive or Non-existent domain: " + invalidTemplateDomain + " in template: " + t.getName());
			}
			domainTemplates.remove(invalidTemplateDomain);
		}
		
		for (Map.Entry<String, List<Template>> entry : domainTemplates.entrySet()) {
			String subsetECL = entry.getKey();
			try {
				List<Template> templates = entry.getValue();
				LOGGER.info("Examining subset defined by '" + subsetECL + "' against " + templates.size() + " templates");
				examineSubset(subsetECL, templates);
			} catch (Exception e) {
				LOGGER.error ("Exception while processing domain " + subsetECL, e);
			}
		}
		
		if (comparePreviousData) {
			comparePreviousConceptData();
			comparePreviousTemplateData();
		} else {
			writeHistoricData();
		}
	}

	private void comparePreviousConceptData() throws TermServerScriptException {
		TransitiveClosure tc = gl.generateTransitiveClosure();
		
		File f = new File(dataDir + dataFileId  + "_conceptData.tsv");
		LOGGER.info("Reading historic data file " + f);
		Set<String> previouslyAligned = new HashSet<>();
		try {
			Scanner scanner = new Scanner(f);
			while (scanner.hasNextLine()) {
				AlignedConcept prevAC= deserialiseAlignedConcept(scanner.nextLine());
				previouslyAligned.add(prevAC.c.getId());
				AlignedConcept currAC = alignedConceptMap.get(prevAC.c);
				//What hierarchy are we looking at here?
				HierarchyData hd = hierarchyDataMap.get(prevAC.hierarchy);
				if (hd == null) {
					hd = new HierarchyData(prevAC.hierarchy);
					hierarchyDataMap.put(prevAC.hierarchy, hd);
				}
				
				//Has this concept been inactivated?
				if (prevAC.c.isActive() == false) {
					hd.inactivatedAlignedConcept++;
				} else if (currAC == null) {
					hd.noLongerAligns ++;
				} else {
					hd.continuesToAlign++;
				}
			}
			
			//Now work how many newly aligning concepts we have
			for (AlignedConcept currAC : alignedConceptMap.values()) {
				if (!previouslyAligned.contains(currAC.c.getId())) {
					HierarchyData hd = hierarchyDataMap.get(getHierarchy(tc, currAC.c));
					hd.newAligningConcept++;
				}
			}
			scanner.close();
			
			//Now output per hierarchy
			for (HierarchyData hd : hierarchyDataMap.values()) {
				Concept h = gl.getConcept(hd.hierarchy);
				report(PRIMARY_REPORT, hd.hierarchy, h.getPreferredSynonym(), hd.inactivatedAlignedConcept, hd.noLongerAligns, hd.continuesToAlign, hd.newAligningConcept);
			}
			
		} catch (FileNotFoundException e) {
			LOGGER.error("Exception encountered",e);
		}
	}
	
	private void comparePreviousTemplateData() throws TermServerScriptException {
		File f = new File(dataDir + dataFileId  + "_templateData.tsv");
		LOGGER.info("Reading historic data file " + f);
		Set<String> previousTemplates= new HashSet<>();
		try {
			Scanner scanner = new Scanner(f);
			while (scanner.hasNextLine()) {
				TemplateData prevTD= deserializeTemplateData(scanner.nextLine());
				TemplateData currTD = templateDataMap.get(prevTD.templateName);
				previousTemplates.add(prevTD.templateName);
				int changeIdentified = currTD.conceptsIdentified - prevTD.conceptsIdentified;
				int changeAlign = currTD.conceptsAligning - prevTD.conceptsAligning;
				report(SECONDARY_REPORT, prevTD.templateName, changeIdentified, changeAlign, currTD.conceptsAligning, currTD.conceptsIdentified);
			}
			
			//Now report new templates
			for (TemplateData currTD : templateDataMap.values()) {
				if (!previousTemplates.contains(currTD.templateName)) {
					report(SECONDARY_REPORT, currTD.templateName, "", "", currTD.conceptsAligning);
				}
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			LOGGER.error("Exception encountered",e);
		}
	}

	private void writeHistoricData() throws TermServerScriptException {
		FileWriter fw = null;
		try {
			//Create the historic-data directory if required
			File dataDirFile = new File(dataDir);
			if (!dataDirFile.exists()) {
				LOGGER.info("Creating directory to store historic data analysis: " + dataDir);
				boolean success = dataDirFile.mkdir();
				if (!success) {
					throw new TermServerScriptException("Failed to create " + dataDirFile.getAbsolutePath());
				}
			}
			
			dataFileId = project.getKey();
			File f = new File(dataDir + dataFileId  + "_conceptData.tsv");
			LOGGER.info("Creating dataFile: " + f.getAbsolutePath());
			f.createNewFile();
			fw = new FileWriter(f);
			
			TransitiveClosure tc = gl.generateTransitiveClosure();
			LOGGER.debug("Outputting Data to " + f.getAbsolutePath());
			for (AlignedConcept ac : alignedConceptMap.values()) {
				fw.append(ac.serialize(getHierarchy(tc, ac.c)));
			}
			fw.close();
			
			f = new File(dataDir + dataFileId  + "_templateData.tsv");
			LOGGER.info("Creating dataFile: " + f.getAbsolutePath());
			f.createNewFile();
			fw = new FileWriter(f);
			
			LOGGER.debug("Outputting Data to " + f.getAbsolutePath());
			for (TemplateData td : templateDataMap.values()) {
				fw.append(td.serialize());
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		} finally {
			try {
				fw.close();
			} catch (Exception e2) {}
		}
	}
	
	private String getHierarchy(TransitiveClosure tc, Concept c) throws TermServerScriptException {
		if (c.equals(ROOT_CONCEPT)) {
			return  "";
		}

		if (!c.isActive() || c.getDepth() == NOT_SET) {
			return "";  //Hopefully the previous release will know
		}

		if (c.getDepth() == 1) {
			return c.getConceptId();
		} 
		
		for (Long sctId : tc.getAncestors(c)) {
			Concept a = gl.getConcept(sctId);
			if (a.getDepth() == 1) {
				return a.getConceptId();
			}
		}
		throw new TermServerScriptException("Unable to determine hierarchy for " + c);
	}

	private void examineSubset(String ecl, List<Template> templates) throws TermServerScriptException {
		Collection<Concept> subset = findConcepts(ecl);
		if (subset.size() == 0) {
			LOGGER.warn ("No concepts found in subset defined by '" + ecl + "' skipping");
			return;
		}
		
		//Now how many of these are we removing because they have no model?
		Set<Concept> noModel = subset.stream()
				.filter(c -> countAttributes(c, CharacteristicType.INFERRED_RELATIONSHIP) == 0)
				.collect(Collectors.toSet());
		subset.removeAll(noModel);
		
		for (Template t : templates) {
			TemplateData td = templateDataMap.get(t.getName());
			if (td == null) {
				td = new TemplateData(t);
				templateDataMap.put(t.getName(), td);
			}
			td.conceptsIdentified += subset.size();
		}
		
		//Now lets see how many we can match to a template
		nextConcept:
		for (Concept c : subset) {
			for (Template t : templates) {
				if (TemplateUtils.matchesTemplate(c, t, this, CharacteristicType.INFERRED_RELATIONSHIP)) {
					recordAlignment(t, c);
					continue nextConcept;
				}
			}
		}
	}
	
	private void recordAlignment(Template t, Concept c) {
		AlignedConcept ac = alignedConceptMap.get(c);
		if (ac == null) {
			ac = new AlignedConcept(c);
			alignedConceptMap.put(c, ac);
		}
		ac.recordAlignment(t);
		
		TemplateData td = templateDataMap.get(t.getName());
		if (td == null) {
			td = new TemplateData(t);
			templateDataMap.put(t.getName(), td);
		}
		td.recordAlignment(c);
	}
	
	private TemplateData deserializeTemplateData (String line) {
		String[] lineItems = line.split(TAB);
		TemplateData td = new TemplateData(null);
		td.templateName = lineItems[0];
		td.conceptsIdentified = Integer.parseInt(lineItems[1]);
		td.conceptsAligning = Integer.parseInt(lineItems[2]);
		return td;
	}
	
	private AlignedConcept deserialiseAlignedConcept (String line) throws TermServerScriptException {
		String[] lineItems = line.split(TAB);
		AlignedConcept ac = new AlignedConcept (gl.getConcept(lineItems[0]));
		ac.hierarchy=lineItems[1];
		String[] tArr = Arrays.copyOfRange(lineItems, 2, lineItems.length);
		ac.matchingTemplates = new HashSet<String>(Arrays.asList(tArr));
		return ac;
	}

	class TemplateData {
		public TemplateData(Template t) {
			if (t != null) {
				this.templateName = t.getName();
			}
		}
		public CharSequence serialize() {
			return templateName + TAB + conceptsIdentified + TAB + conceptsAligning + "\r\n";
		}
		public void recordAlignment(Concept c) {
			conceptsAligning++;
		}
		String templateName;
		int conceptsIdentified;
		int conceptsAligning;
	}
	
	class AlignedConcept {
		AlignedConcept (Concept c) {
			this.c = c;
		}
		public CharSequence serialize(String hierarchy) {
			return c.getId() + TAB + hierarchy + TAB  + matchingTemplates.stream().collect(Collectors.joining(TAB)) + "\r\n";
		}
		public void recordAlignment(Template t) {
			if (matchingTemplates == null) {
				matchingTemplates = new HashSet<>();
			}
			matchingTemplates.add(t.getName());
		}
		Concept c;
		String hierarchy;
		Set<String> matchingTemplates;
	}
	
	class HierarchyData {
		public HierarchyData(String hierarchy) {
			this.hierarchy = hierarchy;
		}
		String hierarchy;
		int inactivatedAlignedConcept;
		int noLongerAligns;
		int continuesToAlign;
		int newAligningConcept;
	}
	
}

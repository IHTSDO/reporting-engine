package org.ihtsdo.termserver.scripting.reports;

import java.util.*;
import java.util.Map.Entry;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * Lists all semantic tags used in each of the top level hierarchies.
 */

public class ListSemanticTagsByHierarchy extends TermServerReport implements ReportClass {
	
	public static String INCLUDE_INT = "Include International Content";
	
	private boolean includeInt = false;

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(INCLUDE_INT, "true");
		TermServerScript.run(ListSemanticTagsByHierarchy.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc
		super.init(run);
		headers="Hieararchy, SemTag, Language, Count";
		additionalReportColumns="";
		includeInt = run.getMandatoryParamBoolean(INCLUDE_INT);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(INCLUDE_INT).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("List Semantic Tags By Hierarchy")
				.withDescription("This report lists all semantic tags used in each top level hierarchy. " + 
								"Note that since this report is not listing any problems, the 'Issues' count will always be 0.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withTag(INT)
				.withTag(MS)
				.withParameters(params)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		//Work through all top level hierarchies and list semantic tags along with their counts
		for (Concept topLevel : ROOT_CONCEPT.getDescendants(IMMEDIATE_CHILD)) {
			Set<Concept> descendants = topLevel.getDescendants(NOT_SET);
			report(PRIMARY_REPORT, (Component)null, topLevel.toString(), "", descendants.size());
			Map<String, Multiset<String>> languageMap = new HashMap<>();
			for (Concept c : descendants) {
				for (Description d : c.getDescriptions()) {
					if(!includeInt && SnomedUtils.isInternational(d)) {
						continue;
					}
					
					if (d.isActive() && d.getType().equals(DescriptionType.FSN)) {
						//Have we see this language before?
						Multiset<String> tags = languageMap.get(d.getLang());
						if (tags == null) {
							tags = HashMultiset.create();
							languageMap.put(d.getLang(), tags);
						}
						tags.add(SnomedUtilsBase.deconstructFSN(d.getTerm())[1]);
					}
				}
			}
			
			for (Entry<String, Multiset<String>> entry : languageMap.entrySet()) {
				String language = entry.getKey();
				Multiset<String> tags = entry.getValue();
				for (String tag : tags.elementSet()) {
					report(PRIMARY_REPORT, (Component)null, "", tag, language, tags.count(tag));
				}
			}
		}
	}
}

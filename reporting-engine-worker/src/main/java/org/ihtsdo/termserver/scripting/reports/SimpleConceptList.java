package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

/**
 * ISP-36 Simple list of concepts based on ECL
 */
public class SimpleConceptList extends TermServerReport implements ReportClass {

	public static final String LANG_REFSETS = "LangRefsets";
	public List<Concept> langRefsets = new ArrayList<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "<105590001 |Substance (substance)|");
		params.put(LANG_REFSETS, "900000000000509007, 900000000000508004");
		TermServerScript.run(SimpleConceptList.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc Reports
		subsetECL = run.getMandatoryParamValue(ECL);
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		additionalReportColumns = "FSN,SemTag,DefStatus,EffectiveTime";
		for (String langRefsetStr : getJobRun().getParamValue(LANG_REFSETS).split(",")) {
			Concept langRefset = gl.getConcept(langRefsetStr.trim());
			langRefsets.add(langRefset);
			additionalReportColumns += "," + SnomedUtils.shortestTerm(langRefset);
		}
		super.postInit();
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL).withMandatory()
				.add(LANG_REFSETS).withType(JobParameter.Type.STRING).withMandatory().withDefaultValue("900000000000509007, 900000000000508004")
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("List selected concepts with preferred terms")
				.withDescription("This report lists all concepts matching the specified ECL and preferred terms from the specified refsets." + 
				" As a special case, GB PTs will only be listed where they differ from the US PT.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.withExpectedDuration(60)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		ArrayList<Concept> subset = new ArrayList<>(findConcepts(subsetECL));
		subset.sort(Comparator.comparing(Concept::getFsnSafely));
		for (Concept c : subset) {
			String defStatus = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
			report(c, defStatus, c.getEffectiveTime(), getPTs(c));
			countIssue(c);
		}
	}

	private String[] getPTs(Concept c) throws TermServerScriptException {
		String[] pts = new String[langRefsets.size()];
		for (int i = 0; i < langRefsets.size(); i++) {
			Concept langRefset = langRefsets.get(i);
			//Is this GB?  Only show if not same as US
			if (langRefset.getId().equals(GB_ENG_LANG_REFSET)) {
				Description gbPT = c.getPreferredSynonym(GB_ENG_LANG_REFSET);
				if (gbPT != null && !gbPT.equals(c.getPreferredSynonym(US_ENG_LANG_REFSET))) {
					pts[i] = gbPT.getTerm();
				} else {
					pts[i] = "";
				}
			} else {
				Description thisPT = c.getPreferredSynonymSafely(langRefset.getId());
				pts[i] = (thisPT == null) ? "" : thisPT.getTerm();
			}
		}
		return pts;
	}

}

package org.ihtsdo.termserver.scripting.reports;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.util.CaseSensitivityUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

public class CaseSensitiveList extends TermServerReport implements ReportClass {

	private CaseSensitivityUtils csUtils;

	public static void main(String[] args) throws TermServerScriptException {
		TermServerScript.run(CaseSensitiveList.class, new HashMap<>(), args);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		headers = "Case Sensitive Word, Category, Reference, ";
		additionalReportColumns = "";
		super.postInit();
		csUtils = CaseSensitivityUtils.get(true);
	}

	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Case Significance List")
				.withDescription("This report lists all known case sensitive words and phrases, explaining their source")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters(new JobParameters())
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		Map<String, Set<CaseSensitivityUtils.KnowledgeSource>> explanation = csUtils.explainEverything();
		for (Map.Entry<String, Set<CaseSensitivityUtils.KnowledgeSource>> entry : explanation.entrySet()) {
			Set<CaseSensitivityUtils.KnowledgeSource> sources = entry.getValue();
			String sourceString = sources.stream().map(CaseSensitivityUtils.KnowledgeSource::getCategory).reduce((a, b) -> a + ",\n" + b).orElse("");
			String referenceString = sources.stream().map(CaseSensitivityUtils.KnowledgeSource::getReference).reduce((a, b) -> a + ",\n" + b).orElse("");
			report(PRIMARY_REPORT, entry.getKey(), sourceString, referenceString);
		}
	}

}

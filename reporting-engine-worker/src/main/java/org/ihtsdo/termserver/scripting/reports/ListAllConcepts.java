package org.ihtsdo.termserver.scripting.reports;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.scheduler.domain.JobParameter.Type;
import org.snomed.otf.script.dao.ReportSheetManager;

public class ListAllConcepts extends TermServerReport implements ReportClass {

	private static final int MAX_CONCEPTS = 10000;
	private static final String TRUE = "true";
	private static final String FALSE = "false";
	
	public static final String NEW_CONCEPTS_ONLY = "New Concepts Only";
	public static final String EXTENSION_ONLY = "Extension Concepts Only";
	public static final String TERM_TYPES = "Term Type";
	private boolean newConceptsOnly = false;
	private boolean extensionOnly = false;

	private List<String> targetTypes;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, Object> params = new HashMap<>();
		params.put(ECL, "<< 1222584008 MINUS <! 1222584008");
		params.put(NEW_CONCEPTS_ONLY, FALSE);
		params.put(EXTENSION_ONLY, FALSE);
		params.put(TERM_TYPES, List.of("PT"));
		TermServerScript.run(ListAllConcepts.class, params, args);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId(GFOLDER_ADHOC_REPORTS);
		additionalReportColumns="FSN, SemTag, Defn, Descriptions, Inferred Expression, Stated Expression, Parents";
		super.init(run);
		newConceptsOnly = run.getMandatoryParamBoolean(NEW_CONCEPTS_ONLY);
		//We only need a fresh delta export if we're checking for new concepts via the isReleased flag
		this.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(newConceptsOnly);
		extensionOnly = run.getMandatoryParamBoolean(EXTENSION_ONLY);
		targetTypes = run.getParameters().getValues(TERM_TYPES);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.add(NEW_CONCEPTS_ONLY).withType(Type.BOOLEAN).withDefaultValue(false)
				.add(EXTENSION_ONLY).withType(Type.BOOLEAN).withDefaultValue(false)
				.add(TERM_TYPES).withType(JobParameter.Type.CHECKBOXES).withOptions("FSN", "PT", "SYN", "DEFN").withDefaultValues(TRUE, TRUE,FALSE,FALSE)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("List all Concepts")
				.withDescription("This report lists all concepts that match a given ECL, with descriptions, parents and the inferred expression included. " +
						"The issues count will show the number of concepts reported.  This report is limited to 10K concept selections.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		if (StringUtils.isEmpty(subsetECL)) {
			subsetECL = "*";
		}
		
		List<Concept> concepts = SnomedUtils.sortFSN(findConcepts(subsetECL));
		
		if (!extensionOnly && !newConceptsOnly && concepts.size() > MAX_CONCEPTS) {
			throw new TermServerScriptException(concepts.size() + " concepts selected without additional filtering.  Please modify ECL selection to pick up < " + MAX_CONCEPTS);
		}
		
		for (Concept c : concepts) {
			if (!c.isActiveSafely() ||
					(extensionOnly && !inScope(c)) ||
					(newConceptsOnly && c.isReleased())) {
				continue;
			}
			String defn = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
			String parents = getParentsStr(c);
			String descriptionsStr = SnomedUtils.getDescriptionsOfType(c, targetTypes);
			String expressionInf = c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP);
			String expressionSta = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
			report(c, defn, descriptionsStr, expressionInf, expressionSta, parents);
			incrementSummaryInformation("Concepts reported");
			countIssue(c);
		}
	}


	private String getParentsStr(Concept c) {
		Set<Concept> parents = c.getParents(CharacteristicType.INFERRED_RELATIONSHIP);
		return parents.stream().map(Concept::toString)
				.collect(Collectors.joining(",\n"));
	}

}

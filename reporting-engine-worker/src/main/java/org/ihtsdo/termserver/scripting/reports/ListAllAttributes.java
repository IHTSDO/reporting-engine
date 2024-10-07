package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

/**
 * RP-276 
 * Similar to the existing QI report, Farzaneh and I will need an additional report where we can enter an SCTID for the subhierarchy of interest and get the following output:
 *
 * SCTID (for all stated or inferred descendants)
 * FSN
 * Rel Type (stated only)
 * AttValueSCTID
 * AttValueFSN (where semantic tag is substance, medicinal product, medicinal product form, product, or clinical drug) - note that the initial reports will only have substance semantic tag
 * CD-52 Update report to run support concrete values.
 */
public class ListAllAttributes extends TermServerReport implements ReportClass {
		public static final String COMPACT = "Compact Report Format";
		public static final String TARGET_VALUE_PROPERTY = "Target Value Property";
		public static final String SPOTLIGHT_ATTRIBUTE_TYPE = "Spotlight Attribute Type";
		public static final String INCLUDE_IS_A = "Include Is A";
		boolean compactReport = true;
		boolean includeIsA = true;
		Concept targetValueProperty = null;
		Concept spotlightAttributeType = null;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "<< 243796009 |Situation with explicit context (situation)|");
		params.put(COMPACT, "false");
		params.put(INCLUDE_IS_A, "false");
		TermServerScript.run(ListAllAttributes.class, args, params);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String primaryColumns = "SCTID,FSN,SemTag,DefStatus,RelType,Target Value's Property is Present,Stated/Inferred,Target SCTID / Value,Target FSN,Target SemTag";
		if (compactReport) {
			primaryColumns = "SCTID,FSN,SemTag,DefStatus,SCT Expression,Target Value's Property is Present, Spotlight Attribute";
		} 
		String[] columnHeadings = new String[] {primaryColumns, "Spotlighted Attribute Value, Count"};
		String[] tabNames = new String[] {
				"Attribute Details",
				"Spotlight Attribute SummaryCounts"};
		summaryTabIdx = SECONDARY_REPORT;
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc Reports
		subsetECL = run.getMandatoryParamValue(ECL);
		compactReport = run.getParameters().getMandatoryBoolean(COMPACT);
		includeIsA = run.getParameters().getMandatoryBoolean(INCLUDE_IS_A);
		
		String targetValuePropertyStr = run.getParamValue(TARGET_VALUE_PROPERTY);
		if (!StringUtils.isEmpty(targetValuePropertyStr)) {
			targetValueProperty = gl.getConcept(targetValuePropertyStr);
		}
		
		String spotlightAttributeTypeStr = run.getParamValue(SPOTLIGHT_ATTRIBUTE_TYPE);
		if (!StringUtils.isEmpty(spotlightAttributeTypeStr)) {
			spotlightAttributeType = gl.getConcept(spotlightAttributeTypeStr);
			if (!compactReport) {
				throw new TermServerScriptException("No need to specify a spotlight attribute in verbose mode, just filter the report on the attribute type you're interested in");
			}
		}
		
		super.init(run);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL).withMandatory()
				.add(COMPACT).withType(JobParameter.Type.BOOLEAN).withMandatory().withDefaultValue(true)
				.add(INCLUDE_IS_A).withType(JobParameter.Type.BOOLEAN).withMandatory().withDefaultValue(false)
				.add(TARGET_VALUE_PROPERTY).withType(JobParameter.Type.CONCEPT)
				.add(SPOTLIGHT_ATTRIBUTE_TYPE).withType(JobParameter.Type.CONCEPT)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("List all Attributes")
				.withDescription("This report lists all concepts matching the specified ECL, along with their attributes" + 
				" in a compact or expanded report format. The target value property can be specified to determine something additional " +
				" about the target value - the primary use case for this is detecting the presence of the modification attribute on a substance " +
				" used as a product ingredient or causative agent. " +
				" A 'spotlight' attribute type can be specified (only in compact mode), and in this case the value for that attribute type if present will be indicated and counted in a summary tab." +
				"The issue count shows the number of data rows in the report.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		ArrayList<Concept> subset = new ArrayList<>(findConcepts(subsetECL));
		subset.sort(Comparator.comparing(Concept::getFsnSafely));
		for (Concept c : subset) {
			if (!includeIsA && countAttributes(c, CharacteristicType.INFERRED_RELATIONSHIP) == 0) {
				continue;
			}
			
			boolean targetValuePropertyPresent = false;
			if (targetValueProperty != null) {
				targetValuePropertyPresent = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE).stream()
						.filter(Relationship::isNotConcrete)
						.map(r -> checkTargetValuePropertyPresent(r.getTarget()))
						.filter(b -> (b == true))
						.count() > 0;
			}
			
			String spotlightTypeValue = getSpotlightTypeValue(c);
			if (!StringUtils.isEmpty(spotlightTypeValue)) {
				incrementSummaryInformation(spotlightTypeValue);
			}
			
			String defStatus = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
			//Are we working in verbose or compact mode?
			if (compactReport) {
				String expression = c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP);
				String targetValuePropertyPresentStr = "N/A";
				if (targetValueProperty != null) {
					targetValuePropertyPresentStr = targetValuePropertyPresent?"Y":"N";
				}
				report(c, defStatus, expression, targetValuePropertyPresentStr, spotlightTypeValue);
				countIssue(c);
			} else {
				String characteristicStr = "";
				for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
					characteristicStr = "I";
					if (r.isNotConcrete()) {
						if (!includeIsA && r.getType().equals(IS_A)) {
							continue;
						}
						if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, r.getType(), r.getTarget(), ActiveState.ACTIVE).size() > 0) {
							characteristicStr += " + S";
						}
						report(c, r, defStatus, characteristicStr);
					} else {
						report(c, r, defStatus, r.getCharacteristicType().name().substring(0, 1));
					}
				}
				//Are there any relationships which are only stated?
				for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (r.isNotConcrete()) {
						if (!includeIsA && r.getType().equals(IS_A)) {
							continue;
						}
						if (c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, r.getType(), r.getTarget(), ActiveState.ACTIVE).size() == 0) {
							report(c, r, defStatus, "S");
						}
					} else {
						report(c, r, defStatus, r.getCharacteristicType().name().substring(0, 1));
					}
				}
			}
		}
	}

	private void report(Concept c, Relationship r, String defStatus, String characteristicStr) throws TermServerScriptException {
		if (r.isNotConcrete()) {
			Concept target = r.getTarget();
			String fsn = target.getFsn();
			report(c, defStatus, r.getType().getPreferredSynonym(), checkTargetValuePropertyPresent(target) ? "Y" : "N", characteristicStr, target.getConceptId(), fsn, SnomedUtilsBase.deconstructFSN(fsn)[1]);
			countIssue(c);
		} else {
			report(c, defStatus, r.getType().getPreferredSynonym(), "N/A", characteristicStr, r.getConcreteValue().valueAsRF2(), "", "");
			countIssue(c);
		}
	}

	private boolean checkTargetValuePropertyPresent(Concept attributeValue) {
		if (targetValueProperty != null) {
			for (Relationship vR : attributeValue.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (vR.getType().equals(targetValueProperty)) {
					return true;
				}
			}
		}
		return false;
	}
	
	private String getSpotlightTypeValue(Concept v) {
		if (spotlightAttributeType != null) {
			for (Relationship vR : v.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (vR.getType().equals(spotlightAttributeType)) {
					return vR.getTarget().toString();
				}
			}
		}
		return "";
	}

}

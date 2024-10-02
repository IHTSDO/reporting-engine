package org.ihtsdo.termserver.scripting.reports.qi;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.DescendantsCache;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

import java.util.*;
import java.util.stream.Collectors;


public class LexicalModellingMismatch extends TermServerReport implements ReportClass {

	public static final String WORDS = "Words";
	public static final String NOT_WORDS = "Not Words";
	public static final String FSN_ONLY = "Check FSNs Only";
	public static final String ATTRIBUTE_TYPE = "Attribute Type";
	public static final String ATTRIBUTE_VALUE = "Attribute Value";
	
	private List<String> targetWords;
	private List<String> notWords;
	private boolean fsnOnly = false;
	private RelationshipTemplate targetAttribute = new RelationshipTemplate(CharacteristicType.INFERRED_RELATIONSHIP);
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();

		params.put(ECL, "<<  404684003 |Clinical finding (finding)|");
		params.put(WORDS, "chronic");
		params.put(NOT_WORDS, "subchronic");
		params.put(ATTRIBUTE_TYPE, "263502005 |Clinical course (attribute)|");
		params.put(ATTRIBUTE_VALUE, "90734009 |Chronic (qualifier value)|");
		params.put(FSN_ONLY, "true");
		TermServerScript.run(LexicalModellingMismatch.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		super.init(run);
		
		String targetWordsStr = run.getMandatoryParamValue(WORDS).toLowerCase().trim();
		targetWords = Arrays.asList(targetWordsStr.split(COMMA)).stream().map(String::trim).toList();
		
		if (run.getParamValue(NOT_WORDS) != null) {
			String notWordsStr = run.getParamValue(NOT_WORDS).toLowerCase().trim();
			notWords = Arrays.asList(notWordsStr.split(COMMA)).stream().map(String::trim).toList();
		}
		
		fsnOnly = run.getParameters().getMandatoryBoolean(FSN_ONLY);
		
		subsetECL = run.getMandatoryParamValue(ECL);
		String attribStr = run.getParamValue(ATTRIBUTE_TYPE);
		if (attribStr != null && !attribStr.isEmpty()) {
			targetAttribute.setType(gl.getConcept(attribStr));
		}
		
		attribStr = run.getParamValue(ATTRIBUTE_VALUE);
		if (attribStr != null && !attribStr.isEmpty()) {
			try {
				targetAttribute.setTarget(gl.getConcept(attribStr));
			} catch (final IllegalArgumentException e) {
				//Presumed to be concrete value.
				targetAttribute.setConcreteValue(new ConcreteValue(attribStr));
			}
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {"SCTID, FSN, SemTag, Descriptions, Model",
				"SCTID, FSN, SemTag, Model",};
		String[] tabNames = new String[] {"Text without Attribute", "Attribute without Text"};
		super.postInit(GFOLDER_QI, tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL).withMandatory()
				.add(WORDS).withType(JobParameter.Type.STRING).withMandatory()
				.add(NOT_WORDS).withType(JobParameter.Type.STRING)
				.add(FSN_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.add(ATTRIBUTE_TYPE).withType(JobParameter.Type.CONCEPT).withMandatory()
				.add(ATTRIBUTE_VALUE).withType(JobParameter.Type.CONCEPT)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.QI))
				.withName("Lexical Modelling Mismatch")
				.withDescription("This report lists all concepts which either a) feature the target word in the FSN but not the specified attribute or b) feature the specified attribute, but not the word.  Note that target attributes more specific than the one specified will be included in the selection.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		DescendantsCache cache = gl.getDescendantsCache();
		for (Concept c : findConcepts(subsetECL)) {
			if (c.isActiveSafely()) {
				//Skip over any that contain the not-words
				if ((fsnOnly && c.fsnContainsAny(notWords)) ||
					(!fsnOnly && !c.findDescriptionsContaining(notWords, true).isEmpty())) {
					continue;
				}
				checkWordAndAttribute(c, cache);
			}
		}
	}

	private void checkWordAndAttribute(Concept c, DescendantsCache cache) throws TermServerScriptException {
		boolean containsWord = false;

		if ((fsnOnly && c.fsnContainsAny(targetWords)) ||
				(!fsnOnly && !c.findDescriptionsContaining(targetWords).isEmpty())) {
			containsWord = true;
		}

		boolean containsAttribute = SnomedUtils.containsAttributeOrMoreSpecific(c, targetAttribute, cache);

		if (containsWord && !containsAttribute) {
			String descriptions = c.findDescriptionsContaining(targetWords).stream()
					.map(d -> d.getTerm())
					.collect(Collectors.joining(",\n"));
			report(PRIMARY_REPORT, c, descriptions, c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
			countIssue(c);
		} else if (!containsWord && containsAttribute) {
			report(SECONDARY_REPORT, c, c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
			countIssue(c);
		}
	}

}

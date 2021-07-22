package org.ihtsdo.termserver.scripting.reports.qi;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.DescendantsCache;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * INFRA-
 */
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
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		/*
		params.put(ATTRIBUTE_VALUE, "424124008 |Sudden onset AND/OR short duration (qualifier value)|");
		params.put(WORDS, "acute,transient,transitory");
		params.put(NOT_WORDS, "subacute,subtransient");
		params.put(ECL, "<< 138875005 |SNOMED CT Concept (SNOMED RT+CTV3)| MINUS ( <<410607006 |Organism (organism)|)"); */
		
		params.put(ECL, "<<  404684003 |Clinical finding (finding)|");
		params.put(WORDS, "chronic");
		params.put(NOT_WORDS, "subchronic");
		params.put(ATTRIBUTE_TYPE, "263502005 |Clinical course (attribute)|");
		params.put(ATTRIBUTE_VALUE, "90734009 |Chronic (qualifier value)|");
		params.put(FSN_ONLY, "true");
		TermServerReport.run(LexicalModellingMismatch.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1PWtDYFfqLoUwk17HlFgNK648Mmra-1GA"; //General QA
		super.init(run);
		
		String targetWordsStr = run.getMandatoryParamValue(WORDS).toLowerCase().trim();
		targetWords = Arrays.asList(targetWordsStr.split(COMMA)).stream().map(word -> word.trim()).collect(Collectors.toList());
		
		if (run.getParamValue(NOT_WORDS) != null) {
			String notWordsStr = run.getParamValue(NOT_WORDS).toLowerCase().trim();
			notWords = Arrays.asList(notWordsStr.split(COMMA)).stream().map(word -> word.trim()).collect(Collectors.toList());
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
				targetAttribute.setValue(attribStr);
			}
		}
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {"SCTID, FSN, SemTag, Descriptions, Model",
				"SCTID, FSN, SemTag, Model",};
		String[] tabNames = new String[] {"Text without Attribute", "Attribute without Text"};
		super.postInit(tabNames, columnHeadings, false);
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
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.GENERAL_QA))
				.withName("Lexical Modelling Mismatch")
				.withDescription("This report lists all concepts which either a) feature the target word in the FSN but not the specified attribute or b) feature the specified attribute, but not the word.  Note that target attributes more specific than the one specified will be included in the selection.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		DescendantsCache cache = gl.getDescendantsCache();
		for (Concept c : findConcepts(subsetECL)) {
			boolean containsWord = false;
			boolean containsAttribute = false;
			if (c.isActive()) {
				//Skip over any that contain the not-words
				if ((fsnOnly && c.fsnContainsAny(notWords)) ||
					(!fsnOnly && c.findDescriptionsContaining(notWords, true).size() > 0)) {
					continue;
				}
				
				if ((fsnOnly && c.fsnContainsAny(targetWords)) ||
					(!fsnOnly && c.findDescriptionsContaining(targetWords).size() > 0)) {
					containsWord = true;
				}

				
				containsAttribute = SnomedUtils.containsAttributeOrMoreSpecific(c, targetAttribute, cache);
				if (containsWord && !containsAttribute) {
					String descriptions = c.findDescriptionsContaining(targetWords).stream()
							.map(d -> d.getTerm().toString())
							.collect(Collectors.joining(",\n"));
					report (PRIMARY_REPORT, c, descriptions, c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
					countIssue(c);
				} else if (!containsWord && containsAttribute) {
					report (SECONDARY_REPORT, c, c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
					countIssue(c);
				}
			}
		}
	}

}

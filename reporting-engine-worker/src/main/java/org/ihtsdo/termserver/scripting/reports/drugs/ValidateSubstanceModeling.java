package org.ihtsdo.termserver.scripting.reports.drugs;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidateSubstanceModeling extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(ValidateSubstanceModeling.class);

	private static final String[] badWords = new String[] { "preparation", "agent", "+"};

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		TermServerScript.run(ValidateSubstanceModeling.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1bwgl8BkUSdNDfXHoL__ENMPQy_EdEP7d");  //Substances
		additionalReportColumns = "FSN, SemTag, Issue, Data, Detail";  //DRUGS-267
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"SCTID, FSN, Semtag, Issue, Detail",
				"Issue, Count"};
		String[] tabNames = new String[] {
				"Issues",
				"Summary"};
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.DRUGS))
				.withName("Substances validation")
				.withDescription("This report checks for a number of potential inconsistencies in the Substances hierarchy.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		validateSubstancesModeling();
		populateSummaryTab(SECONDARY_REPORT);
		LOGGER.info("Summary tab complete, all done.");
	}

	private void validateSubstancesModeling() throws TermServerScriptException {
		Set<Concept> subHierarchy = SUBSTANCE.getDescendants(NOT_SET);
		for (Concept concept : subHierarchy) {
			DrugUtils.setConceptType(concept);
			validateDisposition(concept);
			checkForBadWords(concept);  //DRUGS-93
		}
		LOGGER.info("Substances validation complete.");
	}

	//Ensure that all stated dispositions exist as inferred, and visa-versa
	private void validateDisposition(Concept concept) throws TermServerScriptException {
		validateAttributeViewsMatch (concept, HAS_DISPOSITION, CharacteristicType.STATED_RELATIONSHIP);

		//If this concept has one or more hasDisposition attributes, check if the inferred parent has the same.
		if (!concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_DISPOSITION, ActiveState.ACTIVE).isEmpty()) {
			validateAttributeViewsMatch (concept, HAS_DISPOSITION, CharacteristicType.INFERRED_RELATIONSHIP);
			checkForOddlyInferredParent(concept, HAS_DISPOSITION, true);
		}
	}

	private void validateAttributeViewsMatch(Concept concept,
			Concept attributeType,
			CharacteristicType fromCharType) throws TermServerScriptException {
		String issueStr = fromCharType.toString() + " has no counterpart";
		initialiseSummary(issueStr);
		//Check that all relationships of the given type "From" match "To"
		CharacteristicType toCharType = fromCharType.equals(CharacteristicType.STATED_RELATIONSHIP)? CharacteristicType.INFERRED_RELATIONSHIP : CharacteristicType.STATED_RELATIONSHIP;
		for (Relationship r : concept.getRelationships(fromCharType, attributeType, ActiveState.ACTIVE)) {
			if (findRelationship(concept, r, toCharType, false) == null) {
				report(concept, issueStr, r.toString());
			}
		}
	}

	/**
	 * list of concepts that have an inferred parent with a stated attribute 
	 * that is not the same as the that of the concept.
	 */
	private void checkForOddlyInferredParent(Concept concept, Concept attributeType, boolean allowMoreSpecific) throws TermServerScriptException {
		String issueStr ="Inferred parent has a stated attribute not stated in child.";
		initialiseSummary(issueStr);
		//Work through inferred parents
		for (Concept parent : concept.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			//Find all STATED attributes of interest
			for (Relationship parentAttribute : parent.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE)) {
				//Does our original concept have that attribute?  Report if not.
				if (null == findRelationship(concept, parentAttribute, CharacteristicType.STATED_RELATIONSHIP, allowMoreSpecific)) {
					report(concept, issueStr, parentAttribute.toString());
					//Reporting one issue per concept is sufficient
					return;
				}
			}
		}
	}

	private Relationship findRelationship(Concept concept, Relationship exampleRel, CharacteristicType charType, boolean allowMoreSpecific) throws TermServerScriptException {
		//Find the first relationship matching the type, target and activeState
		for (Relationship r : concept.getRelationships(charType, exampleRel.getType(),  ActiveState.ACTIVE)) {
			if (allowMoreSpecific) {
				//Does this target value have the example rel as self or ancestor?
				Set<Concept> ancestorsOrSelf = gl.getAncestorsCache().getAncestorsOrSelf(r.getTarget());
				if (ancestorsOrSelf.contains(exampleRel.getTarget())) {
					return r;
				}
			} else if (r.getTarget().equals(exampleRel.getTarget())) {
				return r;
			}
		}
		return null;
	}

	/*
	Need to identify and update:
		FSN beginning with "Product containing" that includes any of the following in any active description:
		agent
		+
		preparation
		product (except in the semantic tag)
	 */
	private void checkForBadWords(Concept concept) throws TermServerScriptException {
		//Check if we're product containing and then look for bad words
		for (Description d : concept.getDescriptions(ActiveState.ACTIVE)) {
			String term = d.getTerm();
			if (d.getType().equals(DescriptionType.FSN)) {
				term = SnomedUtilsBase.deconstructFSN(term)[0];
			}
			for (String badWord : badWords ) {
				String issueStr = "Term contains bad word: " + badWord;
				initialiseSummary(issueStr);
				if (term.contains(badWord)) {
					//Exception, MP PT will finish with word "product"
					if (concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT) && d.isPreferred() && badWord.equals("product")) {
						continue;
					} else {
						if (badWord.equals("+") && isPlusException(term)) {
							continue;
						}
						report(concept, issueStr, d.toString());
						return;
					}
				}
			}
		}
	}

	private boolean isPlusException(String term) {
		//Various rules that allow a + to exist next to other characters
		if (term.contains("^+") ||
			term.contains("+)") ||
			term.contains("+]") ||
			term.contains("(+")) {
			return true;
		}
		return false;
	}

	@Override
	protected boolean report(Concept c, Object...details) throws TermServerScriptException {
		//First detail is the issue
		incrementSummaryCount((String)details[0]);
		countIssue(c);
		return super.report(PRIMARY_REPORT, c, details);
	}
	
}

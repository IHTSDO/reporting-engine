package org.ihtsdo.termserver.scripting.reports.release;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * INFRA-2454, INFRA-2723
 * MAINT-489 Ensure that inactivation indicators are appropriate to historical associations
 * No active historical associations if the concept does not have an inactivation indicator
 * Inactivated as "Non conformance to Ed Policy" should have no historical associations
 * Inactivated as "Ambiguous" should have [1..*] Possibly Equivalent to associations
 * Inactivated as "Component move elsewhere" should have [1...1] Moved To associations
 * Inactivated as "Duplicate" should have [1...1] Same As associations
 * Inactivated as "Erroneous" or "Outdated" should have [1...1] Replaced By associations
 * Inactivated as "Limited Component" should have [1..*] Was A associations
 * 
 * Update: as pointed out by Jeremy Rogers (thanks Jeremy!) we have a small number of hist
 * assocs which are NOT "MovedTo", but are pointing to some namespace concept.  This is obviously wrong.
 * 
 * A legacy issue is defined as one that existed in the previous release
 * So if EITHER the concept or the inactivation indicator OR the target has changed in this
 * release, then it's a new issue (we're saying).
 * 
 * RP-536
 * Ambiguous concepts can now have [1..*] POSSIBLY_EQUIVALENT_TO associations
 * Concepts "Moved To"  can now have [0..1] Alternative associations
 * Concepts "Concept Unknown" is a new inactivation reason which will take [0..0] associations.
 * NCEP concepts can now optionally feature [0..1] Replaced By  OR  [0..1] Alternative    have made comment in document asking for explicit confirmation of cardinality and cross validation.
 * New "Classification derived concept" can take [0..1] Replaced by   OR [2..*] Partially Equivalent To associations
 * Outdated Concepts can take [0..1] Replaced by   OR [2..*] Possibly Replaced By associations
 *
 * RP-568 Inactivation reason "Concept Moved Elsewhere" will no longer be used, neither will association "Moved To"
 */
public class ValidateInactivationsWithAssociations extends TermServerReport implements ReportClass {
	
	boolean includeLegacyIssues = false;
	Set<Concept> namespaceConcepts;
	public static final String CARDINALITY_ISSUE = "Cardinality constraint breached.  Expected ";
	
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(SUB_HIERARCHY, ROOT_CONCEPT.toString());
		params.put(INCLUDE_ALL_LEGACY_ISSUES, "true");
		TermServerReport.run(ValidateInactivationsWithAssociations.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		includeLegacyIssues = run.getParameters().getMandatoryBoolean(INCLUDE_ALL_LEGACY_ISSUES);
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA
		this.summaryTabIdx = PRIMARY_REPORT;
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Issue, Count",
				"SCTID, FSN, SemTag, Concept EffectiveTime, Issue, isLegacy (C/D), Data, Data"};
		String[] tabNames = new String[] {	
				"Summary", "Issues"};
		super.postInit(tabNames, columnHeadings, false);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(INCLUDE_ALL_LEGACY_ISSUES)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue(false)
				.add(UNPROMOTED_CHANGES_ONLY)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue(false)
				.build();

		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("Validate Inactivations with Associations")
				.withDescription("This report ensures that inactive concepts have exactly one inactivation indicator, and appropriate historical associations. " +
								"The 'Issues' count here reflects the number of rows in the report.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		namespaceConcepts = gl.getDescendantsCache().getDescendentsOrSelf(NAMESPACE_CONCEPT);
		List<Concept> concepts = SnomedUtils.sort(gl.getAllConcepts());
		for (Concept c : concepts) {
			if (whiteListedConcepts.contains(c)) {
				incrementSummaryInformation(WHITE_LISTED_COUNT);
				continue;
			}
			
			boolean isLegacy = isLegacy(c);
			if (!c.isActive() && inScope(c)) {
				//Are we only interested in concepts that have any new inactivation indicator?
				if (!includeLegacyIssues && isLegacy) {
					continue;
				}
				
				//Ensure that association target is active.   To ignore legacy issues, both the 
				//concept AND the target must be legacy
				for (AssociationEntry a : c.getAssociations(ActiveState.ACTIVE, true)) {
					Concept target = gl.getConcept(a.getTargetComponentId(), false, false);
					if (target == null) {
						incrementSummaryInformation("Inactive concept association target not a concept");
					} else if (!target.isActive()) {
						String targetStr = SnomedUtils.translateAssociation(a.getRefsetId()) + " " + target.toString();
						incrementSummaryInformation("Inactive concept association target is inactive");
						report(SECONDARY_REPORT, c, c.getEffectiveTime(), "Concept has inactive association target", isLegacy, targetStr, c.getInactivationIndicator().toString() + "\n" + a);
						countIssue(c);
					}
				}
				
				if (c.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() == 0) {
					incrementSummaryInformation("Inactive concept missing inactivation indicator");
					report (SECONDARY_REPORT, c, c.getEffectiveTime(), "Inactive concept missing inactivation indicator", isLegacy);
					countIssue(c);
				} else if (c.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() > 1) {
					String data = c.getInactivationIndicatorEntries(ActiveState.ACTIVE).stream()
							.map(i->i.toString())
							.collect(Collectors.joining(",\n"));
					String issue = "Concept has multiple inactivation indicators";
					incrementSummaryInformation(issue);
					report(SECONDARY_REPORT, c, c.getEffectiveTime(), issue, isLegacy, data);
					countIssue(c);
				} else {
					InactivationIndicatorEntry i = c.getInactivationIndicatorEntries(ActiveState.ACTIVE).iterator().next(); 
					List<AssociationCardinality> associationsWithCardinality = new ArrayList<>();
 					switch (i.getInactivationReasonId()) {
						case SCTID_INACT_AMBIGUOUS : 
							associationsWithCardinality.add(new AssociationCardinality("1..*", SCTID_ASSOC_POSS_EQUIV_REFSETID, true));
							validate(c, i, associationsWithCardinality, isLegacy);
							break;
						case SCTID_INACT_NON_CONFORMANCE :
							associationsWithCardinality.add(new AssociationCardinality("0..1", SCTID_ASSOC_REPLACED_BY_REFSETID, true));
							associationsWithCardinality.add(new AssociationCardinality("0..*", SCTID_ASSOC_ALTERNATIVE_REFSETID, true));
							validate(c, i, associationsWithCardinality, isLegacy);
							break;
						case SCTID_INACT_COMPONENT_MEANING_UNKNOWN :
							associationsWithCardinality.add(new AssociationCardinality("0..0", null, true));
							validate(c, i, associationsWithCardinality, isLegacy);
							break;
						case SCTID_INACT_CLASS_DERIVED_COMPONENT :
							associationsWithCardinality.add(new AssociationCardinality("0..1", SCTID_ASSOC_REPLACED_BY_REFSETID, true));
							associationsWithCardinality.add(new AssociationCardinality("2..*", SCTID_ASSOC_PARTIALLY_EQUIV_REFSETID, true));
							validate(c, i, associationsWithCardinality, isLegacy);
							break;
						case SCTID_INACT_MOVED_ELSEWHERE :
							if (isLegacy) {
								associationsWithCardinality.add(new AssociationCardinality("1..1", SCTID_ASSOC_MOVED_TO_REFSETID, false));
								associationsWithCardinality.add(new AssociationCardinality("0..*", SCTID_ASSOC_ALTERNATIVE_REFSETID, false));
								validate(c, i, associationsWithCardinality, isLegacy);
							} else {
								String issue = "Moved Elsewhere indicator no longer used";
								incrementSummaryInformation(issue);
								report(SECONDARY_REPORT, c, c.getEffectiveTime(), issue, "N");
								countIssue(c);
								associationsWithCardinality.add(new AssociationCardinality("0..*", SCTID_ASSOC_ALTERNATIVE_REFSETID, true));
								validate(c, i, associationsWithCardinality, isLegacy);
							}
							break;
						case SCTID_INACT_DUPLICATE :
							associationsWithCardinality.add(new AssociationCardinality("1..1", SCTID_ASSOC_SAME_AS_REFSETID, true));
							validate(c, i, associationsWithCardinality, isLegacy);
							break;
						case SCTID_INACT_LIMITED :
							if (isLegacy) {
								associationsWithCardinality.add(new AssociationCardinality("1..1", SCTID_ASSOC_WAS_A_REFSETID, true));
								validate(c, i, associationsWithCardinality, isLegacy);
							} else {
								String issue = "Limited indicator no longer used";
								incrementSummaryInformation(issue);
								report(SECONDARY_REPORT, c, c.getEffectiveTime(), issue, "N");
								countIssue(c);
							}
							break;
						case SCTID_INACT_ERRONEOUS :
							associationsWithCardinality.add(new AssociationCardinality("1..1", SCTID_ASSOC_REPLACED_BY_REFSETID, true));
							validate(c, i, associationsWithCardinality, isLegacy);
							break;
						case SCTID_INACT_OUTDATED :
							associationsWithCardinality.add(new AssociationCardinality("0..1", SCTID_ASSOC_REPLACED_BY_REFSETID, true));
							associationsWithCardinality.add(new AssociationCardinality("2..*", SCTID_ASSOC_POSS_REPLACED_BY_REFSETID, true));
							validate(c, i, associationsWithCardinality, isLegacy);
							break;
						default :
							String issue = "Unrecognised concept inactivation indicator";
							incrementSummaryInformation(issue);
							report(SECONDARY_REPORT, c, c.getEffectiveTime(), issue, isLegacy, i);
							countIssue(c);
					}
				}
			}
			
			//In all cases, run a check on the descriptions as well
			//Inactivation indicators should have an inactivation indicator
			//Active descriptions must have one if the concept is inactive
			
			nextDescription:
			for (Description d : c.getDescriptions()) {
				if (inScope(d)) {
					String cdLegacy = (isLegacy?"Y":"N") + "/" + (isLegacy(d)?"Y":"N"); 
					//What are we looking at here?
					String data = c.getInactivationIndicatorEntries(ActiveState.ACTIVE).stream()
							.map(i->i.toString())
							.collect(Collectors.joining(",\n"));
					
					//First check that any indicators are only in the appropriate refset
					for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries(ActiveState.ACTIVE)) {
						if (!i.getRefsetId().equals(SCTID_DESC_INACT_IND_REFSET)) {
							String issue = "Description has something other than a description inactivation indicator";
							incrementSummaryInformation(issue);
							report(SECONDARY_REPORT, c, c.getEffectiveTime(), d, issue, cdLegacy, d, i);
							countIssue(c);
						}
						continue nextDescription;
					}
					
					// Now check for duplicates, followed by acceptable entries
					if (d.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() > 1) {
						String issue = "Description has multiple inactivation indicators";
						incrementSummaryInformation(issue);
						report(SECONDARY_REPORT, c, c.getEffectiveTime(), issue, cdLegacy, d, data);
						countIssue(c);
					} else if (d.isActive() && !c.isActive()) {
						//Expect a single "Concept not current" indicator
						if (d.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() == 0) {
							String issue = "Active description of inactive concept is missing 'Concept non-current' indicator";
							incrementSummaryInformation(issue);
							report(SECONDARY_REPORT, c, c.getEffectiveTime(), issue, cdLegacy, d);
							countIssue(c);
						} else {
							InactivationIndicatorEntry i = d.getInactivationIndicatorEntries(ActiveState.ACTIVE).iterator().next(); 
							if (!i.getInactivationReasonId().equals(SCTID_INACT_CONCEPT_NON_CURRENT)) {
								report(SECONDARY_REPORT, c, c.getEffectiveTime(), "Active description of inactive concept has something other than a 'Concept non-current' indicator", cdLegacy, d, i);
								countIssue(c);
							}
						}
					} else if (d.isActive() && c.isActive()) {
						//Expect NO inactivation indicator here
						if (d.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() > 0) {
							report(SECONDARY_REPORT, c, c.getEffectiveTime(), d, "Active description of active concept should not have an inactivation indicator", cdLegacy, d, data);
							countIssue(c);
						}
					} else if (!d.isActive() && !c.isActive()) {
						if (!isLegacy(d) || includeLegacyIssues) {
							//Expect inactivation indicator here, but not Concept-non-current
							if (d.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() != 1) {
								report (SECONDARY_REPORT, c, c.getEffectiveTime(), "Inactive description of active concept should have an inactivation indicator", cdLegacy, d);
								incrementSummaryInformation("Inactive description of active concept should have an inactivation indicator");
								countIssue(c);
							} else {
								InactivationIndicatorEntry i = d.getInactivationIndicatorEntries(ActiveState.ACTIVE).iterator().next(); 
								if (i.getInactivationReasonId().equals(SCTID_INACT_CONCEPT_NON_CURRENT)) {
									String issue = "Inactive description of an active concept should not have ga 'Concept non-current' indicator";
									incrementSummaryInformation(issue);
									report(SECONDARY_REPORT, c, c.getEffectiveTime(), issue, cdLegacy, d, i);
									countIssue(c);
								}
							}
						}
					}
				}
			}
		}
	}
	
	private void validate(Concept c, InactivationIndicatorEntry i, List<AssociationCardinality> associationsWithCardinality, Boolean legacy) throws TermServerScriptException {
		String data = c.getAssociations(ActiveState.ACTIVE).stream()
				.map(h->h.toString())
				.collect(Collectors.joining(",\n"));
		
		if (StringUtils.isEmpty(data)) {
			data = "No historical associations defined";
		}
		
		//Add in the inactivation indicator
		if (i != null) {
			data = SnomedUtils.translateInactivationIndicator(i.getInactivationReasonId()) + "\n" + data;
		}
		
		String targets = c.getAssociations(ActiveState.ACTIVE).stream()
				.map(h-> SnomedUtils.translateAssociation(h.getRefsetId()).toString() + " " + gl.getConceptSafely(h.getTargetComponentId()).toString())
				.collect(Collectors.joining(",\n"));
		
		if (StringUtils.isEmpty(targets)) {
			targets = "No historical associations defined";
		}
		
		//Loop through all possible associations.  Don't report issue unless all fail
		String lastIssue = "";
		boolean onePassed = false;
		for (AssociationCardinality associationWithCardinality : associationsWithCardinality) {
			lastIssue = validate(c, i, associationWithCardinality.cardinality, associationWithCardinality.association, legacy, associationWithCardinality.mutuallyExclusive);
			if (lastIssue.isEmpty()) {
				onePassed = true;
			}
		}
		if (!onePassed && !lastIssue.isEmpty()) {
			if (lastIssue.equals(CARDINALITY_ISSUE)) {
				String separator = associationsWithCardinality.get(0).mutuallyExclusive == true ? " OR \n" : " AND/OR \n";
				lastIssue += associationsWithCardinality.stream()
						.map(ac -> ac.toString())
						.collect(Collectors.joining(separator));
			}
			incrementSummaryInformation(lastIssue);
			report(SECONDARY_REPORT, c, c.getEffectiveTime(), lastIssue, legacy, targets, data);
			countIssue(c);
		}
	}

	private String validate(Concept c, InactivationIndicatorEntry i, String cardinality, String assocId, Boolean legacy, boolean mutuallyExclusive) throws TermServerScriptException {
		int minAssocs = Character.getNumericValue(cardinality.charAt(0));
		char maxStr = cardinality.charAt(cardinality.length() - 1);
		int maxAssocs = maxStr == '*' ? Integer.MAX_VALUE : Character.getNumericValue(maxStr);
		String inactStr = SnomedUtils.translateInactivationIndicator(i.getInactivationReasonId()).toString();
		String reqAssocStr = assocId == null? "None" : SnomedUtils.translateAssociation(assocId).toString();
		
		//Special case, we're getting limited inactivations with both SameAs and Was A attributes.  Record stats, but skip
		if (i.getInactivationReasonId().equals(SCTID_INACT_LIMITED) || i.getInactivationReasonId().equals(SCTID_INACT_MOVED_ELSEWHERE)) {
			String typesOfAssoc = c.getAssociations(ActiveState.ACTIVE).stream()
					.map(h->SnomedUtils.translateAssociation(h.getRefsetId()).toString())
					.collect(Collectors.joining(", "));
			typesOfAssoc = typesOfAssoc.isEmpty()? "No associations" : typesOfAssoc;
			//incrementSummaryInformation(inactStr +" inactivation with " + typesOfAssoc);
			return "";
		}
		
		//First check cardinality
		int assocCount = c.getAssociations(ActiveState.ACTIVE, assocId).size();
		int allAssocCount = c.getAssociations(ActiveState.ACTIVE, true).size();
		
		if (assocCount > 0 && assocCount != allAssocCount && mutuallyExclusive) {
			return inactStr + " inactivation's " + reqAssocStr + " association is mutually exclusive with other associations types.";
		}
		
		if (assocCount > maxAssocs) {
			//return inactStr + " inactivation must have no more than " + maxStr + " historical associations of type " + reqAssocStr;
			return CARDINALITY_ISSUE;
		} else if (assocCount < minAssocs) {
			//return inactStr + " inactivation must have at least " + minAssocs + " historical associations of type " + reqAssocStr;
			return CARDINALITY_ISSUE;
		} else {
			//Now check association is appropriate for the inactivation indicator used
			for (AssociationEntry h : c.getAssociations(ActiveState.ACTIVE, true)) {
				String assocStr = SnomedUtils.translateAssociation(h.getRefsetId()).toString();
				Concept target = gl.getConcept(h.getTargetComponentId());
				//Only the "MovedTo" historical association should point to a Namespace Concept
				if (!h.getRefsetId().equals(SCTID_ASSOC_MOVED_TO_REFSETID) && namespaceConcepts.contains(target)) {
					String msg = assocStr + " should not point to namespace concept: " + target;
					return msg;
				} else if (!h.getRefsetId().equals(assocId)) {
					//If we found a "WAS_A" then just record the stat for that
					String msg = inactStr + " inactivation requires " + reqAssocStr + " historical association.  Found: " + assocStr;
					if (assocStr.equals(Association.WAS_A.toString()) && !inactStr.equals(InactivationIndicator.AMBIGUOUS.toString())) {
						incrementSummaryInformation(msg);
					} else {
						return msg;
					}
				}
				
				if (!h.getRefsetId().equals(SCTID_ASSOC_MOVED_TO_REFSETID)) {
					//In fact, so much of this has occurred historically that it would be a huge
					//undertaking to review them all.
					//validateTargetAppropriate(c,assocStr, target, h);
				}
			}
		}
		return "";
	}
	
/*	private void validateTargetAppropriate(Concept c, String assocStr, Concept target, AssociationEntry h) throws TermServerScriptException {
		//What did this concept used to be?
		Concept wasA = SnomedUtils.getHistoricalParent(c);
		if (wasA != null) {
			Concept topLevelSource = SnomedUtils.getHighestAncestorBefore(wasA, ROOT_CONCEPT);
			Concept topLevelTarget = SnomedUtils.getHighestAncestorBefore(target, ROOT_CONCEPT);
			
			if (topLevelSource != null && topLevelTarget != null 
					&& !topLevelSource.equals(SPECIAL_CONCEPT)
					&& !topLevelTarget.equals(SPECIAL_CONCEPT)
					&& !topLevelSource.equals(topLevelTarget)) {
				String msg = assocStr + " pointing to target in other top level hierarchy: " + target;
				report (c, c.getEffectiveTime(), msg);
				countIssue(c);
			}
		} else {
			if (c.getRelationships().size() > 0) {
				warn ("Unable to determine historical parent of " + c);
			}
		}
	}*/
	
	private boolean isLegacy(Component c) throws TermServerScriptException {
		//If any relationship, description or historical association
		//has been modified, then this is not a legacy issue
		if (StringUtils.isEmpty(c.getEffectiveTime())) {
			return false;
		}
		
		if (c instanceof Concept) {
			Concept concept = (Concept)c;
		
			for (Description d : concept.getDescriptions()) {
				if (StringUtils.isEmpty(d.getEffectiveTime())) {
					return false;
				}
			}
			
			for (Relationship r : concept.getRelationships()) {
				if (StringUtils.isEmpty(r.getEffectiveTime())) {
					return false;
				}
			}
			
			for (AssociationEntry a : concept.getAssociationEntries()) {
				if (StringUtils.isEmpty(a.getEffectiveTime())) {
					return false;
				}
				//Also look up the target of the association because it might have changed
				Concept target = gl.getConcept(a.getTargetComponentId(), false, false);
				if (target != null && StringUtils.isEmpty(target.getEffectiveTime())) {
					return false;
				}
			}
			
			for (InactivationIndicatorEntry i : concept.getInactivationIndicatorEntries()) {
				if (StringUtils.isEmpty(i.getEffectiveTime())) {
					return false;
				}
			}
		}
		return true;
	}
	
	class AssociationCardinality {
		String cardinality;
		String association;
		boolean mutuallyExclusive;
		
		AssociationCardinality(String cardinality, String association, boolean mutuallyExclusive) {
			this.cardinality = cardinality;
			this.association = association;
			this.mutuallyExclusive = mutuallyExclusive;
		}
		
		public String toString() {
			return "[" + cardinality + "] " + gl.getConceptSafely(association).getPreferredSynonym(); 
		}
	}

}

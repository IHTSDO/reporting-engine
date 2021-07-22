package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.springframework.util.StringUtils;

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
 */
public class ValidateInactivationsWithAssociations extends TermServerReport implements ReportClass {
	
	boolean includeLegacyIssues = false;
	Set<Concept> namespaceConcepts;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(SUB_HIERARCHY, ROOT_CONCEPT.toString());
		params.put(INCLUDE_ALL_LEGACY_ISSUES, "true");
		TermServerReport.run(ValidateInactivationsWithAssociations.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		includeLegacyIssues = run.getParameters().getMandatoryBoolean(INCLUDE_ALL_LEGACY_ISSUES);
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA
		additionalReportColumns="FSN, SemTag, Concept EffectiveTime, Issue, isLegacy (C/D), Data, Data";
		super.init(run);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(INCLUDE_ALL_LEGACY_ISSUES)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue(true)
				.build();

		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("Validate Inactivations with Associations")
				.withDescription("This report ensures that inactive concepts have exactly one inactivation indicator, and appropriate historical associations. " +
								"The 'Issues' count here reflects the number of rows in the report.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		namespaceConcepts = gl.getDescendantsCache().getDescendentsOrSelf(NAMESPACE_CONCEPT);
		for (Concept c : gl.getAllConcepts()) {
			if (whiteListedConcepts.contains(c)) {
				incrementSummaryInformation(WHITE_LISTED_COUNT);
				continue;
			}
			
			boolean isLegacy = isLegacy(c);
			if (!c.isActive()) {
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
						incrementSummaryInformation("Inactive concept association target is inactive");
						report (c, c.getEffectiveTime(), "Concept has inactive association target", isLegacy, target, a);
						countIssue(c);
					}
				}
				
				if (c.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() == 0) {
					incrementSummaryInformation("Inactive concept missing inactivation indicator");
					//report (c, c.getEffectiveTime(), "Inactive concept missing inactivation indicator", isLegacy);
				} else if (c.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() > 1) {
					String data = c.getInactivationIndicatorEntries(ActiveState.ACTIVE).stream()
							.map(i->i.toString())
							.collect(Collectors.joining(",\n"));
					report (c, c.getEffectiveTime(), "Concept has multiple inactivation indicators", isLegacy, data);
					countIssue(c);
				} else {
					InactivationIndicatorEntry i = c.getInactivationIndicatorEntries(ActiveState.ACTIVE).iterator().next(); 
					switch (i.getInactivationReasonId()) {
						case SCTID_INACT_AMBIGUOUS : 
							validate(c, i, "1..*", SCTID_ASSOC_POSS_EQUIV_REFSETID, isLegacy);
							break;
						case SCTID_INACT_NON_CONFORMANCE :
							validate(c, i, "0..0", null, isLegacy);
							break;
						case SCTID_INACT_MOVED_ELSEWHERE :
							validate(c, i, "1..1", SCTID_ASSOC_MOVED_TO_REFSETID, isLegacy);
							break;
						case SCTID_INACT_DUPLICATE :
							validate(c, i, "1..1", SCTID_ASSOC_SAME_AS_REFSETID, isLegacy);
							break;
						case SCTID_INACT_LIMITED : 
							validate(c, i, "1..1", SCTID_ASSOC_WAS_A_REFSETID, isLegacy);
							break;
						case SCTID_INACT_ERRONEOUS :
						case SCTID_INACT_OUTDATED :
							validate(c, i, "1..1", SCTID_ASSOC_REPLACED_BY_REFSETID, isLegacy);
							break;
						default :
							report (c, c.getEffectiveTime(), "Unrecognised concept inactivation indicator", isLegacy, i);
							countIssue(c);
					}
				}
			}
			
			//In all cases, run a check on the descriptions as well
			//Inactivation indicators should have an inactivation indicator
			//Active descriptions might have one if the concept is inactive
			
			nextDescription:
			for (Description d : c.getDescriptions()) {
				String cdLegacy = (isLegacy?"Y":"N") + "/" + (isLegacy(d)?"Y":"N"); 
				//What are we looking at here?
				String data = c.getInactivationIndicatorEntries(ActiveState.ACTIVE).stream()
						.map(i->i.toString())
						.collect(Collectors.joining(",\n"));
				
				//First check that any indicators are only in the appropriate refset
				for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries(ActiveState.ACTIVE)) {
					if (!i.getRefsetId().equals(SCTID_DESC_INACT_IND_REFSET)) {
						report (c, c.getEffectiveTime(), d, "Description has something other than a description inactivation indicator", cdLegacy, d, i);
						countIssue(c);
					}
					continue nextDescription;
				}
				
				// Now check for duplicates, followed by acceptable entries
				if (d.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() > 1) {
					report (c, c.getEffectiveTime(), "Description has multiple inactivation indicators", cdLegacy, d, data);
				} else if (d.isActive() && !c.isActive()) {
					//Expect a single "Concept not current" indicator
					if (d.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() == 0) {
						report (c, c.getEffectiveTime(), "Active description of inactive concept is missing 'Concept non-current' indicator", cdLegacy, d);
						countIssue(c);
					} else {
						InactivationIndicatorEntry i = d.getInactivationIndicatorEntries(ActiveState.ACTIVE).iterator().next(); 
						if (!i.getInactivationReasonId().equals(SCTID_INACT_CONCEPT_NON_CURRENT)) {
							report (c, c.getEffectiveTime(), "Active description of inactive concept has something other than a 'Concept non-current' indicator", cdLegacy, d, i);
							countIssue(c);
						}
					}
				} else if (d.isActive() && c.isActive()) {
					//Expect NO inactivation indicator here
					if (d.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() > 0) {
						report (c, c.getEffectiveTime(), d, "Active description of active concept should not have an inactivation indicator", cdLegacy, d, data);
						countIssue(c);
					}
				} else if (!d.isActive() && !c.isActive()) {
					//Expect inactivation indicator here, but not Concept-non-current
					if (d.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() != 1) {
						//report (c, c.getEffectiveTime(), d, "Inactive description of active concept should have an inactivation indicator", cdLegacy, d);
						incrementSummaryInformation("Inactive description of active concept should have an inactivation indicator");
					} else {
						InactivationIndicatorEntry i = d.getInactivationIndicatorEntries(ActiveState.ACTIVE).iterator().next(); 
						if (i.getInactivationReasonId().equals(SCTID_INACT_CONCEPT_NON_CURRENT)) {
							report (c, c.getEffectiveTime(), d, "Inactive description of an active concept should not have a 'Concept non-current' indicator", cdLegacy, d, i);
							countIssue(c);
						}
					}
				}
			}
		}
	}

	private void validate(Concept c, InactivationIndicatorEntry i, String cardinality, String requiredAssociation, Boolean legacy) throws TermServerScriptException {
		int minAssocs = Character.getNumericValue(cardinality.charAt(0));
		char maxStr = cardinality.charAt(cardinality.length() - 1);
		int maxAssocs = maxStr == '*' ? Integer.MAX_VALUE : Character.getNumericValue(maxStr);
		
		String data = c.getAssociations(ActiveState.ACTIVE).stream()
				.map(h->h.toString())
				.collect(Collectors.joining(",\n"));
		String inactStr = SnomedUtils.translateInactivationIndicator(i.getInactivationReasonId()).toString();
		String reqAssocStr = requiredAssociation == null? "None" : SnomedUtils.translateAssociation(requiredAssociation).toString();
		
		//Special case, we're getting limited inactivations with both SameAs and Was A attributes.  Record stats, but skip
		if (i.getInactivationReasonId().equals(SCTID_INACT_LIMITED) || i.getInactivationReasonId().equals(SCTID_INACT_MOVED_ELSEWHERE)) {
			String typesOfAssoc = c.getAssociations(ActiveState.ACTIVE).stream()
					.map(h->SnomedUtils.translateAssociation(h.getRefsetId()).toString())
					.collect(Collectors.joining(", "));
			typesOfAssoc = typesOfAssoc.isEmpty()? "No associations" : typesOfAssoc;
			incrementSummaryInformation(inactStr +" inactivation with " + typesOfAssoc);
			return;
		}
		
		//First check cardinality
		int assocCount = c.getAssociations(ActiveState.ACTIVE, true).size();
		if (assocCount > maxAssocs) {
			report (c, c.getEffectiveTime(), inactStr + " inactivation must have no more than " + maxStr + " historical associations.", legacy,  data);
			countIssue(c);
		} else if  (assocCount < minAssocs) {
			report (c, c.getEffectiveTime(), inactStr + " inactivation must have at least " + minAssocs + " historical associations.", legacy, data);
			countIssue(c);
		} else {
			//Now check association is appropriate for the inactivation indicator used
			for (AssociationEntry h : c.getAssociations(ActiveState.ACTIVE, true)) {
				String assocStr = SnomedUtils.translateAssociation(h.getRefsetId()).toString();
				Concept target = gl.getConcept(h.getTargetComponentId());
				//Only the "MovedTo" historical association should point to a Namespace Concept
				if (!h.getRefsetId().equals(SCTID_ASSOC_MOVED_TO_REFSETID) && namespaceConcepts.contains(target)) {
					String msg = assocStr + " should not point to namespace concept: " + target;
					report (c, c.getEffectiveTime(), msg, legacy, data);
					countIssue(c);
				} else if (!h.getRefsetId().equals(requiredAssociation)) {
					//If we found a "WAS_A" then just record the stat for that
					String msg = inactStr + " inactivation requires " + reqAssocStr + " historical association.  Found: " + assocStr;
					if (assocStr.equals(Association.WAS_A.toString()) && !inactStr.equals(InactivationIndicator.AMBIGUOUS.toString())) {
						incrementSummaryInformation(msg);
					} else {
						report (c, c.getEffectiveTime(), msg, legacy, data);
						countIssue(c);
					}
				}
				
				if (!h.getRefsetId().equals(SCTID_ASSOC_MOVED_TO_REFSETID)) {
					//In fact, so much of this has occurred historically that it would be a huge
					//undertaking to review them all.
					//validateTargetAppropriate(c,assocStr, target, h);
				}
			}
		}
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
				warn ("Unable to determine histroical parent of " + c);
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

}

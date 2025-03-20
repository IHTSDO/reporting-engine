package org.ihtsdo.termserver.scripting.reports.release;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
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

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(SUB_HIERARCHY, ROOT_CONCEPT.toString());
		params.put(INCLUDE_ALL_LEGACY_ISSUES, "true");
		params.put(UNPROMOTED_CHANGES_ONLY, "true");
		TermServerScript.run(ValidateInactivationsWithAssociations.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		includeLegacyIssues = run.getParameters().getMandatoryBoolean(INCLUDE_ALL_LEGACY_ISSUES);
		ReportSheetManager.setTargetFolderId("15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"); //Release QA
		this.summaryTabIdx = PRIMARY_REPORT;
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Issue, Count",
				"SCTID, FSN, SemTag, Concept EffectiveTime, Issue, isLegacy (C/D), Data, Data"};
		String[] tabNames = new String[] {	
				"Summary", "Issues"};
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(INCLUDE_ALL_LEGACY_ISSUES)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue(false)
				.add(UNPROMOTED_CHANGES_ONLY)
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
				.withTag(MS)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		namespaceConcepts = gl.getDescendantsCache().getDescendantsOrSelf(NAMESPACE_CONCEPT);
		List<Concept> concepts = SnomedUtils.sort(gl.getAllConcepts());
		for (Concept c : concepts) {
			//Are we checking only unpromoted changes?  Either d or the already known
			//term can be unpromoted to qualify
			if (unpromotedChangesOnly && !unpromotedChangesHelper.hasUnpromotedChange(c)) {
				continue;
			}
			
			if (whiteListedConceptIds.contains(c.getId())) {
				incrementSummaryInformation(WHITE_LISTED_COUNT);
				continue;
			}
			
			boolean isLegacy = isLegacy(c);
			if (!c.isActiveSafely() && inScope(c)) {
				//Are we only interested in concepts that have any new inactivation indicator?
				if (!includeLegacyIssues && isLegacy) {
					continue;
				}
				
				//Ensure that association target is active.   To ignore legacy issues, both the 
				//concept AND the target must be legacy
				for (AssociationEntry a : c.getAssociationEntries(ActiveState.ACTIVE, true)) {
					Concept target = gl.getConcept(a.getTargetComponentId(), false, false);
					if (target == null) {
						incrementSummaryInformation("Inactive concept association target not a concept");
					} else if (!target.isActiveSafely()) {
						String targetStr = SnomedUtils.translateAssociation(a.getRefsetId()) + " " + target.toString();
						incrementSummaryInformation("Inactive concept association target is inactive");
						report(SECONDARY_REPORT, c, c.getEffectiveTime(), "Concept has inactive association target", isLegacy, targetStr, c.getInactivationIndicator().toString() + "\n" + a);
						countIssue(c);
					}
				}
				
				if (c.getInactivationIndicatorEntries(ActiveState.ACTIVE).isEmpty()) {
					incrementSummaryInformation("Inactive concept missing inactivation indicator");
					report(SECONDARY_REPORT, c, c.getEffectiveTime(), "Inactive concept missing inactivation indicator", isLegacy);
					countIssue(c);
				} else if (c.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() > 1) {
					String data = c.getInactivationIndicatorEntries(ActiveState.ACTIVE).stream()
							.map(InactivationIndicatorEntry::toString)
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
					    case SCTID_INACT_CLASS_DERIVED_COMPONENT :
						    associationsWithCardinality.add(new AssociationCardinality("0..1", SCTID_ASSOC_REPLACED_BY_REFSETID, true));
						    associationsWithCardinality.add(new AssociationCardinality("2..*", SCTID_ASSOC_PART_EQUIV_REFSETID, true));
						    validate(c, i, associationsWithCardinality, isLegacy);
						    break;
					    case SCTID_INACT_DUPLICATE :
						    associationsWithCardinality.add(new AssociationCardinality("1..1", SCTID_ASSOC_SAME_AS_REFSETID, true));
						    validate(c, i, associationsWithCardinality, isLegacy);
						    break;
					    case SCTID_INACT_ERRONEOUS :
						    associationsWithCardinality.add(new AssociationCardinality("1..1", SCTID_ASSOC_REPLACED_BY_REFSETID, true));
						    validate(c, i, associationsWithCardinality, isLegacy);
						    break;
					    case SCTID_INACT_MEANING_OF_COMPONENT_UNKNOWN:
						    associationsWithCardinality.add(new AssociationCardinality("0..0", null, true));
						    validate(c, i, associationsWithCardinality, isLegacy);
						    break;
					    case SCTID_INACT_NON_CONFORMANCE :
						    associationsWithCardinality.add(new AssociationCardinality("0..1", SCTID_ASSOC_REPLACED_BY_REFSETID, true));
						    associationsWithCardinality.add(new AssociationCardinality("0..*", SCTID_ASSOC_ALTERNATIVE_REFSETID, true));
						    validate(c, i, associationsWithCardinality, isLegacy);
						    break;
					    case SCTID_INACT_OUTDATED :
						    associationsWithCardinality.add(new AssociationCardinality("0..1", SCTID_ASSOC_REPLACED_BY_REFSETID, true));
						    associationsWithCardinality.add(new AssociationCardinality("2..*", SCTID_ASSOC_POSS_REPLACED_BY_REFSETID, true));
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
					String cdLegacy = (isLegacy?"Y":"N") + "/" + (getLegacyIndicator(d));
					//What are we looking at here?
					String data = c.getInactivationIndicatorEntries(ActiveState.ACTIVE).stream()
							.map(InactivationIndicatorEntry::toString)
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
					} else if (d.isActiveSafely() && !c.isActiveSafely()) {
						//Expect a single "Concept not current" indicator
						if (d.getInactivationIndicatorEntries(ActiveState.ACTIVE).isEmpty()) {
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
						if (!d.getInactivationIndicatorEntries(ActiveState.ACTIVE).isEmpty()) {
							report(SECONDARY_REPORT, c, c.getEffectiveTime(), d, "Active description of active concept should not have an inactivation indicator", cdLegacy, d, data);
							countIssue(c);
						}
					} else if ((!d.isActive() && !c.isActive()) && (includeLegacyIssues || !isLegacy(d))) {
						//Expect inactivation indicator here, but not Concept-non-current
						if (d.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() != 1) {
							//FRI-272 Agreed with Maria that we're not going to put time into these when they're legacy
							if (!isLegacy(d)) {
								report(SECONDARY_REPORT, c, c.getEffectiveTime(), "Inactive description of active concept should have an inactivation indicator", cdLegacy, d);
								incrementSummaryInformation("Inactive description of active concept should have an inactivation indicator");
								countIssue(c);
							}
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
	
	private void validate(Concept c, InactivationIndicatorEntry i, List<AssociationCardinality> associationsWithCardinality, Boolean legacy) throws TermServerScriptException {
		String data = c.getAssociationEntries(ActiveState.ACTIVE, true).stream()
				.map(AssociationEntry::toString)
				.collect(Collectors.joining(",\n"));
		
		if (StringUtils.isEmpty(data)) {
			data = "No historical associations defined";
		}
		
		//Add in the inactivation indicator
		if (i != null) {
			data = SnomedUtils.translateInactivationIndicator(i.getInactivationReasonId()) + "\n" + data;
		}
		
		String targets = c.getAssociationEntries(ActiveState.ACTIVE, true).stream()
				.map(h-> SnomedUtils.translateAssociation(h.getRefsetId()).toString() + " " + gl.getConceptSafely(h.getTargetComponentId()).toString())
				.collect(Collectors.joining(",\n"));
		
		if (StringUtils.isEmpty(targets)) {
			targets = "No historical associations defined";
		}
		
		//Loop through all possible associations.  Don't report issue unless all fail
		String lastIssue = "";
		boolean onePassed = false;
		for (AssociationCardinality associationWithCardinality : associationsWithCardinality) {
			lastIssue = validate(c, i, associationWithCardinality.cardinality, associationWithCardinality.association, associationWithCardinality.mutuallyExclusive);
			if (lastIssue.isEmpty()) {
				onePassed = true;
			}
		}
		if (!onePassed && !lastIssue.isEmpty()) {
			if (lastIssue.equals(CARDINALITY_ISSUE)) {
				String separator = associationsWithCardinality.get(0).mutuallyExclusive ? " OR \n" : " AND/OR \n";
				lastIssue += associationsWithCardinality.stream()
						.map(AssociationCardinality::toString)
						.collect(Collectors.joining(separator));
			}
			incrementSummaryInformation(lastIssue);
			report(SECONDARY_REPORT, c, c.getEffectiveTime(), lastIssue, legacy, targets, data);
			countIssue(c);
		}
	}

	private String validate(Concept c, InactivationIndicatorEntry i, String cardinality, String assocId, boolean mutuallyExclusive) throws TermServerScriptException {
		int minAssocs = Character.getNumericValue(cardinality.charAt(0));
		char maxStr = cardinality.charAt(cardinality.length() - 1);
		int maxAssocs = maxStr == '*' ? Integer.MAX_VALUE : Character.getNumericValue(maxStr);
		String inactStr = SnomedUtils.translateInactivationIndicator(i.getInactivationReasonId()).toString();
		String reqAssocStr = assocId == null? "None" : SnomedUtils.translateAssociation(assocId).toString();
		
		//Special case, we're getting limited inactivations with both SameAs and Was A attributes.  Record stats, but skip
		if (i.getInactivationReasonId().equals(SCTID_INACT_LIMITED) || i.getInactivationReasonId().equals(SCTID_INACT_MOVED_ELSEWHERE)) {
			return "";
		}
		
		//First check cardinality
		int assocCount = c.getAssociationEntries(ActiveState.ACTIVE, assocId, true).size();
		int allAssocCount = c.getAssociationEntries(ActiveState.ACTIVE, true).size();
		
		if (assocCount > 0 && assocCount != allAssocCount && mutuallyExclusive) {
			if (assocId == null) {
				return inactStr + " inactivation does not allow for any historical association to be specified";
			}
			return inactStr + " inactivation's " + reqAssocStr + " association is mutually exclusive with other associations types.";
		}
		
		if (assocCount > maxAssocs) {
			return CARDINALITY_ISSUE;
		} else if (assocCount < minAssocs) {
			return CARDINALITY_ISSUE;
		} else {
			//Now check association is appropriate for the inactivation indicator used
			for (AssociationEntry h : c.getAssociationEntries(ActiveState.ACTIVE, true)) {
				String assocStr = SnomedUtils.translateAssociation(h.getRefsetId()).toString();
				Concept target = gl.getConcept(h.getTargetComponentId());
				//Only the "MovedTo" historical association should point to a Namespace Concept
				if (!h.getRefsetId().equals(SCTID_ASSOC_MOVED_TO_REFSETID) && namespaceConcepts.contains(target)) {
					return assocStr + " should not point to namespace concept: " + target;
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
					//validateTargetAppropriate(c,assocStr, target, h)
				}
			}
		}
		return "";
	}

	private boolean isLegacy(Component c) {
		//If any relationship, description or historical association
		//has been modified, then this is not a legacy issue
		return !StringUtils.isEmpty(c.getEffectiveTime());
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

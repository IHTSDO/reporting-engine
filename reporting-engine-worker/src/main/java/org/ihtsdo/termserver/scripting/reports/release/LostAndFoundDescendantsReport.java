package org.ihtsdo.termserver.scripting.reports.release;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.AncestorsCache;
import org.ihtsdo.termserver.scripting.ArchiveManager;
import org.ihtsdo.termserver.scripting.TransitiveClosure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 */
public class LostAndFoundDescendantsReport extends TermServerReport implements ReportClass {
	private static String COUNT_NEW_AS_GAINED = "Count new concepts as gained";
	
	private AncestorsCache cache;
	private TransitiveClosure tc;
	private TransitiveClosure ptc;
	private Map<Concept, Set<Long>> gainedDescendantsMap = new HashMap<>();
	private Map<Concept, Set<Long>> lostDescendantsMap = new HashMap<>();
	
	private boolean countNewAsGained = true;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(UNPROMOTED_CHANGES_ONLY, "Y");
		params.put(COUNT_NEW_AS_GAINED, "N");
		//params.put(ECL, "<< 443961001 |Malignant adenomatous neoplasm (disorder)|" );
		TermServerReport.run(LostAndFoundDescendantsReport.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA
		super.init(run);
		runStandAlone = false; //We need to load previous previous for real
		ArchiveManager mgr = getArchiveManager();
		mgr.setPopulateReleasedFlag(true);
		if (!StringUtils.isNumeric(project.getKey())) {
			mgr.setPopulatePreviousTransativeClosure(true);
		}
	}
	
	public void postInit() throws TermServerScriptException {
		countNewAsGained = getJobRun().getParamBoolean(COUNT_NEW_AS_GAINED);
		String[] columnHeadings = new String[] { "SCTID, FSN, Semtag, Previous Count, Current Count " + (countNewAsGained?"(includes new)":"(does not include new)") + ", Hierarchy Movement Count (does not include inactivated)",
				"SCTID, FSN, Semtag, Movement, Concept"};
		String[] tabNames = new String[] {	"Summary",
				"Detail"};
		cache = gl.getAncestorsCache();
		ptc = gl.getPreviousTC();
		tc = gl.getTransitiveClosure();
		
		super.postInit(tabNames, columnHeadings, false);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.add(UNPROMOTED_CHANGES_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.add(COUNT_NEW_AS_GAINED).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("Lost and Found Descendants")
				.withDescription("This report lists descendants gained and lost in the current authoring cycle.  Note that a concept being made inactive does not qualify it for being 'lost'.  A lost concept must still be active.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withTag(INT)
				.withParameters(params)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		if (ptc == null) {
			throw new TermServerScriptException("Previous Transitive Closure not available.  Cannot continue.");
		}
		
		Collection<Concept> conceptsOfInterest;
		if (subsetECL != null && !subsetECL.isEmpty()) {
			conceptsOfInterest = findConcepts(subsetECL);
		} else {
			conceptsOfInterest = gl.getAllConcepts();
		}
		
 		for (Concept c : SnomedUtils.sort(conceptsOfInterest)) {
 			if (unpromotedChangesOnly && !unpromotedChangesHelper.hasUnpromotedChange(c)) {
 				continue;
 			}
			Set<Long> gainedDescendants = getGainedDescendants(c);
			Set<Long> lostDescendants = getLostDescendants(c);
			if (hasGainedOrLostDescendants(c) || isTopLevelConcept(c,conceptsOfInterest)) {
				String stats = "+" + gainedDescendants.size() + " / -" + lostDescendants.size();
				int previousCount = ptc.getDescendants(c).size();
				int currentCount = tc.getDescendants(c).size();
				//Skip concept if - despite being top level in our set, has no descendants and hasn't lost any
				if (currentCount == 0 && lostDescendants.size() == 0) {
					continue;
				}
				
				report(c, previousCount, currentCount, stats);
				
				for (Long gainedConceptId : gainedDescendants) {
					Concept gainedConcept = gl.getConcept(gainedConceptId);
					//If this concept has ancestors in this set that are also being reported
					//then we don't need to also report the children.
					if (!hasAncestorsBeingReported(c, gainedDescendants)) {
						report(SECONDARY_REPORT, c, "Gained", gainedConcept);
					}
				}
				
				for (Long lostConceptId : lostDescendants) {
					Concept lostConcept = gl.getConcept(lostConceptId);
					//If this concept has ancestors in this set that are also being reported
					//then we don't need to also report the children.
					if (!hasAncestorsBeingReported(c, lostDescendants)) {
						report(SECONDARY_REPORT, c, "Lost", lostConcept);
					}
				}
			}
		}
	}

	private boolean isTopLevelConcept(Concept c, Collection<Concept> conceptsOfInterest) throws NumberFormatException, TermServerScriptException {
		//If this concept has no ancestors being examined, then it's a top level concept
		for (Concept ancestor : cache.getAncestors(c)) {
			//If we're ignoring this concept, then ignore it here too
 			if (unpromotedChangesOnly && !unpromotedChangesHelper.hasUnpromotedChange(ancestor)) {
 				continue;
 			}
 			
			if (conceptsOfInterest.contains(ancestor)) {
				return false;
			}
		}
		return true;
	}

	private boolean hasAncestorsBeingReported(Concept c, Set<Long> beingReported) throws NumberFormatException, TermServerScriptException {
		for (Concept ancestor : cache.getAncestors(c)) {
			if (beingReported.contains(Long.parseLong(ancestor.getId()))) {
				return true;
			}
		}
		return false;
	}

	private boolean hasGainedOrLostDescendants(Concept c) {
		return getGainedDescendants(c).size() > 0 || getLostDescendants(c).size() > 0;
	}

	private Set<Long> getLostDescendants(Concept c) {
		if (!lostDescendantsMap.containsKey(c)) {
			populateGainedLostDescendants(c);
		}
		return lostDescendantsMap.get(c);
	}

	private Set<Long> getGainedDescendants(Concept c) {
		if (!gainedDescendantsMap.containsKey(c)) {
			populateGainedLostDescendants(c);
		}
		return gainedDescendantsMap.get(c);
	}

	private void populateGainedLostDescendants(Concept c) {
		//What descendants have we lost?  Make sure they're still active or they're not 'lost'
		Set<Long> previousDescendantIds = ptc.getDescendants(c);
		Set<Long> lostDescendantIds = new HashSet<Long>(previousDescendantIds);

		Set<Long> currentDescendantIds = tc.getDescendants(c);
		Set<Long> gainedDescendantIds = new HashSet<Long>();
		if (currentDescendantIds != null) {
			gainedDescendantIds = new HashSet<Long>(currentDescendantIds);
		}

		//Remove the current set, to see what's no longer a descendant
		lostDescendantIds.removeAll(tc.getDescendants(c));
		
		//Remove the previous descendants to find the ones we've gained
		gainedDescendantIds.removeAll(previousDescendantIds);
		
		//Map to concepts and filter to retain only those that are active
		lostDescendantIds = lostDescendantIds.stream()
				.map(l -> gl.getConceptSafely(l.toString()))
				.filter(f -> f.isActive())
				.map(concept -> Long.parseLong(concept.getId()))
				.collect (Collectors.toSet());
		
		//If we not counting new concepts as gained, then only include those that
		//appeared in the previous transitive closure
		if (!countNewAsGained) {
			gainedDescendantIds = gainedDescendantIds.stream()
					.filter(id -> ptc.contains(id))
					.collect (Collectors.toSet());
		}
		
		lostDescendantsMap.put(c, lostDescendantIds);
		gainedDescendantsMap.put(c, gainedDescendantIds);
	}

}

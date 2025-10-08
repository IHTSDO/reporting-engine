package org.ihtsdo.termserver.scripting.reports.release;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.AncestorsCache;
import org.ihtsdo.termserver.scripting.snapshot.ArchiveManager;
import org.ihtsdo.termserver.scripting.TransitiveClosure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LostAndFoundDescendantsReport extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(LostAndFoundDescendantsReport.class);
	private static final String COUNT_NEW_AS_GAINED = "Count new concepts as gained";
	
	private AncestorsCache cache;
	private TransitiveClosure tc;
	private TransitiveClosure ptc;
	
	private Map<Concept, Set<Long>> gainedDescendantsMap = new HashMap<>();
	private Map<Concept, Set<Long>> lostDescendantsMap = new HashMap<>();
	
	private Map<Concept, Set<Long>> gainedDescendantsInScopeMap = new HashMap<>();
	private Map<Concept, Set<Long>> lostDescendantsInScopeMap = new HashMap<>();
	
	private boolean countNewAsGained = true;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(UNPROMOTED_CHANGES_ONLY, "N");
		params.put(COUNT_NEW_AS_GAINED, "Y");
		params.put(ECL, "*" );
		TermServerScript.run(LostAndFoundDescendantsReport.class, args, params);
	}
	
	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId(GFOLDER_RELEASE_QA);
		super.init(run);
		runStandAlone = false; //We need to load previous previous for real
		ArchiveManager mgr = getArchiveManager();
		mgr.setEnsureSnapshotPlusDeltaLoad(true);
		if (!StringUtils.isNumeric(project.getKey())) {
			mgr.setPopulatePreviousTransitiveClosure(true);
		}
	}
	
	@Override
	public void postInit() throws TermServerScriptException {
		countNewAsGained = getJobRun().getParamBoolean(COUNT_NEW_AS_GAINED);
		String[] columnHeadings = new String[] { "SCTID, FSN, Semtag, Active, Previous Count, Current Count " + (countNewAsGained?"(includes new)":"(does not include new)") + ", Hierarchy Movement Count (does not include inactivated)",
				"SCTID, FSN, Semtag, Active, Movement, Concept"};
		String[] tabNames = new String[] {	"Summary",
				"Detail"};
		cache = gl.getAncestorsCache();
		ptc = gl.getPreviousTC();
		tc = gl.getTransitiveClosure();
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.add(UNPROMOTED_CHANGES_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.add(COUNT_NEW_AS_GAINED).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.QI))
				.withName("Lost and Found Descendants")
				.withDescription("This report lists descendants gained and lost in the current authoring cycle.  Note that a concept being made inactive does not qualify it for being 'lost'.  A lost concept must still be active.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withTag(INT)
				.withTag(MS)
				.withParameters(params)
				.withExpectedDuration(150)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		if (ptc == null) {
			throw new TermServerScriptException("Previous Transitive Closure not available.  Cannot continue.");
		}
		
		Collection<Concept> conceptsOfInterest = getConceptsOfInterest();
		
		int lastPercReported = 0;
		int conceptsProcessed = 0;
 		for (Concept c : conceptsOfInterest) {
 			//Skip the root concept, its descendant's lost/gained would take all day!
 			if (c.equals(ROOT_CONCEPT) 
 					|| (unpromotedChangesOnly && !unpromotedChangesHelper.hasUnpromotedChange(c))) {
 				continue;
 			}
 			
 			int percCompleted = (int)((++conceptsProcessed/(double)conceptsOfInterest.size())*100);
 			if (percCompleted >= lastPercReported + 1) {
 				LOGGER.info("{}% complete.", percCompleted);
 				lastPercReported = percCompleted;
 			}
			
			analyzeConcept(c, conceptsOfInterest);
		}
	}

	private void analyzeConcept(Concept c, Collection<Concept> conceptsOfInterest) throws TermServerScriptException {
		Set<Long> gainedDescendants = getGainedDescendants(c);
		Set<Long> lostDescendants = getLostDescendants(c);
		//Now if this concept is in scope eg Dutch, then we're interested if it's gained/lost ANY concepts
		//But if it's International, we're only interested if in scope concepts have been lost
		if (isTopLevelConcept(c,conceptsOfInterest)
				|| (inScope(c) && hasGainedOrLostDescendants(c))
				|| !inScope(c) && hasGainedOrLostDescendantsInScope(c)) {
			String stats = "+" + gainedDescendants.size() + " / -" + lostDescendants.size();
			int previousCount = ptc.getDescendants(c).size();
			int currentCount = tc.getDescendants(c).size();
			//Skip concept if - despite being top level in our set, has no descendants and hasn't lost any
			if (currentCount == 0 && lostDescendants.isEmpty()) {
				return;
			}
			
			report(c, c.isActiveSafely() ? "Y":"N", previousCount, currentCount, stats);
			countIssue(c);
			reportGainedAndLostDescendants(c, gainedDescendants, lostDescendants);
		}
	}

	private void reportGainedAndLostDescendants(Concept c, Set<Long> gainedDescendants, Set<Long> lostDescendants) throws TermServerScriptException {
		for (Long gainedConceptId : gainedDescendants) {
			Concept gainedConcept = gl.getConcept(gainedConceptId);
			//If this concept has ancestors in this set that are also being reported
			//then we don't need to also report the children.
			if (!hasAncestorsBeingReported(c, gainedDescendants)) {
				report(SECONDARY_REPORT, c, c.isActiveSafely() ? "Y":"N", "Gained", gainedConcept);
			}
		}
		
		for (Long lostConceptId : lostDescendants) {
			Concept lostConcept = gl.getConcept(lostConceptId);
			//If this concept has ancestors in this set that are also being reported
			//then we don't need to also report the children.
			if (!hasAncestorsBeingReported(c, lostDescendants)) {
				report(SECONDARY_REPORT, c, c.isActiveSafely() ? "Y":"N", "Lost", lostConcept);
			}
		}
	}

	private Collection<Concept> getConceptsOfInterest() throws TermServerScriptException {
		Collection<Concept> conceptsOfInterest;
		if (subsetECL != null && !subsetECL.isEmpty()) {
			conceptsOfInterest = findConcepts(subsetECL);
		} else {
			conceptsOfInterest = gl.getAllConcepts();
		}
		
		LOGGER.info("Sorting concepts of interest...");
		conceptsOfInterest = SnomedUtils.sort(conceptsOfInterest);
		LOGGER.info("Sorting complete.");
		return conceptsOfInterest;
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
		return !getGainedDescendants(c).isEmpty() || !getLostDescendants(c).isEmpty();
	}

	private Set<Long> getLostDescendants(Concept c) {
		if (!lostDescendantsMap.containsKey(c)) {
			populateGainedLostDescendants(c, false);
		}
		return lostDescendantsMap.getOrDefault(c, new HashSet<>());
	}

	private Set<Long> getGainedDescendants(Concept c) {
		if (!gainedDescendantsMap.containsKey(c)) {
			populateGainedLostDescendants(c, false);
		}
		return gainedDescendantsMap.getOrDefault(c, new HashSet<>());
	}
	
	private boolean hasGainedOrLostDescendantsInScope(Concept c) {
		return !getGainedDescendantsInScope(c).isEmpty() || !getLostDescendantsInScope(c).isEmpty();
	}

	private Set<Long> getLostDescendantsInScope(Concept c) {
		if (!lostDescendantsInScopeMap.containsKey(c)) {
			populateGainedLostDescendants(c, true);
		}
		return lostDescendantsInScopeMap.getOrDefault(c, new HashSet<>());
	}

	private Set<Long> getGainedDescendantsInScope(Concept c) {
		if (!gainedDescendantsInScopeMap.containsKey(c)) {
			populateGainedLostDescendants(c, true);
		}
		return gainedDescendantsInScopeMap.getOrDefault(c, new HashSet<>());
	}

	private void populateGainedLostDescendants(Concept c, boolean inScopeOnly) {
		Set<Long> previousDescendantIds;
		if (inScopeOnly) {
			previousDescendantIds = ptc.getDescendants(c, d -> inScope(gl.getConceptSafely(d.toString())));
		} else {
			previousDescendantIds = ptc.getDescendants(c);
		}
		

		Set<Long> currentDescendantIds;
		if (inScopeOnly) {
			currentDescendantIds = tc.getDescendants(c, d -> inScope(gl.getConceptSafely(d.toString())));
		} else {
			currentDescendantIds = tc.getDescendants(c);
		}
		
		Set<Long> gainedDescendantIds = new HashSet<>();
		
		if (currentDescendantIds != null) {
			gainedDescendantIds = new HashSet<>(currentDescendantIds);
		}

		Set<Long> lostDescendantIds = new HashSet<>(previousDescendantIds);
		
		//Remove the current set, to see what's no longer a descendant
		lostDescendantIds.removeAll(currentDescendantIds);
		
		//Remove the previous descendants to find the ones we've gained
		gainedDescendantIds.removeAll(previousDescendantIds);
		
		//What descendants have we lost?  Make sure they're still active or they're not 'lost'
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

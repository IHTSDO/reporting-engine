package org.ihtsdo.termserver.scripting.reports.release;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.*;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.snapshot.ArchiveManager;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * RP-227 Pattern KPIs (RP-273 Splits this into KPI and QI Patterns reports)
 * --------------------
 * RP-228 Redundant ungrouped roles
 * RP-229 Redundant stated groups
 * RP-232 Redundant stated IS A
 * RP-231 Newly inactivatated duplicate created in prior release
 * RP-230 Existing sufficiently defined concepts that 
 * gained a stated primitive parent and lost active inferred descendant(s)
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KPIPatternsReport extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(KPIPatternsReport.class);

	private Map<String, Integer> issueSummaryMap = new HashMap<>();
	AncestorsCache cache;
	String previousPreviousRelease;
	TransitiveClosure tc;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(UNPROMOTED_CHANGES_ONLY, "Y");
		TermServerScript.run(KPIPatternsReport.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"); //Release QA
		super.init(run);
		runStandAlone = false; //We need to load previous previous for real
		ArchiveManager mgr = getArchiveManager();
		mgr.setEnsureSnapshotPlusDeltaLoad(true);
		if (!StringUtils.isNumeric(project.getKey())) {
			mgr.setPopulatePreviousTransitiveClosure(true);
			try {
				previousPreviousRelease = mgr.getPreviousPreviousBranch(project);
			} catch (Exception e) {
				throw new TermServerScriptException("Failed to recover previous previous branch", e);
			}
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { "SCTID, FSN, Semtag, Issue, Details, Details, Details, Details, Details",
				"Issue, Count, Details, Details, Details",
				"SCTID, FSN, Semtag, Lost Concept Id, Lost Concept FSN, Details, Details, Details"};
		String[] tabNames = new String[] {	"Issues",
				"Summary",
				"Pattern 11 Detail"};
		cache = gl.getAncestorsCache();
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(UNPROMOTED_CHANGES_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("KPI Patterns Report")
				.withDescription("This report identifies a number of potentially problematic patterns, many of which are tracked as KPIs ")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withTag(INT)
				.withParameters(params)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		
		LOGGER.info("Checking structural integrity");
		if (checkStructuralIntegrity()) {
			LOGGER.info("Checking for problematic patterns...");
			
			LOGGER.info("Checking for redundancies...");
			checkRedundantlyStatedParents();
			checkRedundantlyStatedUngroupedRoles();
			checkRedundantlyStatedGroups();
		
			LOGGER.info("Building current Transitive Closure");
			tc = gl.generateTransitiveClosure();
			
			LOGGER.info("Checking for historical patterns 11 & 21");
			if (previousPreviousRelease != null) {
				checkCreatedButDuplicate();
				checkPattern11();  //...a very specific situation
			} else {
				report(null, "Skipping Patterns 11 & 21", "No previous previous release available");
			}
			
		} else {
			report(null, "Structural integrity test failed.  Report execution terminated early");
		}
		LOGGER.info("Checks complete, creating summary tag");
		populateSummaryTab();
		
		LOGGER.info("Summary tab complete, all done.");
	}

	private void populateSummaryTab() throws TermServerScriptException {
		issueSummaryMap.entrySet().stream()
				.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
						.forEach(e -> reportSafely (SECONDARY_REPORT, (Component)null, e.getKey(), e.getValue()));
		
		int total = issueSummaryMap.entrySet().stream()
				.map(e -> e.getValue())
				.collect(Collectors.summingInt(Integer::intValue));
		reportSafely (SECONDARY_REPORT, (Component)null, "TOTAL", total);
	}
	
	private boolean checkStructuralIntegrity() throws TermServerScriptException {
		String issueStr = "Concept referenced in axiom, but does not exist";
		String issueStr2 = "Concept exists without an FSN";
		String issueStr3 = "Active concept does not have any axioms";
		//Don't initialise, we won't mention the check if there's no problem.
		boolean isOK = true;
		for (Concept c : gl.getAllConcepts()) {
			if (c.getId().equals("897596000")) {
				LOGGER.debug("here");
			}
			if (c.isReleased() == null) {
				String detail = "";
				if (c.getAxiomEntries() != null && c.getAxiomEntries().size() > 0) {
					detail = c.getAxiomEntries().get(0).toString();
				} else {
					//We'll have to work through all axioms to find where this came from
					List<AxiomEntry> axiomsContaining = findAxiomsContaining(c);
					if (axiomsContaining.size() > 0) {
						detail = "In " + axiomsContaining.size() + " axioms eg " + axiomsContaining.iterator().next();
					} else {
						if (c.getFSNDescription() == null) {
							detail = "Concept referenced in relationship, but without description or axiom";
						} else if (c.getModuleId() == null) {
							detail = "Concept exists only as orphaned descriptions";
						} else {
							detail = "Concept exists with description but no relationships?";
						}
					}
				}
				report(c, issueStr, detail);
				isOK = false;
			} else if (c.getFsn() == null || c.getFsn().isEmpty()) {
				report(c, issueStr2);
				isOK = false;
			} else if (c.isActive() && c.getAxiomEntries().size() == 0) {
				//The Root concept can get away with this
				if (!c.equals(ROOT_CONCEPT)) {
					report(c, issueStr3);
					isOK = false;
				} 
			}
		}
		return isOK;
	}
	
	private List<AxiomEntry> findAxiomsContaining(Concept findme) {
		List<AxiomEntry> matches = new ArrayList<>();
		for (Concept c : gl.getAllConcepts()) {
			for (AxiomEntry a : c.getAxiomEntries()) {
				//TODO Isn't great - we may match SCTIDs which are a subtring of another
				//Split the axiom into constituent parts if this proves to be a problem
				if (a.getOwlExpression().contains(findme.getConceptId())) {
					matches.add(a);
				}
			}
		}
		return matches;
	}

	private void checkRedundantlyStatedParents() throws TermServerScriptException {
		//RP-232 Redundantly stated IsA relationships
		String issueStr = "Pattern 1: Redundantly stated IsA relationships";
		initialiseSummary(issueStr);
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive()) {
				//Test each ungrouped relationship to see if it subsumes any other
				for (Concept a : c.getParents(CharacteristicType.STATED_RELATIONSHIP)) {
					for (Concept b :c.getParents(CharacteristicType.STATED_RELATIONSHIP)) {
						if (!a.equals(b) && cache.getAncestorsOrSelf(a).contains(b)) {
							report(c, issueStr, a, b);
						}
					}
				}
			}
		}
	}

	private void checkRedundantlyStatedUngroupedRoles() throws TermServerScriptException {
		//RP-288 Redundantly stated ungrouped roles
		String issueStr = "Pattern 2: Redundantly stated ungrouped role";
		initialiseSummary(issueStr);
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive()) {
				//Test each ungrouped relationship to see if it subsumes any other
				for (Relationship a : c.getRelationshipGroupSafely(CharacteristicType.STATED_RELATIONSHIP, UNGROUPED).getRelationships()) {
					for (Relationship b : c.getRelationshipGroupSafely(CharacteristicType.STATED_RELATIONSHIP, UNGROUPED).getRelationships()) {
						if (!a.equals(b) && cache.getAncestorsOrSelf(a.getType()).contains(b.getType())) {
							if (cache.getAncestorsOrSelf(a.getTarget()).contains(b.getTarget())) {
								report(c, issueStr, a, b);
							}
						}
					}
				}
			}
		}
	}
	
	private void checkRedundantlyStatedGroups() throws TermServerScriptException {
		//RP-289 Redundantly stated ungrouped roles
		String issueStr = "Pattern 3: Redundantly stated group";
		initialiseSummary(issueStr);
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive()) {
				//Test each relationship group to see if it subsumes any other
				for (RelationshipGroup a : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
					for (RelationshipGroup b : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
						if (a.getGroupId() == UNGROUPED || b.getGroupId() == UNGROUPED || a.getGroupId() == b.getGroupId()) {
							continue;
						} else {
							if (SnomedUtils.covers(a, b, cache)) {
								report(c, issueStr, a, b);
							}
						}
					}
				}
			}
		}
	}
	
	private void checkCreatedButDuplicate() throws TermServerScriptException {
		//RP-231 Pattern 21 Newly inactivatated duplicate was created in previous release
		String issueStr = "Pattern 21: Newly inactivatated duplicate was created in previous release.";
		initialiseSummary(issueStr);
		String issue2Str = "Assertion: Inactive concept is missing inactivation indicator";
		initialiseSummary(issue2Str);
		int errorCount = 0;
		for (Concept c : gl.getAllConcepts()) {
			if (!c.isActive() && 
				(c.getEffectiveTime() == null || c.getEffectiveTime().isEmpty())) {
				if (c.getInactivationIndicator() == null) {
					report(c, issue2Str);
				} else if (c.getInactivationIndicator().equals(InactivationIndicator.DUPLICATE)) {
					//Did this concept exist in the previous previous release?
					//If not, then it was created in the previous release and immediately retired
					try {
						Concept loadedConcept = loadConcept(c, previousPreviousRelease);
						if (loadedConcept == null) {
							report(c, issueStr);
						}
					} catch (Exception e) {
						report(c, "API ERROR", "Failed to check previous previous release due to " + e.getMessage());
						if (++errorCount == 5) {
							report(c, "API ERROR", "Maximum failures reached, giving up on Pattern 21");
							break;
						}
					}
			}
			}
		}
	}
	
	private void checkPattern11() throws TermServerScriptException {
		//RP-230 Existing sufficiently defined concepts that: 
		//1. gained a stated intermediate primitive parent and 
		//2. lost active inferred descendant(s)
		String issueStr = "Pattern 11: Existing sufficiently defined concepts that gained a stated primitive parent and lost active inferred descendant(s)";
		initialiseSummary(issueStr);
		
		if (gl.getPreviousTC() == null) {
			report(null, "Previous Transitive Closure is not available.  Unable to check Pattern 11");
			return;
		}
		
 		for (Concept c : gl.getAllConcepts()) {
 			/*if (c.getId().equals("1163463008")) {
 				LOGGER.debug("here");
 			}*/
			//Filter for active concepts that have already been published and are sufficiently defined.
			if (c.isActive() && c.isReleased() &&
					c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				List<Concept> newPrimitiveParents = getNewPrimitiveParents(c);
				if (newPrimitiveParents.size() > 0) {
					//What descendants have we lost?  Make sure they're still active.
					Set<Long> previousDescendantIds = gl.getPreviousTC().getDescendants(c);
					//Did we in fact have any descendants in the previous release?
					if (previousDescendantIds == null) {
						continue;
					}
					previousDescendantIds = new HashSet<Long>(previousDescendantIds);
					int previousCount = previousDescendantIds.size();
					Set<Long> currentDescendantIds = tc.getDescendants(c);
					//Have we in fact lost _all_ descendants?
					if (currentDescendantIds == null) {
						currentDescendantIds = new HashSet<Long>();
					}
					int newCount = currentDescendantIds.size();
					//Remove the current set, to see what's no longer a descendant
					previousDescendantIds.removeAll(currentDescendantIds);
					//Map to concepts and filter to retain only those that are active
					List<Concept> lostActive = previousDescendantIds.stream()
							.map(l -> gl.getConceptSafely(l.toString()))
							.filter(f -> f.isActive())
							.collect (Collectors.toList());
					if (lostActive.size() > 0) {
						String stats = previousCount + " -> " + newCount + " (-" + lostActive.size() + ")";
						boolean reported = report(c, issueStr, toString(newPrimitiveParents), "inferred descendants", 
								stats, "eg", lostActive.get(0));
						if (reported) {
							for (Concept lostConcept : lostActive) {
								report(TERTIARY_REPORT, c, lostConcept.getConceptId(), lostConcept.getFsn());
							}
						}
					}
				}
			}
		}
	}

	private String toString(List<Concept> concepts) {
		return concepts.stream()
				.map(c -> c.toString())
				.collect(Collectors.joining(", "));
	}

	private List<Concept> getNewPrimitiveParents(Concept c) {
		//Get new (ie not released) stated IS_A relationship targets
		//This changes with axioms because an axiom can be released, and then changed
		//which means relationships we've never seen before get marked as released.
		//This has been addressed in GraphLoader by checking for pre-existing stated rels
		return c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE).stream()
				.filter(r -> r.getType().equals(IS_A) && !r.isReleased())
				.map(r -> r.getTarget())
				.collect(Collectors.toList());
	}

	protected void initialiseSummary(String issue) {
		issueSummaryMap.merge(issue, 0, Integer::sum);
	}
	
	public boolean report(Concept c, Object...details) throws TermServerScriptException {
		//Are we filtering this report to only concepts with unpromoted changes?
		if (unpromotedChangesOnly && !unpromotedChangesHelper.hasUnpromotedChange(c)) {
			return false;
		}
		//First detail is the issue
		issueSummaryMap.merge(details[0].toString(), 1, Integer::sum);
		countIssue(c);
		return super.report(PRIMARY_REPORT, c, details);
	}
	
	class GroupPair {
		RelationshipGroup one;
		RelationshipGroup two;
		int hash;
		GroupPair (RelationshipGroup one, RelationshipGroup two) {
			this.one = one;
			this.two = two;
			hash = (one.toString() + two.toString()).hashCode();
		}
		
		public boolean equals(Object other) {
			if (other instanceof GroupPair) {
				GroupPair otherPair = (GroupPair)other;
				if (this.one.getGroupId() == otherPair.one.getGroupId()) {
					return this.two.getGroupId() == otherPair.two.getGroupId();
				}
			}
			return false;
		}
		
		public int hashCode() {
			return hash;
		}
	}
}

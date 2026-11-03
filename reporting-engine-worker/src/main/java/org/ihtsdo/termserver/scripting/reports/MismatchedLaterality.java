package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * RP-403 Report concepts that have laterality in their attributes but not in their
 * FSN or visa versa
 * CDI-52 Update report to successfully run against projects with concrete values.
 * RP-439 Only expect a left/right counterpart where a body structure is lateralizable
 */
public class MismatchedLaterality extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(MismatchedLaterality.class);
	private static final String COMMON_HEADERS = "Concept, FSN, SemTag, Expression";
	private static final String SEMTAG_BODY_STRUCTURE = "(body structure)";
	private static final String SCTID_LATERALIZABLE_REFSET = "723264001";

	Set<String> hierarchies = new HashSet<>();
	Set<Concept> reportedSuspect = new HashSet<>();
	Map<String, Concept> fsnMap = new HashMap<>();
	Set<Concept> lateralizableStructures = new HashSet<>();
	
	public static final String STR_LEFT = "left";
	public static final String STR_RIGHT = "right";
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		TermServerScript.run(MismatchedLaterality.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc
		super.init(run);
		getSnapshotConfiguration().setLoadOtherReferenceSets(true);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Laterality Mismatch")
				.withDescription("This report lists concepts that have laterality indicated in their attributes but not in their " + 
						"FSN or visa versa.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(new JobParameters())
				.withTag(INT).withTag(MS)
				.build();
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] tabNames = new String[] {
				"Missing Concepts",
				"Lateralized FSN No Model",
				"Lateralized Model No FSN", 
				"Suspect Lateralization",
				"Bilateralized FSN No Model",
				"Bilateralized Model No FSN",
				"Processing Notes"};
		String[] columnHeadings = new String[] {
			"Concept, FSN, SemTag, Missing Concept",
			COMMON_HEADERS,
			COMMON_HEADERS,
			COMMON_HEADERS,
			COMMON_HEADERS,
			COMMON_HEADERS,
			"Concept, FSN, SemTag, Comment",
		};
		super.postInit(GFOLDER_GENERAL_QA, tabNames, columnHeadings, false);
		hierarchies.add("< 71388002 |Procedure (procedure)|");
		hierarchies.add("< 404684003 |Clinical finding (finding)| ");
		hierarchies.add("< 123037004 |Body structure (body structure)|");
		
		LOGGER.info("Populating FSN map and lateralizable body structures for all concepts");
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActiveSafely()) {
				fsnMap.put(c.getFsn().toLowerCase(), c);
				if (isInLateralizableRefset(c)) {
					lateralizableStructures.add(c);
				}
			}
		}
		if (lateralizableStructures.isEmpty()) {
			throw new TermServerScriptException("No active members of the Lateralizable body structure reference set " + SCTID_LATERALIZABLE_REFSET + " found");
		}
		LOGGER.info("{} active members of the Lateralizable body structure reference set found", lateralizableStructures.size());
	}

	@Override
	public void runJob() throws TermServerScriptException {
		for (String hierarchy : hierarchies) {
			for (Concept c : findConcepts(hierarchy)) {
				if (!inScope(c)) {
					continue;
				}
				boolean hasLateralizedFSN = hasLateralizedFSN(c);
				boolean hasBilateralFSN = hasBilateralFSN(c);
				boolean hasLateralizedModel = hasLateralizedModel(c, 0);  //Only check 1 level downs
				boolean hasBilaterlizedModel = hasBilateralizedModel(c, 0);
				if (hasBilateralFSN && !hasBilaterlizedModel) {
					reportIssue(QUINARY_REPORT, c, c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
				} else if (!hasBilateralFSN && hasBilaterlizedModel) {
					reportIssue(SENARY_REPORT, c, c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
				} else if (hasBilateralFSN && hasBilaterlizedModel) {
					//All good here
				} else if (hasLateralizedFSN && !hasLateralizedModel) {
					reportIssue(SECONDARY_REPORT, c, c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
				} else if (!hasLateralizedFSN && hasLateralizedModel) {
					reportIssue(TERTIARY_REPORT, c, c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
				}
				
				if (hasLateralizedFSN && !hasBilateralFSN) {
					checkForCounterpartLaterality(c, hasLateralizedModel);
				}
			}
		}
	}

	private void checkForCounterpartLaterality(Concept c, boolean hasLateralizedModel) throws TermServerScriptException {
		if (hasLateralizedFSN(c, STR_LEFT)) {
			checkForCounterpartLaterality(c, hasLateralizedModel, STR_LEFT, STR_RIGHT);
		} else {
			checkForCounterpartLaterality(c, hasLateralizedModel, STR_RIGHT, STR_LEFT);
		}
	}

	private void checkForCounterpartLaterality(Concept c, boolean hasLateralizedModel, String current, String siblingLaterality) throws TermServerScriptException {
		//What would the name of the sibling concept be?
		String origFSN = " " + c.getFsn().toLowerCase();
		String targetFSN = origFSN.replace(" " + current + " ", " " + siblingLaterality + " ").trim();
		Concept sibling = fsnMap.get(targetFSN);
		if (sibling == null) {
			String counterpartSuggested = "Suggests need for counterpart \"" + targetFSN + "\"";
			if (!hasLateralizedModel) {
				//eg "left atrium" is the name of a structure, not a laterality
				report(SEPTENARY_REPORT, c, counterpartSuggested + " but the model is not lateralized");
			} else if (hasLateralizableBodyStructure(c)) {
				reportIssue(PRIMARY_REPORT, c, targetFSN);
			} else {
				report(SEPTENARY_REPORT, c, counterpartSuggested + " but has no lateralizable body structure");
			}
		}
	}

	private boolean hasLateralizedFSN(Concept c) {
		return hasLateralizedFSN(c, STR_LEFT) || hasLateralizedFSN(c, STR_RIGHT);
	}
	
	private boolean hasLateralizedFSN(Concept c, String laterality) {
		String fsn = " " + c.getFsn().toLowerCase();
		return fsn.contains(" " + laterality + " ");
	}
	
	private boolean hasBilateralFSN(Concept c) {
		String fsn = " " + c.getFsn().toLowerCase();
		return fsn.contains(" bilateral ") || fsn.contains(" left and right");
	}

	private boolean hasLateralizableBodyStructure(Concept c) {
		if (isBodyStructure(c)) {
			return isLateralizable(c);
		}
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.isNotConcrete() && !r.getType().equals(IS_A)
					&& isBodyStructure(r.getTarget()) && isLateralizable(r.getTarget())) {
				return true;
			}
		}
		return false;
	}

	private boolean isBodyStructure(Concept c) {
		return SEMTAG_BODY_STRUCTURE.equals(c.getSemTag());
	}

	private boolean isLateralizable(Concept bodyStructure) {
		//The refset only holds the highest lateralizable concept in each subhierarchy,
		//so all descendants of a member are also lateralizable
		return gl.getAncestorsCache().getAncestorsOrSelfSafely(bodyStructure).stream()
				.anyMatch(lateralizableStructures::contains);
	}

	private boolean isInLateralizableRefset(Concept c) {
		return c.getOtherRefsetMembers().stream()
				.anyMatch(m -> m.isActiveSafely() && m.getRefsetId().equals(SCTID_LATERALIZABLE_REFSET));
	}

	private boolean hasLateralizedModel(Concept c, int level) throws TermServerScriptException {
		//So either a target value is a type of laterality or it is itself 
		//lateralized - but we're only expecting that in a body structure
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if ((r.isNotConcrete()) && (r.getTarget().equals(LEFT) || r.getTarget().equals(RIGHT))) {
				reportIfSuspect(c);
				return true;
			} else if (level == 0 && r.isNotConcrete() && hasLateralizedModel(r.getTarget(), 1)) {
				return true;
			}
		}
		return false;
	}
	
	private boolean hasBilateralizedModel(Concept c, int level) throws TermServerScriptException {
		//So either a target value is a type of laterality or it is itself 
		//lateralized - but we're only expecting that in a body structure
		//Both must be present
		boolean leftPresent = false;
		boolean rightPresent = false;
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.isConcrete()) {
				continue;
			}
			if (r.getTarget().equals(LEFT)) {
				reportIfSuspect(c);
				leftPresent = true;
			} else if (r.getTarget().equals(RIGHT)) {
				reportIfSuspect(c);
				rightPresent = true;
			} else if (level == 0 && hasLaterality(r.getTarget(), LEFT)) {
				leftPresent = true;
			} else if (level == 0 && hasLaterality(r.getTarget(), RIGHT)) {
				rightPresent = true;
			}
		}
		return leftPresent && rightPresent;
	}

	private void reportIfSuspect(Concept c) throws TermServerScriptException {
		//Laterality is only expected directly on a body structure
		if (!isBodyStructure(c) && reportedSuspect.add(c)) {
			reportIssue(QUATERNARY_REPORT, c, c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
		}
	}

	private void reportIssue(int reportIdx, Concept c, Object... details) throws TermServerScriptException {
		countIssue(c);
		report(reportIdx, c, details);
	}

	private boolean hasLaterality(Concept c, Concept laterality) {
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.isNotConcrete() && r.getTarget().equals(laterality)) {
				return true;
			}
		}
		return false;
	}
}

package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RP-403 Report concepts that have laterality in their attributes but not in their
 * FSN or visa versa
 * CDI-52 Update report to successfully run against projects with concrete values.
 */
public class MismatchedLaterality extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(MismatchedLaterality.class);
	private static final String COMMON_HEADERS = "Concept, FSN, SemTag, Expression";
	private static final String SEMTAG_BODY_STRUCTURE = "(body structure)";

	Set<String> hierarchies = new HashSet<>();
	Set<Concept> reportedSuspect = new HashSet<>();
	Map<String, Concept> fsnMap = new HashMap<>();
	
	public static final String STR_LEFT = "left";
	public static final String STR_RIGHT = "right";
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		TermServerScript.run(MismatchedLaterality.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
		super.init(run);
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
		
		LOGGER.info("Populating FSN map for all concepts");
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActiveSafely()) {
				fsnMap.put(c.getFsn().toLowerCase(), c);
			}
		}
	}

	@Override
	public void runJob() throws TermServerScriptException {
		for (String hierarchy : hierarchies) {
			for (Concept c : findConcepts(hierarchy)) {
				if (c.getId().equals("449537006")) {
					LOGGER.debug("Here");
				}
				if (!inScope(c)) {
					continue;
				}
				boolean hasLateralizedFSN = hasLateralizedFSN(c);
				boolean hasBilateralFSN = hasBilateralFSN(c);
				boolean hasLateralizedModel = hasLateralizedModel(c, 0);  //Only check 1 level downs
				boolean hasBilaterlizedModel = hasBilateralizedModel(c, 0);
				if (hasBilateralFSN && !hasBilaterlizedModel) {
					report(QUINARY_REPORT, c, c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
				} else if (!hasBilateralFSN && hasBilaterlizedModel) {
					report(SENARY_REPORT, c, c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
				} else if (hasBilateralFSN && hasBilaterlizedModel) {
					//All good here
				} else if (hasLateralizedFSN && !hasLateralizedModel) {
					report(SECONDARY_REPORT, c, c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
				} else if (!hasLateralizedFSN && hasLateralizedModel) {
					report(TERTIARY_REPORT, c, c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
				}
				
				if (hasLateralizedFSN && !hasBilateralFSN) {
					checkForCounterpartLaterality(c);
				}
			}
		}
	}

	private void checkForCounterpartLaterality(Concept c) throws TermServerScriptException {
		if (hasLateralizedFSN(c, STR_LEFT)) {
			checkForCounterpartLaterality(c, STR_LEFT, STR_RIGHT);
		} else {
			checkForCounterpartLaterality(c, STR_RIGHT, STR_LEFT);
		}
	}

	private void checkForCounterpartLaterality(Concept c, String current, String siblingLaterality) throws TermServerScriptException {
		//What would the name of the sibling concept be?
		String origFSN = " " + c.getFsn().toLowerCase();
		String targetFSN = origFSN.replace(" " + current + " ", " " + siblingLaterality + " ").trim();
		Concept sibling = fsnMap.get(targetFSN);
		if (sibling == null) {
			if (hasUnlaterizedLeftOrRightBodyStructure(c)) {
				report(SEPTENARY_REPORT, c, "Suggests need for counterpart \"" + targetFSN + "\" but relates to an unlateralized body structure");
			} else {
				report(PRIMARY_REPORT, c, targetFSN);
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

	private boolean hasUnlaterizedLeftOrRightBodyStructure(Concept c) {
		//Return true if this concept has been modelled with a left or right body structure which is
		//not actually lateralizable
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			String semTag = SnomedUtilsBase.deconstructFSN(r.getTarget().getFsn())[1];
			if (semTag.equals(SEMTAG_BODY_STRUCTURE )) {
				Concept bodyStructure = r.getTarget();
				//Does the body structure feature 'left' or 'right' in its FSN, but isn't actually lateralized in the sense
				//of being symmetrical around the midsagittal plane?
				if (!isLateralized(bodyStructure)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean hasLateralizedModel(Concept c, int level) throws TermServerScriptException {
		//So either a target value is a type of laterality or it is itself 
		//lateralized - but we're only expecting that in a body structure
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if ((r.isNotConcrete()) && (r.getTarget().equals(LEFT) || r.getTarget().equals(RIGHT))) {
				String semTag = SnomedUtilsBase.deconstructFSN(
						c.getFsn())[1];
				if (!semTag.equals(SEMTAG_BODY_STRUCTURE) && !reportedSuspect.contains(c)) {
					report(QUATERNARY_REPORT, c, c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
					reportedSuspect.add(c);
				}
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
				String semTag = SnomedUtilsBase.deconstructFSN(c.getFsn())[1];
				if (!semTag.equals(SEMTAG_BODY_STRUCTURE) && !reportedSuspect.contains(c)) {
					report(TERTIARY_REPORT, c ,c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
					reportedSuspect.add(c);
				}
				leftPresent = true;
			} if (r.getTarget().equals(RIGHT)) {
				String semTag = SnomedUtilsBase.deconstructFSN(c.getFsn())[1];
				if (!semTag.equals(SEMTAG_BODY_STRUCTURE) && !reportedSuspect.contains(c)) {
					report(TERTIARY_REPORT, c ,c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
					reportedSuspect.add(c);
				}
				rightPresent = true;
			} else if (level == 0 && hasLaterality(r.getTarget(), LEFT)) {
				leftPresent = true;
			} else if (level == 0 && hasLaterality(r.getTarget(), RIGHT)) {
				rightPresent = true;
			}
		}
		return leftPresent && rightPresent;
	}

	private boolean hasLaterality(Concept c, Concept laterality) {
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.isNotConcrete() && r.getTarget().equals(laterality)) {
				return true;
			}
		}
		return false;
	}

	private boolean isLateralized(Concept c) {
		return hasLaterality(c, LEFT) || hasLaterality(c, RIGHT);
	}
}

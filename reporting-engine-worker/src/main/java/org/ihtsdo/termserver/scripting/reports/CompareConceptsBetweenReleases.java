package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.snapshot.SnapshotConfiguration;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompareConceptsBetweenReleases extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(CompareConceptsBetweenReleases.class);

	private static final String CONCEPT_IDS = "Concepts";
	private static final String PREV_RELEASE = "Previous Release";
	private static final String THIS_RELEASE = "This Release";

	protected String prevRelease;
	protected String thisRelease;

	private final Set<String> conceptIdsOfInterest = new HashSet<>();
	private final Map <String, ConceptState> conceptStates = new HashMap<>();

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(CONCEPT_IDS, "22298006,386661006,713427006,44054006,444814009,84757009,195967001,128462008,38341003,233604007");
		params.put(PREV_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_20260101T120000Z.zip");
		params.put(THIS_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_20260701T120000Z.zip");
		TermServerScript.run(CompareConceptsBetweenReleases.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(CONCEPT_IDS).withType(JobParameter.Type.CONCEPT_LIST).withMandatory()
				.add(THIS_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE).withMandatory()
				.add(PREV_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE).withMandatory()
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.QI))
				.withName("Compare Concepts Between Releases")
				.withDescription("This report compares properties of specified concepts between two releases.  The issue count shows the number of differences in those concepts between the two specified releases")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		try {
			prevRelease = run.getMandatoryParamValue(PREV_RELEASE);
			thisRelease = run.getMandatoryParamValue(THIS_RELEASE);
			getConceptsOfInterest(run.getMandatoryParamValue(CONCEPT_IDS));
		} catch (IllegalArgumentException e) {
			throw new TermServerScriptException("Mandatory parameters are missing: " + e.getMessage(), e);
		}
		super.init(run);
	}

	private void getConceptsOfInterest(String sctIds) {
		// Regular expression to match numbers before the pipe symbol
		String regex = "^\\d+";
		Pattern pattern = Pattern.compile(regex);

		// Split at comma and extract SCTID
		Arrays.stream(sctIds.split(",")).forEach(sctId -> {
			Matcher m = pattern.matcher(sctId);
			if (m.find()) {
				conceptIdsOfInterest.add(m.group());
			}
		});
	}

	@Override
	protected void loadProjectSnapshot() throws TermServerScriptException {
		SnapshotConfiguration previousReleaseConfig = new SnapshotConfiguration();
		previousReleaseConfig.setSource(prevRelease);

		LOGGER.info("Previous (historic) data is being loaded: {}", previousReleaseConfig.getSource());
		try {
			getArchiveManager().loadSnapshot(this, previousReleaseConfig);
			populateConceptState();
			gl.reset();
		} catch (TermServerScriptException e) {
			throw new TermServerScriptException("Previous release data generation failed due to " + e.getMessage(), e);
		}
		loadCurrentPosition();
	}

	private void populateConceptState() throws TermServerScriptException {
		for (String sctId : conceptIdsOfInterest) {
			Concept c = gl.getConcept(sctId, false, false);
			conceptStates.put(sctId, new ConceptState(c));
		}
		LOGGER.info("Populated 'previous' state of {} concepts", conceptStates.size());
	}

	protected void loadCurrentPosition() throws TermServerScriptException {
		SnapshotConfiguration currentReleaseConfig = getSnapshotConfiguration();
		currentReleaseConfig.setSource(thisRelease);

		LOGGER.info("Previous (historic) data generated. Now loading 'current' position: {}", currentReleaseConfig.getSource());
		getArchiveManager().loadSnapshot(this, currentReleaseConfig);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Id, Active, FSN, DefintionStatus, Descriptions, StatedExpression, InferredExpression",
				"Id, Active, FSN, DefintionStatus, Descriptions, StatedExpression, InferredExpression",
				"Id, FSN, SemTag, Field, Before, After",
		};
		String[] tabNames = new String[] {
				getDateFromRelease(prevRelease),
				getDateFromRelease(thisRelease),
				"Differences"
		};
		super.postInit(GFOLDER_QI, tabNames, columnHeadings, false);
	}

	private String getDateFromRelease(String release) {
		String regex = "\\d{8}(?=T)";

		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(release);

		if (matcher.find()) {
			return matcher.group();
		}
		return release;
	}

	@Override
	public void runJob() throws TermServerScriptException {
		examineConcepts();
		LOGGER.info("Job complete");
	}
	
	public void examineConcepts() throws TermServerScriptException { 
		LOGGER.info("Examining {} concepts of interest", conceptIdsOfInterest.size());
		for (String sctId : conceptIdsOfInterest) {
			Concept c = gl.getConcept(sctId, false, false);
			ConceptState previousState = conceptStates.get(c.getConceptId());
			report(PRIMARY_REPORT, previousState.toReportString());
			report(SECONDARY_REPORT, new ConceptState(c).toReportString());
			for (Difference difference : previousState.findDifferences(c)) {
				countIssue(c);
				report(TERTIARY_REPORT, c, difference.fieldName, difference.before, difference.after);
			}
		}
	}

	private class ConceptState {
		String id;
		boolean exists;
		boolean active;
		String fsn;
		DefinitionStatus definitionStatus;
		String descriptions;
		String statedExpression;
		String inferredExpression;

		String[] toReportString() {
			return new String[] {id, active ? "Y" : "N", fsn, exists ? definitionStatus.toString() : "", descriptions, statedExpression, inferredExpression};
		}

		ConceptState(Concept c) {
			if (c == null) {
				exists = false;
			} else {
				exists = true;
				id = c.getConceptId();
				active = c.isActive();
				fsn = c.getFsn();
				definitionStatus = c.getDefinitionStatus();
				descriptions = SnomedUtils.getDescriptions(c);
				statedExpression = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
				inferredExpression = c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP);
			}
		}

		List<Difference> findDifferences(Concept c) {
			List<Difference> differences = new ArrayList<>();
			if (!exists && c != null) {
				Difference d = new Difference();
				d.fieldName = "Exists";
				d.before = "N";
				d.after = "Y";
				differences.add(d);
				return differences;
			} else if (c == null) {
				Difference d = new Difference();
				d.fieldName = "Exists";
				d.before = "Y";
				d.after = "N";
				differences.add(d);
				return differences;
			}

			if (!active == c.isActiveSafely()) {
				Difference d = new Difference();
				d.fieldName = "Active";
				d.before = active?"Y":"N";
				d.after = c.isActiveSafely()? "Y":"N";
				differences.add(d);
			}

			if (!fsn.equals(c.getFsn())) {
				Difference d = new Difference();
				d.fieldName = "FSN";
				d.before = fsn;
				d.after = c.getFsn();
				differences.add(d);
			}

			if (!definitionStatus.equals(c.getDefinitionStatus())) {
				Difference d = new Difference();
				d.fieldName = "Definition Status";
				d.before = definitionStatus.toString();
				d.after = c.getDefinitionStatus().toString();
				differences.add(d);
			}

			if (!descriptions.equals(SnomedUtils.getDescriptions(c))) {
				Difference d = new Difference();
				d.fieldName = "Descriptions";
				d.before = descriptions;
				d.after = SnomedUtils.getDescriptions(c);
				differences.add(d);
			}

			if (!statedExpression.equals(c.toExpression(CharacteristicType.STATED_RELATIONSHIP))) {
				Difference d = new Difference();
				d.fieldName = "Stated Expression";
				d.before = statedExpression;
				d.after = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
				differences.add(d);
			}

			if (!inferredExpression.equals(c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP))) {
				Difference d = new Difference();
				d.fieldName = "Inferred Expression";
				d.before = inferredExpression;
				d.after = c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP);
				differences.add(d);
			}
			return differences;
		}
	}

	private class Difference {
		String fieldName;
		String before;
		String after;
	}
}

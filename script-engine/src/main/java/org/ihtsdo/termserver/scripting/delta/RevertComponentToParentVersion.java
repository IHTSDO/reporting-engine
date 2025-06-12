package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Branch;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Produce Delta archive containing components reverted to their parent version.
 */
public class RevertComponentToParentVersion extends DeltaGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(RevertComponentToParentVersion.class);

	/*
	 * State of the component on this branch will be used to determine whether the component should be restored.
	 */
	private static final String BRANCH_PARENT = ""; // Give me a value, i.e. MAIN/SNOMEDCT-NO

	/**
	 * State of the component on this branch will be used to determine whether the component should be restored.
	 */
	private static final String BRANCH_CHILD = ""; // Give me a value, i.e. MAIN/SNOMEDCT-NO/NO8

	/**
	 * Identifiers of ReferenceSetMembers that may be reverted.
	 */
	private static final List<String> REVERT_MEMBERS = List.of(
			// Give me a value, i.e. 97678dc2-560b-5dc3-ac2d-e5b6e5c76a6b
	);

	/**
	 * Identifiers of Relationships that may be reverted.
	 */
	private static final List<String> REVERT_RELATIONSHIPS = List.of(
			// Give me a value, i.e.
	);

	/**
	 * Identifiers of Descriptions that may be reverted.
	 */
	private static final List<String> REVERT_DESCRIPTIONS = List.of(
			// Give me a value, i.e. 3502010019
	);

	private static boolean forceOutput = false;

	public static void main(String[] args) throws Exception {
		RevertComponentToParentVersion app = new RevertComponentToParentVersion();
		try {
			String now = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
			app.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
			app.newIdsRequired = false;
			app.packageDir = "output" + File.separator + "Delta_" + now + File.separator;
			app.init(args);
			app.postInit(GFOLDER_ADHOC_UPDATES);
			app.validateScriptArguments();
			app.process();
			app.flushFiles(false);
			SnomedUtils.createArchive(new File(app.outputDirName));
		} finally {
			app.finish();
		}
	}

	@Override
	public void postInit(String googleFolder) throws TermServerScriptException {
		String[] columnHeadings = new String[]{
				"Id, Type",
				"Id, Type, Revert Reason",
				"Id, Type, Ignored Reason"
		};

		String[] tabNames = new String[]{
				"Components Requested",
				"Components Reverted",
				"Components Ignored"
		};

		super.postInit(GFOLDER_ADHOC_UPDATES, tabNames, columnHeadings);
	}

	private void validateScriptArguments() throws TermServerScriptException {
		if (Objects.equals(BRANCH_PARENT, "")) {
			throw new TermServerScriptException("BRANCH_PARENT required.");
		}

		if (Objects.equals(BRANCH_CHILD, "")) {
			throw new TermServerScriptException("BRANCH_CHILD required.");
		}

		if (Objects.equals(BRANCH_PARENT, BRANCH_CHILD)) {
			throw new TermServerScriptException("BRANCH_PARENT and BRANCH_CHILD have the same value.");
		}
	}

	@Override
	protected void process() throws TermServerScriptException {
		// Verify branches exist
		Branch parentBranch = getBranchOrThrow(BRANCH_PARENT);
		Branch childBranch = getBranchOrThrow(BRANCH_CHILD);

		processMembers(parentBranch, childBranch);
		processRelationships(parentBranch, childBranch);
		processDescriptions(parentBranch, childBranch);
	}

	private void processMembers(Branch parentBranch, Branch childBranch) throws TermServerScriptException {
		if (REVERT_MEMBERS.isEmpty()) {
			return;
		}

		// Collect members on both parent and child
		Map<String, RefsetMember> parentMembers = getMembers(parentBranch.getPath(), REVERT_MEMBERS);
		Map<String, RefsetMember> childMembers = getMembers(childBranch.getPath(), REVERT_MEMBERS);

		if (childMembers.isEmpty()) {
			return;
		}

		// Produce Delta of reverted components
		for (Map.Entry<String, RefsetMember> entrySet : childMembers.entrySet()) {
			String memberId = entrySet.getKey();
			RefsetMember childMember = entrySet.getValue();
			RefsetMember parentMember = parentMembers.get(memberId);

			report(0, memberId, "ReferenceSetMember");
			if (parentMember == null) {
				LOGGER.info(String.format("RefsetMember %s not found on parent and will be ignored by script.", memberId));
				report(TERTIARY_REPORT, memberId, "ReferenceSetMember", "Doesn't exist on parent. Cannot be restored.");
				continue;
			}

			if (jumpedModule(childMember, parentMember)) {
				report(SECONDARY_REPORT, memberId, "ReferenceSetMember", "Looks to have incorrectly jumped module.");
				writeToRF2File(getFileNameByRefsetId(parentMember.getRefsetId()), parentMember.toRF2());
			} else if (illegalChangeOfAdditionalField(childMember, parentMember)) {
				report(SECONDARY_REPORT, memberId, "ReferenceSetMember", "Looks to have an illegal additional field change.");
				writeToRF2File(getFileNameByRefsetId(parentMember.getRefsetId()), parentMember.toRF2());
			} else if (forceOutput) {
				report(SECONDARY_REPORT, memberId, "ReferenceSetMember", "'ForcedOutput' set to true");
				writeToRF2File(getFileNameByRefsetId(parentMember.getRefsetId()), parentMember.toRF2());
			} else {
				report(TERTIARY_REPORT, memberId, "ReferenceSetMember", "Unknown fault.");
			}
		}
	}

	private void processRelationships(Branch parentBranch, Branch childBranch) throws TermServerScriptException {
		if (REVERT_RELATIONSHIPS.isEmpty()) {
			return;
		}

		// Collect relationships on both parent and child
		Map<String, Relationship> parentRelationships = getRelationships(parentBranch.getPath(), REVERT_RELATIONSHIPS);
		Map<String, Relationship> childRelationships = getRelationships(childBranch.getPath(), REVERT_RELATIONSHIPS);

		if (childRelationships.isEmpty()) {
			return;
		}

		// Produce Delta of reverted components
		for (Map.Entry<String, Relationship> entrySet : childRelationships.entrySet()) {
			String relationshipId = entrySet.getKey();
			Relationship parentRelationship = parentRelationships.get(relationshipId);

			report(0, relationshipId, "Relationship");

			if (parentRelationship == null) {
				LOGGER.info("Relationship {} not found on parent and will be ignored by script.", relationshipId);
				report(TERTIARY_REPORT, relationshipId, "Relationship", "Doesn't exist on parent. Cannot be restored.");
				continue;
			}

			if (forceOutput) {
				report(SECONDARY_REPORT, relationshipId, "Relationship", "'ForcedOutput' set to true");
				writeToRF2File(relDeltaFilename, parentRelationship.toRF2());
			} else {
				report(TERTIARY_REPORT, relationshipId, "Relationship", "Unknown fault.");
			}
		}
	}

	private void processDescriptions(Branch parentBranch, Branch childBranch) throws TermServerScriptException {
		if (REVERT_DESCRIPTIONS.isEmpty()) {
			return;
		}

		// Collect descriptions on both parent and child
		Map<String, Description> parentDescriptions = getDescriptions(parentBranch.getPath(), REVERT_DESCRIPTIONS);
		Map<String, Description> childDescriptions = getDescriptions(childBranch.getPath(), REVERT_DESCRIPTIONS);

		if (childDescriptions.isEmpty()) {
			return;
		}

		// Produce Delta of reverted components
		for (Map.Entry<String, Description> entrySet : childDescriptions.entrySet()) {
			String descriptionId = entrySet.getKey();
			Description childDescription = entrySet.getValue();
			Description parentDescription = parentDescriptions.get(descriptionId);

			report(PRIMARY_REPORT, descriptionId, "Description");
			if (parentDescription == null) {
				LOGGER.info(String.format("Description %s not found on parent and will be ignored by script.", descriptionId));
				report(TERTIARY_REPORT, descriptionId, "Description", "Doesn't exist on parent. Cannot be restored.");
				continue;
			}

			if (lostEffectiveTime(childDescription, parentDescription)) {
				report(SECONDARY_REPORT, descriptionId, "Description", "Looks to have lost effective time.");
				writeToRF2File(descDeltaFilename, parentDescription.toRF2());
			} else if (jumpedModule(childDescription, parentDescription)) {
				report(SECONDARY_REPORT, descriptionId, "Description", "Looks to have jumped module.");
				writeToRF2File(descDeltaFilename, parentDescription.toRF2());
			} else {
				report(TERTIARY_REPORT, descriptionId, "Description", "No fault detected, ignoring.");
			}
		}
	}

	private Map<String, Description> getDescriptions(String branchPath, List<String> descriptionIds) {
		LOGGER.info(String.format("Fetching Descriptions for branch %s.", branchPath));
		Map<String, Description> descriptions = new HashMap<>();
		for (int x = 0; x < descriptionIds.size(); x++) {
			String identifier = descriptionIds.get(x);
			Description description = tsClient.getDescription(identifier, branchPath);
			descriptions.put(description.getId(), description);
			LOGGER.info(String.format("%s/%s Descriptions fetched.", x + 1, descriptionIds.size()));
		}

		return descriptions;
	}

	private Branch getBranchOrThrow(String branchPath) throws TermServerScriptException {
		Branch branch = tsClient.getBranch(branchPath);
		if (branch == null) {
			throw new TermServerScriptException(String.format("Cannot find branch with path '%s'.", branchPath));
		}

		return branch;
	}

	private Map<String, Relationship> getRelationships(String branchPath, List<String> relationshipIds) {
		LOGGER.info("Fetching relationships for branch {}.", branchPath);

		Map<String, Relationship> relationships = new HashMap<>();

		for (int x = 0; x < relationshipIds.size(); x++) {
			String identifier = relationshipIds.get(x);
			Relationship relationship = tsClient.getRelationship(identifier, branchPath);
			relationships.put(relationship.getId(), relationship);
			LOGGER.info("{}/{} relationships fetched.", x + 1, relationshipIds.size());
		}

		return relationships;
	}
	private Map<String, RefsetMember> getMembers(String branchPath, List<String> referenceSetMemberIds) {
		LOGGER.info(String.format("Fetching RefsetMembers for branch %s.", branchPath));
		Map<String, RefsetMember> refSetMembers = new HashMap<>();
		for (int x = 0; x < referenceSetMemberIds.size(); x++) {
			String identifier = referenceSetMemberIds.get(x);
			RefsetMember member = tsClient.getRefsetMember(identifier, branchPath);
			refSetMembers.put(member.getId(), member);
			LOGGER.info(String.format("%s/%s RefsetMembers fetched.", x + 1, referenceSetMemberIds.size()));
		}

		return refSetMembers;
	}

	private boolean jumpedModule(RefsetMember childMember, RefsetMember parentMember) {
		boolean matchActive = Objects.equals(childMember.isActive(), parentMember.isActive());
		boolean matchModule = Objects.equals(childMember.getModuleId(), parentMember.getModuleId());
		boolean matchReleased = Objects.equals(childMember.getReleased(), parentMember.getReleased());
		boolean matchReleasedEffectiveTime = Objects.equals(childMember.getReleasedEffectiveTime(), parentMember.getReleasedEffectiveTime());
		boolean matchRefsetId = Objects.equals(childMember.getRefsetId(), parentMember.getRefsetId());
		boolean matchReferencedComponentId = Objects.equals(childMember.getReferencedComponentId(), parentMember.getReferencedComponentId());
		boolean matchAdditionalFields = Objects.equals(childMember.getAdditionalFields(), parentMember.getAdditionalFields());
		boolean matchEffectiveTime = Objects.equals(childMember.getEffectiveTime(), parentMember.getEffectiveTime());

		if (!matchModule && !matchEffectiveTime) {
			return matchActive && matchReleased && matchReleasedEffectiveTime && matchRefsetId && matchReferencedComponentId && matchAdditionalFields;
		}

		return false;
	}

	private boolean illegalChangeOfAdditionalField(RefsetMember childMember, RefsetMember parentMember) {
		if (childMember.isActive()) {
			return false;
		}

		boolean matchActive = Objects.equals(childMember.isActive(), parentMember.isActive());
		boolean matchModule = Objects.equals(childMember.getModuleId(), parentMember.getModuleId());
		boolean matchReleased = Objects.equals(childMember.getReleased(), parentMember.getReleased());
		boolean matchReleasedEffectiveTime = Objects.equals(childMember.getReleasedEffectiveTime(), parentMember.getReleasedEffectiveTime());
		boolean matchRefsetId = Objects.equals(childMember.getRefsetId(), parentMember.getRefsetId());
		boolean matchReferencedComponentId = Objects.equals(childMember.getReferencedComponentId(), parentMember.getReferencedComponentId());
		boolean matchAdditionalFields = Objects.equals(childMember.getAdditionalFields(), parentMember.getAdditionalFields());
		boolean matchEffectiveTime = Objects.equals(childMember.getEffectiveTime(), parentMember.getEffectiveTime());

		if (!matchAdditionalFields && !matchEffectiveTime) {
			return matchActive && matchModule && matchReleased && matchReleasedEffectiveTime && matchRefsetId && matchReferencedComponentId;
		}

		return false;
	}

	private boolean lostEffectiveTime(Description childDescription, Description parentDescription) {
		boolean matchActive = Objects.equals(childDescription.isActive(), parentDescription.isActive());
		boolean matchModule = Objects.equals(childDescription.getModuleId(), parentDescription.getModuleId());
		boolean matchConceptId = Objects.equals(childDescription.getConceptId(), parentDescription.getConceptId());
		boolean matchLanguageCode = Objects.equals(childDescription.getLang(), parentDescription.getLang());
		boolean matchType = Objects.equals(childDescription.getType(), parentDescription.getType());
		boolean matchCaseSignificance = Objects.equals(childDescription.getCaseSignificance(), parentDescription.getCaseSignificance());
		boolean matchReleased = Objects.equals(childDescription.getReleased(), parentDescription.getReleased());
		boolean matchEffectiveTime = Objects.equals(childDescription.getEffectiveTime(), parentDescription.getEffectiveTime());

		if (!matchEffectiveTime) {
			return matchActive && matchModule && matchReleased && matchConceptId && matchLanguageCode && matchType && matchCaseSignificance;
		}

		return false;
	}

	private boolean jumpedModule(Description childDescription, Description parentDescription) {
		boolean matchActive = Objects.equals(childDescription.isActive(), parentDescription.isActive());
		boolean matchModule = Objects.equals(childDescription.getModuleId(), parentDescription.getModuleId());
		boolean matchConceptId = Objects.equals(childDescription.getConceptId(), parentDescription.getConceptId());
		boolean matchLanguageCode = Objects.equals(childDescription.getLang(), parentDescription.getLang());
		boolean matchType = Objects.equals(childDescription.getType(), parentDescription.getType());
		boolean matchCaseSignificance = Objects.equals(childDescription.getCaseSignificance(), parentDescription.getCaseSignificance());
		boolean matchReleased = Objects.equals(childDescription.getReleased(), parentDescription.getReleased());
		boolean matchEffectiveTime = Objects.equals(childDescription.getEffectiveTime(), parentDescription.getEffectiveTime());

		if (!matchModule && !matchEffectiveTime) {
			return matchActive && matchReleased && matchConceptId && matchLanguageCode && matchType && matchCaseSignificance;
		}

		return false;
	}

	private String getFileNameByRefsetId(String refsetId) throws TermServerScriptException {
		 //See RF2Constants for constants for these values
		switch (refsetId) {
			case "734138000": // |Anatomy structure and entire association reference set|
			case "734139008": // |Anatomy structure and part association reference set|
			case "1186921001": // |POSSIBLY REPLACED BY association reference set|
			case "1186924009": // |PARTIALLY EQUIVALENT TO association reference set|
			case "900000000000523009": // |POSSIBLY EQUIVALENT TO association reference set|
			case "900000000000524003": // |MOVED TO association reference set|
			case "900000000000525002": // |MOVED FROM association reference set|
			case "900000000000526001": // |REPLACED BY association reference set|
			case "900000000000527005": // |SAME AS association reference set|
			case "900000000000528000": // |WAS A association reference set|
			case "900000000000530003": // |ALTERNATIVE association reference set|
			case "900000000000531004": // |REFERS TO concept association reference set|
				return assocDeltaFilename;
			case "900000000000490003": // |Description inactivation indicator reference set|
			case "900000000000489007": // |Concept inactivation indicator reference set|
				return attribValDeltaFilename;
			case GB_ENG_LANG_REFSET:
			case US_ENG_LANG_REFSET:
				return langDeltaFilename;
			case SCTID_OWL_AXIOM_REFSET:
				return owlDeltaFilename;
			default:
				throw new TermServerScriptException(String.format("Cannot get filename for reference set '%s'", refsetId));
		}
	}
}

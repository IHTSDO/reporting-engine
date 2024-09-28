package org.ihtsdo.termserver.scripting.reports;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.util.concurrent.AtomicLongMap;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.EclCache;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.client.TermServerClient;
import org.ihtsdo.termserver.scripting.domain.AssociationEntry;
import org.ihtsdo.termserver.scripting.domain.CodeSystem;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Template;
import org.ihtsdo.termserver.scripting.domain.mrcm.MRCMAttributeDomain;
import org.ihtsdo.termserver.scripting.reports.qi.AllKnownTemplates;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.scheduler.domain.JobParameter.Type;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DEVICES-92, QI-784
 * CDI-52 Update to run successfully against projects with concrete values
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InactivationImpactAssessment extends AllKnownTemplates implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(InactivationImpactAssessment.class);

	static public String REFSET_ECL = "(< 446609009 |Simple type reference set| OR < 900000000000496009 |Simple map type reference set|) MINUS 900000000000497000 |CTV3 simple map reference set (foundation metadata concept)|";
	private static String CONCEPT_INACTIVATIONS = "Concepts to inactivate";
	private static String INCLUDE_INFERRED = "Include Inferred Relationships";
	private Collection<Concept> referenceSets;
	private List<Concept> emptyReferenceSets;
	private List<Concept> outOfScopeReferenceSets;
	private AtomicLongMap<Concept> refsetSummary = AtomicLongMap.create();
	private static int CHUNK_SIZE = 200;
	private boolean includeInferred = false;
	private String selectionCriteria;
	private boolean isECL = false;
	
	private Collection<Concept> inactivatingConcepts = new ArrayList<>();
	private List<String> inactivatingConceptIds = new ArrayList<>();

	private Map<String, DerivativeLocation> derivativeLocationMap;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(CONCEPT_INACTIVATIONS, "<< 48694002 |Anxiety|");  //Found in the Nursing Issues Reset
		params.put(INCLUDE_INFERRED, "false");
		TermServerScript.run(InactivationImpactAssessment.class, args, params);
	}
	
	@Override
	public void init(JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleasedFlag(true);
		importDerivativeLocations();
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc reports
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		referenceSets = findConcepts(REFSET_ECL);
		removeEmptyNoScopeAndDerivativeRefsets();
		LOGGER.info ("Recovered {} simple reference sets and maps", referenceSets.size());

		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Issue, Detail, Additional Detail"};
		String[] tabNames = new String[] {	
				"Impact Details"};
		
		selectionCriteria = jobRun.getMandatoryParamValue(CONCEPT_INACTIVATIONS);
		isECL = SnomedUtils.isECL(selectionCriteria);
		if (isECL) {
			//With ECL selection we don't need to worry about the concept already being inactive
			inactivatingConcepts = findConcepts(selectionCriteria);
			inactivatingConceptIds = inactivatingConcepts.stream()
					.map(c -> c.getId())
					.collect(Collectors.toList());
		} else {
			for (String inactivatingConceptId : selectionCriteria.split(",")) {
				inactivatingConceptId = inactivatingConceptId.trim();
				Concept c = gl.getConcept(inactivatingConceptId);
				if (c.isActiveSafely()) {
					inactivatingConceptIds.add(inactivatingConceptId);
					inactivatingConcepts.add(c);
				} else {
					report (c, " is already inactive");
				}
			}
		}
		
		if (inactivatingConcepts.isEmpty()) {
			throw new TermServerScriptException("Selection criteria '" + selectionCriteria + "' represents 0 active concepts");
		}
		
		includeInferred = jobRun.getParameters().getMandatoryBoolean(INCLUDE_INFERRED);
		super.postInit(tabNames, columnHeadings, false);
	}

	private void removeEmptyNoScopeAndDerivativeRefsets() throws TermServerScriptException {
		LOGGER.info("Checking local refsets for emptiness, out of scope and derivative refsets");
		emptyReferenceSets = new ArrayList<>();
		outOfScopeReferenceSets = new ArrayList<>();
		List<Concept> derivativeRefsets = new ArrayList<>();
		for (Concept refset : referenceSets) {
			if (!inScope(refset)) {
				outOfScopeReferenceSets.add(refset);
				continue;
			}
			if (getConceptsCount("^" + refset) == 0) {
				emptyReferenceSets.add(refset);
			} else if (derivativeLocationMap.containsKey(refset.getId())) {
				derivativeRefsets.add(refset);
			} else {
				refsetSummary.put(refset, 0);
			}
			try { Thread.sleep(1 * 1000); } catch (Exception e) {}
		}
		referenceSets.removeAll(emptyReferenceSets);
		referenceSets.removeAll(derivativeRefsets);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(CONCEPT_INACTIVATIONS).withType(Type.STRING).withMandatory()
					.withDescription("List of concept ids to inactivated, comma separated")
				.add("Notes").withType(Type.STRING)
					.withDescription("Any notes the Author might want to make about why they're running this report.  Has no functional impact")
				.add(INCLUDE_INFERRED).withType(Type.BOOLEAN).withDefaultValue(false).withMandatory()
				.add(SERVER_URL).withType(JobParameter.Type.HIDDEN).withMandatory()
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Inactivation Impact Assessment")
				.withDescription("This report takes in a list of concepts (comma separated SCTIDs or ECL) " +
						"to be inactivated and reports if they're current used as attribute values, parents of other "+
						"concepts (not being inactivated), in refsets or as historical association targets.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		checkHighVolumeUsage();
		checkChildInactivation();
		if (isECL) {
			checkRefsetUsageECL();
		} else {
			checkRefsetUsageEnumerated();
		}
		checkDerivativeUsage();
		checkAttributeUsage();
		checkHistoricalAssociations();
		checkMRCM();
		checkTemplates();
	}

	private void checkChildInactivation() throws TermServerScriptException {
		for (Concept c : inactivatingConcepts) {
			for (Concept child : c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
				//If we're not also inactivating the child, that could be a problem
				if (!inactivatingConcepts.contains(child)) {
					report (c, "has child not scheduled for inactivation", child);
				}
			}
		}
	}

	private void checkAttributeUsage() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActiveSafely() && !inactivatingConcepts.contains(c)) {
				Set<Relationship> rels = includeInferred ? c.getRelationships() : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
				for (Relationship r : rels) {
					if (r.isNotConcrete() && Boolean.TRUE.equals(r.isActive()) && !r.getType().equals(IS_A) && inactivatingConcepts.contains(r.getTarget())) {
						report(r.getTarget(), "used as attribute target value", c, r);
					}
				}
			}
		}
	}

	private void checkHistoricalAssociations() throws TermServerScriptException {
		for (Concept c : inactivatingConcepts) {
			for (AssociationEntry assoc : gl.usedAsHistoricalAssociationTarget(c)) {
				report (c, "used as target of historical association", gl.getConcept(assoc.getReferencedComponentId()), assoc);
			}
		}
	}

	private void checkRefsetUsageEnumerated() throws TermServerScriptException {
		LOGGER.debug ("Checking {} inactivating concepts against {} refsets", inactivatingConceptIds.size(),referenceSets.size());
		for (Concept refset : referenceSets) {
			EclCache eclCache = getDefaultEclCache();
			reportRefsetInactivationsAgainstEnumeratedList(refset, eclCache);
		}
	}

	private void checkRefsetUsageECL() throws TermServerScriptException {
		LOGGER.debug("Checking {} inactivating concepts against {} refsets", inactivatingConceptIds.size(), referenceSets.size());
		EclCache eclCache = getDefaultEclCache();
		for (Concept refset : referenceSets) {
			try {
				reportRefsetInactivationsAgainstEcl(refset, eclCache);
			} catch (TermServerScriptException e) {
				report(PRIMARY_REPORT, e.getMessage());
			}
		}
	}

	private void reportRefsetInactivationsAgainstEcl(Concept refset, EclCache eclCache) throws TermServerScriptException {
		String ecl = "^" + refset.getId() + " AND ( " + selectionCriteria + " )";
		String detail = "As per branch " + eclCache.getBranch() + " on " + eclCache.getServer();
		for (Concept c : eclCache.findConcepts(ecl)) {
			report (c, "active in refset", refset.getPreferredSynonym(), detail);
		}
	}

	private void reportRefsetInactivationsAgainstEnumeratedList(Concept refset, EclCache eclCache) throws TermServerScriptException {
		int conceptsProcessed = 0;
		do {
			String subsetList = "";
			for (int i = 0; i < CHUNK_SIZE && conceptsProcessed < inactivatingConceptIds.size(); i++) {
				if (i > 0) {
					subsetList += " OR ";
				}
				subsetList += inactivatingConceptIds.get(conceptsProcessed);
				conceptsProcessed++;
			}
			String ecl = "^" + refset.getId() + " AND ( " + subsetList + " )";
			for (Concept c : eclCache.findConcepts(ecl)) {
				report(c, "active in refset", refset.getPreferredSynonym());
			}
			try {
				Thread.sleep(1 * 200);
			} catch (Exception e) {}
		} while (conceptsProcessed < inactivatingConceptIds.size());
	}

	private void checkDerivativeUsage() throws TermServerScriptException {
		Map<String, Map<String, TermServerClient>> serverCodeSystemTsClientMap = new HashMap<>();
		Map<String, String> branchForCodeSystemMap = new HashMap<>();
		for (DerivativeLocation location : derivativeLocationMap.values()) {
			try {
				checkInactivationsForDerivative(location, serverCodeSystemTsClientMap, branchForCodeSystemMap);
			} catch (IllegalStateException | TermServerScriptException e) {
				report(PRIMARY_REPORT, e.getMessage());
			}
		}
	}

	private void checkInactivationsForDerivative(DerivativeLocation location, Map<String, Map<String, TermServerClient>> serverCodeSystemTsClientMap, Map<String, String> branchForCodeSystemMap) throws TermServerScriptException {
		//Keep a map of server specific clients for each codeSystem / server combination.
		//For SNOMEDCT-DERIVATIVES on the browser, for example, we should only need to do this once.
		Map<String, TermServerClient> codeSystemTsClientMap = serverCodeSystemTsClientMap.computeIfAbsent(location.server, k -> new HashMap<>());
		TermServerClient tsClient = codeSystemTsClientMap.computeIfAbsent(location.codeSystem, k -> new TermServerClient(location.server, location.codeSystem));

		//Now get an Ecl Cache which is specific to this server (via the tsClient) and the branch
		//Watch that the same branch on another server will not be considered distinct so that's a danger point.
		String branch = branchForCodeSystemMap.computeIfAbsent(location.codeSystem, k -> getBranchForCodeSystem(tsClient, location));
		EclCache eclCache = EclCache.getCache(branch, tsClient, gl, false);
		//Refset might not be known to the current project, so create a temporary concept
		Concept refset = new Concept(location.sctId);
		refset.setPreferredSynonym(location.pt);
		if (isECL) {
			reportRefsetInactivationsAgainstEcl(refset, eclCache);
		} else {
			reportRefsetInactivationsAgainstEnumeratedList(refset, eclCache);
		}
	}

	private String getBranchForCodeSystem(TermServerClient tsClient, DerivativeLocation location) {
		String reason = "";
		try {
			//What is the current branch for this CodeSystem?
			CodeSystem cs = tsClient.getCodeSystem(location.codeSystem);
			if (cs != null && cs.getLatestVersion() != null) {
				return cs.getLatestVersion().getBranchPath();
			}
		} catch (TermServerScriptException e) {
			reason = " due to " + e.getMessage();
		}

		throw new IllegalStateException("Unable to recover CodeSystem " + location.codeSystem + " from " + location.server + reason);
	}

	private void checkHighVolumeUsage() throws TermServerScriptException {
		LOGGER.debug ("Checking {} inactivating concepts against High Usage SCTIDs", inactivatingConceptIds.size());
		String fileName = "resources/HighVolumeSCTIDs.txt";
		LOGGER.debug ("Loading " + fileName );
		try {
			List<String> lines = Files.readLines(new File(fileName), Charsets.UTF_8);
			for (String line : lines) {
				String id = line.split(TAB)[0];
				if (inactivatingConceptIds.contains(id)) {
					report (gl.getConcept(id), "High Volume Usage (UK)");
				}
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to read " + fileName, e);
		}
	}

	private void checkMRCM() throws TermServerScriptException {
		checkRefsetFields("MRCM Domain", gl.getMRCMDomainManager().getMrcmDomainMap().values());

		checkRefsetFields("MRCM Attribute Domain - PreCoord", flattenMap(gl.getMRCMAttributeDomainManager().getMrcmAttributeDomainMapPreCoord()));
		checkRefsetFields("MRCM Attribute Domain - PostCoord", flattenMap(gl.getMRCMAttributeDomainManager().getMrcmAttributeDomainMapPostCoord()));
		checkRefsetFields("MRCM Attribute Domain - All", flattenMap(gl.getMRCMAttributeDomainManager().getMrcmAttributeDomainMapAll()));
		checkRefsetFields("MRCM Attribute Domain - New PreCoord", flattenMap(gl.getMRCMAttributeDomainManager().getMrcmAttributeDomainMapNewPreCoord()));

		checkRefsetFields("MRCM Attribute Range - PreCoord", gl.getMRCMAttributeRangeManager().getMrcmAttributeRangeMapPreCoord().values());
		checkRefsetFields("MRCM Attribute Range - PostCoord", gl.getMRCMAttributeRangeManager().getMrcmAttributeRangeMapPostCoord().values());
		checkRefsetFields("MRCM Attribute Range - All", gl.getMRCMAttributeRangeManager().getMrcmAttributeRangeMapAll().values());
		checkRefsetFields("MRCM Attribute Range - New PreCoord", gl.getMRCMAttributeRangeManager().getMrcmAttributeRangeMapNewPreCoord().values());
	}
	
	private Collection<? extends RefsetMember> flattenMap(
			Map<Concept, Map<Concept, MRCMAttributeDomain>> mrcmAttributeDomainMapPreCoord) {
		return mrcmAttributeDomainMapPreCoord.values().stream()
				.flatMap(m -> m.values().stream())
				.toList();
	}

	private void checkRefsetFields(String recordType, Collection<? extends RefsetMember> refsetMembers) throws TermServerScriptException {
		for(RefsetMember rm : refsetMembers) {
			//No need to check inactive refset members
			if (!rm.isActiveSafely()) {
				continue;
			}

			//Does this field contain any SCTIDs?  Check they exist and are active if so.
			validateRefsetMemberField(recordType, "Referenced Component ID", rm.getReferencedComponentId(), rm);
			for (String fieldName : rm.getAdditionalFieldNames()) {
				validateRefsetMemberField(recordType, fieldName, rm.getField(fieldName), rm);
			}
		}
	}

	private void validateRefsetMemberField(String recordType, String fieldName, String fieldValue, RefsetMember rm) throws TermServerScriptException {
		for (String sctId : SnomedUtils.extractSCTIDs(fieldValue)) {
			Concept c = gl.getConcept(sctId, false, false);
			if (c == null) {
				report(PRIMARY_REPORT, sctId, "Unknown in specified release/project", recordType + "/" + fieldName, rm);
			} else if (!c.isActiveSafely()) {
				report(c, "Inactive in specified release/project", recordType + "/" + fieldName, rm);
			}
		}
	}
	
	private void checkTemplates() throws TermServerScriptException {
		for (Concept c : inactivatingConcepts) {
			for (Template t : listTemplatesUsingConcept(c)) {
				report (c, "used in template ", t.getName());
			}
		}
	}

	@Override
	protected boolean report (Concept c, Object...details) throws TermServerScriptException {
		countIssue(c);
		return super.report (PRIMARY_REPORT, c, details);
	}

	private void importDerivativeLocations() throws TermServerScriptException {
		derivativeLocationMap = new HashMap<>();
		String fileName = "resources/derivative-locations.tsv";
		LOGGER.debug ("Loading {}", fileName );

		try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] columns = line.split("\t", -1);
				//Is this the header row?
				if (columns[0].equals("SCTID")) {
					continue;
				}

				if (columns.length >= 5) {
					DerivativeLocation location = new DerivativeLocation();
					location.sctId = columns[0];
					location.pt = columns[1];
					location.server = columns[2];
					location.codeSystem = columns[3];
					location.project = columns[4];
					derivativeLocationMap.put(location.sctId, location);
				}
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to read " + fileName, e);
		}
		LOGGER.info("Configured the location of {} derivatives", derivativeLocationMap.size());
	}

	private class DerivativeLocation {
		String sctId;
		String pt;
		String server;
		String codeSystem;
		String project;
	}

}

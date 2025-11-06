package org.ihtsdo.termserver.scripting.snapshot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.client.TermServerClient.ExportType;
import org.ihtsdo.termserver.scripting.client.TermServerClient.ExtractType;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * RP-272 Reconcile an exported snapshot against a generated one
 * 
 * NB Use the -f <file> option after the first time you've exported an archive.  Those things
 * take an hour to obtain.  Actually the last time I ran this I didn't get the snapshot out programmatically
 * and with it taking so long it might be easier to just do it manually and pick up with wget
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReconcileSnapshot extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReconcileSnapshot.class);

	private Map<String, Component> remainingComponents;
	private int componentsChecked = 0;
	private int totalToCheck = 0;
	private String[] modulesOfInterest = new String[] { SCTID_CORE_MODULE, SCTID_MODEL_MODULE };
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		TermServerScript.run(ReconcileSnapshot.class, args, params);
	}
	
	@Override
	public void postInit() throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"); //Release Validation
		String[] tabNames = new String[] {	"Concept", "Desc", "Stated", 
				"Inferred", "Lang", "InactInd", "HistAssoc",
				"TDefn", "Axioms"};
		String[] columnHeadings = new String[tabNames.length];
		for (int i=0; i< tabNames.length; i++) {
			columnHeadings[i] = "Concept, FSN, SemTag, Affected Component, Component Active, Snapshot vs Generated";
		}
		super.postInit(tabNames, columnHeadings);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("Snapshot Reconciliation")
				.withDescription("This report validates all components in a generated snapshot (formed by " + 
				"adding a delta to the previous release) against an exported snapshot.  Be aware that the " +
				"snapshot export performed by this report will lock the relevant project for 15 minutes")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		File exportedSnapshot = getInputFile();
		if (exportedSnapshot == null) {
			LOGGER.info("Exporting Snapshot...");
			exportedSnapshot = getSnapshot();
		} else {
			LOGGER.info("Loading snapshot {}", exportedSnapshot);
		}
		
		LOGGER.info("Generating local map of components");
		remainingComponents = gl.getComponentMap();
		totalToCheck = remainingComponents.size();
		LOGGER.info(totalToCheck + " components mapped");
		
		LOGGER.info("Validating " + exportedSnapshot);
		validateArchiveZip(exportedSnapshot);
		reportRemainder();
	}

	private File getSnapshot() throws TermServerScriptException {
		File snapshot = null;
		try {
			snapshot = File.createTempFile("snapshot_export-", ".zip");
			LOGGER.warn("Downloading Snapshot to: " + snapshot.getCanonicalPath());
			getTSClient().export(project.getBranchPath(), null, ExportType.UNPUBLISHED, ExtractType.SNAPSHOT, snapshot, false);
		} catch (TermServerScriptException |IOException e) {
			throw new TermServerScriptException("Unable to obtain " + project.getKey() + " snapshot",e);
		}
		return snapshot;
	}
	
	private void validateArchiveZip(File archive) throws TermServerScriptException {
		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(archive));
			ZipEntry ze = zis.getNextEntry();
			try {
				while (ze != null) {
					if (!ze.isDirectory()) {
						Path path = Paths.get(ze.getName());
						validateFile(path, zis);
					}
					ze = zis.getNextEntry();
				}
			} finally {
				try{
					zis.closeEntry();
					zis.close();
				} catch (Exception e){
					//Well, we tried.
				}
			}
		}catch (IOException e) {
			throw new TermServerScriptException("Unable to load " + archive,e);
		}
	}
	
	private void validateFile(Path path, InputStream is)  {
		String fileType = "Snapshot";
		try {
			String fileName = path.getFileName().toString();
			if (fileName.contains(fileType)) {
				if (fileName.contains("sct2_Concept_" )) {
					LOGGER.info("Validating Concept {} file: {}", fileType, fileName);
					validateComponentFile(is, ComponentType.CONCEPT);
				} else if (fileName.contains("Relationship_" )) {
					LOGGER.info("Validating " + fileName);
					validateComponentFile(is, ComponentType.INFERRED_RELATIONSHIP);
				} else if (fileName.contains("sct2_sRefset_OWLExpression" ) ||
						   fileName.contains("sct2_sRefset_OWLAxiom" )) {
					LOGGER.info("Validating Axiom " + fileType + " refset file: " + fileName);
					validateComponentFile(is, ComponentType.AXIOM);
				} else if (fileName.contains("sct2_Description_" )) {
					LOGGER.info("Validating Description {} file: {}", fileType, fileName);
					validateComponentFile(is, ComponentType.DESCRIPTION);
				} else if (fileName.contains("sct2_TextDefinition_" )) {
					LOGGER.info("Validating Text Definition {} file: {}", fileType, fileName);
					validateComponentFile(is, ComponentType.TEXT_DEFINITION);
				} else if (fileName.contains("der2_cRefset_ConceptInactivationIndicatorReferenceSet" )) {
					LOGGER.info("Validating Concept Inactivation Indicator {} file: {}", fileType, fileName);
					validateComponentFile(is, ComponentType.ATTRIBUTE_VALUE);
				} else if (fileName.contains("der2_cRefset_DescriptionInactivationIndicatorReferenceSet" )) {
					LOGGER.info("Validating Description Inactivation Indicator {} file: {}", fileType, fileName);
					validateComponentFile(is, ComponentType.ATTRIBUTE_VALUE);
				} else if (fileName.contains("der2_cRefset_AttributeValue" )) {
					LOGGER.info("Validating Concept/Description Inactivation Indicators {} file: {}", fileType, fileName);
					validateComponentFile(is, ComponentType.ATTRIBUTE_VALUE);
				} else if (fileName.contains("Association" ) || fileName.contains("AssociationReferenceSet" )) {
					LOGGER.info("Validating Historical Association File: " + fileName);
					validateComponentFile(is, ComponentType.HISTORICAL_ASSOCIATION);
				} else if (fileName.contains("Language")) {
					LOGGER.info("Validating " + fileType + " Language Reference Set File - " + fileName);
					validateComponentFile(is, ComponentType.LANGREFSET);
				}
			}
			flushFilesWithWait(false);
		} catch (TermServerScriptException | IOException  e) {
			throw new IllegalStateException("Unable to validate " + path + " due to " + e.getMessage(), e);
		}
	}
	
	public void validateComponentFile(InputStream is, ComponentType componentType) throws IOException, TermServerScriptException {
		//Not putting this in a try resource block otherwise it will close the stream on completion and we've got more to read!
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		String line;
		boolean isHeaderLine = true;
		int issueCount = 0;
		int reportTabIdx = componentType.ordinal();
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				if (inScope(lineItems[IDX_MODULEID])) {
					issueCount += validate(reportTabIdx, createComponent(componentType, lineItems));
					if (++componentsChecked % 100000 == 0) {
						LOGGER.debug("Checked " + componentsChecked + " / " + totalToCheck);
					}
				}
			} else {
				isHeaderLine = false;
			}
		}
		LOGGER.info (componentType + " reconciliation detected " + issueCount + " issues");
		flushFilesSoft();
	}
	
	private int validate(int reportTabIdx, Component c) throws TermServerScriptException {
		List<String> issues = new ArrayList<>();
		if (inScope(c)) {
			//What does our generated snapshot hold for this component?
			Component other = gl.getComponent(c.getId());
			if (other == null) {
				if (!c.isActiveSafely() &&
						(c.getComponentType().equals(ComponentType.STATED_RELATIONSHIP) ||
						c.getComponentType().equals(ComponentType.INFERRED_RELATIONSHIP))) {
					remainingComponents.remove(c.getId());
					//TODO Investigate here. I think we're replacing duplicate triples when importing
					//perhaps we can stop doing that.
					return NO_CHANGES_MADE;
				}
				Concept owner = gl.getComponentOwner(c.getId());	
				report(reportTabIdx, owner, c.getId(), c.isActive(), c.getComponentType() + " from export not present in generated snapshot", c);
				countIssue(null);
				return 1;
			}
			issues = c.fieldComparison(other);
			Concept owner = gl.getComponentOwner(c.getId());
			for (String issue : issues) {
				countIssue(owner);
				report(reportTabIdx, owner, c.getId(), c.isActive(), issue);
			}
		}
		remainingComponents.remove(c.getId());
		return issues.size();
	}
	
	
	private void reportRemainder() throws TermServerScriptException {
		for (Map.Entry<String, Component> entry : remainingComponents.entrySet()) {
			Component c = entry.getValue();
			if (c.getComponentType().equals(ComponentType.STATED_RELATIONSHIP)) {
				continue;
			}
			Concept owner = gl.getComponentOwner(c.getId());
			int reportTabIdx = entry.getValue().getComponentType().ordinal();
			report(reportTabIdx, owner, entry.getKey(), c.isActive()?"Y":"N", "Component from generated snapshot not present in export", c);
		}
	}

	public Component createComponent(ComponentType componentType, String[] lineItems) throws TermServerScriptException {
		//Work out the owning component given the known type of this component
		switch (componentType) {
			case CONCEPT :	Concept c = new Concept(lineItems[IDX_ID]);
							Concept.fillFromRf2(c, lineItems);
							return c;
			case DESCRIPTION :
			case TEXT_DEFINITION :	Description d = new Description(lineItems[IDX_ID]);
									Description.fillFromRf2(d,lineItems);
									return d;
			case INFERRED_RELATIONSHIP: return createRelationshipFromRF2(lineItems);
			case HISTORICAL_ASSOCIATION : return AssociationEntry.fromRf2(lineItems);
			case ATTRIBUTE_VALUE : return  InactivationIndicatorEntry.fromRf2(lineItems);
			case LANGREFSET : return LangRefsetEntry.fromRf2(lineItems);
			case AXIOM : return AxiomEntry.fromRf2(lineItems);
		default: throw new TermServerScriptException("Unknown component Type: " + componentType);
		}
	}
	
	private Relationship createRelationshipFromRF2(String[] lineItems) throws TermServerScriptException {
		String sourceId = lineItems[REL_IDX_SOURCEID];
		Concept source = gl.getConcept(sourceId);
		String destId = lineItems[REL_IDX_DESTINATIONID];
		String typeId = lineItems[REL_IDX_TYPEID];
		
		if (sourceId.length() < 4 || destId.length() < 4 || typeId.length() < 4 ) {
			LOGGER.warn("*** Invalid SCTID encountered in relationship {}: s{} d{} t{}", lineItems[REL_IDX_ID], sourceId, destId, typeId);
		}
		Concept type = gl.getConcept(lineItems[REL_IDX_TYPEID]);
		Concept destination = gl.getConcept(lineItems[REL_IDX_DESTINATIONID]);
		int groupNum = Integer.parseInt(lineItems[REL_IDX_RELATIONSHIPGROUP]);
		
		Relationship r = new Relationship(source, type, destination, groupNum);
		r.setRelationshipId(lineItems[REL_IDX_ID].isEmpty()?null:lineItems[REL_IDX_ID]);
		r.setCharacteristicType(SnomedUtils.translateCharacteristicType(lineItems[REL_IDX_CHARACTERISTICTYPEID]));
		r.setActive(lineItems[REL_IDX_ACTIVE].equals("1"));
		r.setEffectiveTime(lineItems[REL_IDX_EFFECTIVETIME].isEmpty()?null:lineItems[REL_IDX_EFFECTIVETIME]);
		r.setModifier(SnomedUtils.translateModifier(lineItems[REL_IDX_MODIFIERID]));
		r.setModuleId(lineItems[REL_IDX_MODULEID]);
		return r;
	}

	@Override
	protected boolean inScope (Component c) {
		return inScope(c.getModuleId());
	}
	
	protected boolean inScope (String moduleId) {
		for (String moduleOfInterest : modulesOfInterest) {
			if (moduleId.equals(moduleOfInterest)) {
				return true;
			}
		}
		return false;
	}
}

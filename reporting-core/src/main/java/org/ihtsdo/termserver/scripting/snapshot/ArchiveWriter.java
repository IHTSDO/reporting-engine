package org.ihtsdo.termserver.scripting.snapshot;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ComponentAnnotationEntry;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class ArchiveWriter implements Runnable, RF2Constants {
	private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveWriter.class);
	private TermServerScript ts;
	private String moduleId;

	protected String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
	protected String packageDir;
	protected String outputDirName;

	protected String languageCode = "en";
	protected String edition = "INT";
	protected boolean leaveArchiveUncompressed = true;

	protected String conSnapshotFilename;
	protected String relSnapshotFilename;
	protected String relConcreteFilename;
	protected String attribValSnapshotFilename;
	protected String assocSnapshotFilename;
	protected String owlSnapshotFilename;
	protected String sRelSnapshotFilename;
	protected String descSnapshotFilename;
	protected String langSnapshotFilename;
	protected String altIdSnapshotFilename;
	protected String annotSnapshotFilename;

	protected String[] conHeader = new String[] {COL_ID,COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,"definitionStatusId"};
	protected String[] descHeader = new String[] {COL_ID,COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,"conceptId","languageCode",COL_TYPE_ID,"term","caseSignificanceId"};
	protected String[] relHeader = new String[] {COL_ID,COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,"sourceId","destinationId","relationshipGroup",COL_TYPE_ID,"characteristicTypeId","modifierId"};
	protected String[] relConcreteHeader = new String[] {COL_ID,COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,"sourceId","value","relationshipGroup",COL_TYPE_ID,"characteristicTypeId","modifierId"};
	protected String[] langHeader = new String[] {COL_ID,COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,COL_REFSET_ID,COL_REFERENCED_COMPONENT_ID,"acceptabilityId"};
	protected String[] attribValHeader = new String[] {COL_ID,COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,COL_REFSET_ID,COL_REFERENCED_COMPONENT_ID,"valueId"};
	protected String[] assocHeader = new String[] {COL_ID,COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,COL_REFSET_ID,COL_REFERENCED_COMPONENT_ID,"targetComponentId"};
	protected String[] owlHeader = new String[] {COL_ID,COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,COL_REFSET_ID,COL_REFERENCED_COMPONENT_ID,"owlExpression"};
	protected String[] altIdHeader = new String[] {"alternateIdentifier",COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,"identifierSchemeId",COL_REFERENCED_COMPONENT_ID};
	protected String[] annotHeader = new String[] {COL_ID,COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,COL_REFSET_ID,COL_REFERENCED_COMPONENT_ID,"languageDialectCode",COL_TYPE_ID,"value"};

	private int conceptsWrittenToDisk = 0;

	ArchiveWriter(TermServerScript ts, File outputDirFile) {
		this.ts = ts;
		this.outputDirName = outputDirFile.getAbsolutePath();
	}

	public void init(String moduleId) throws TermServerScriptException {
		File outputDir = new File (outputDirName);
		this.moduleId = moduleId;
		int increment = 0;
		while (outputDir.exists()) {
			String proposedOutputDirName = outputDirName + "_" + (++increment) ;
			outputDir = new File(proposedOutputDirName);
		}

		packageDir = outputDir.getPath() + File.separator;
		LOGGER.info("Outputting data to {}", packageDir);
		initialiseFileHeaders();
	}

	private void initialiseFileHeaders() throws TermServerScriptException {
		String termDir = packageDir +"Snapshot/Terminology/";
		String refDir =  packageDir +"Snapshot/Refset/";
		conSnapshotFilename = termDir + "sct2_Concept_Snapshot_"+edition+"_" + today + ".txt";
		ts.writeToRF2File(conSnapshotFilename, conHeader);

		relSnapshotFilename = termDir + "sct2_Relationship_Snapshot_"+edition+"_" + today + ".txt";
		ts.writeToRF2File(relSnapshotFilename, relHeader);

		relConcreteFilename = termDir + "sct2_RelationshipConcreteValues_Snapshot_"+edition+"_" + today + ".txt";
		ts.writeToRF2File(relConcreteFilename, relConcreteHeader);

		sRelSnapshotFilename = termDir + "sct2_StatedRelationship_Snapshot_"+edition+"_" + today + ".txt";
		ts.writeToRF2File(sRelSnapshotFilename, relHeader);

		descSnapshotFilename = termDir + "sct2_Description_Snapshot-"+languageCode+"_"+edition+"_" + today + ".txt";
		ts.writeToRF2File(descSnapshotFilename, descHeader);

		langSnapshotFilename = refDir + "Language/der2_cRefset_LanguageSnapshot-"+languageCode+"_"+edition+"_" + today + ".txt";
		ts.writeToRF2File(langSnapshotFilename, langHeader);

		attribValSnapshotFilename = refDir + "Content/der2_cRefset_AttributeValueSnapshot_"+edition+"_" + today + ".txt";
		ts.writeToRF2File(attribValSnapshotFilename, attribValHeader);

		assocSnapshotFilename = refDir + "Content/der2_cRefset_AssociationSnapshot_"+edition+"_" + today + ".txt";
		ts.writeToRF2File(assocSnapshotFilename, assocHeader);

		owlSnapshotFilename = termDir + "sct2_sRefset_OWLExpressionSnapshot_"+edition+"_" + today + ".txt";
		ts.writeToRF2File(owlSnapshotFilename, owlHeader);

		altIdSnapshotFilename = termDir + "sct2_Identifier_Snapshot_"+edition+"_" + today + ".txt";
		ts.writeToRF2File(altIdSnapshotFilename, altIdHeader);

		annotSnapshotFilename = refDir + "Metadata/der2_sscsRefset_MemberAnnotationStringValueSnapshot_"+edition+"_" + today + ".txt";
		ts.writeToRF2File(annotSnapshotFilename, annotHeader);
		ts.getRF2Manager().flushFiles(false);
	}

	public void run() {
		LOGGER.debug("Writing RF2 Snapshot to disk{}", (leaveArchiveUncompressed ? "." : " and compressing."));
		conceptsWrittenToDisk = 0;
		try {
			//Tell our parent that a child is working so it doesn't try and
			//start processing something else.
			ts.asyncSnapshotCacheInProgress(true);
			outputRF2();
			ts.getRF2Manager().flushFiles(true);
			if (!leaveArchiveUncompressed) {
				SnomedUtils.createArchive(new File(outputDirName));
			}
			LOGGER.debug("Completed writing RF2 Snapshot ({} concepts) to disk", conceptsWrittenToDisk);
		} catch (Exception e) {
			LOGGER.error("Failed to write archive to disk", e);
		} finally {
			ts.asyncSnapshotCacheInProgress(false);
		}
	}


	private void outputRF2() throws TermServerScriptException {
		//Create new collection in case some other process looks at a new concept
		Set<Concept> allConcepts = new HashSet<>(ts.getGraphLoader().getAllConcepts());
		for (Concept thisConcept : allConcepts) {
			outputRF2(thisConcept);
			conceptsWrittenToDisk++;
		}

		//Alt Identifier file is independent of individual concepts
		for (Map.Entry<Concept, Map<String, String>> entry : ts.getGraphLoader().getAlternateIdentifierMap().entrySet()) {
			Concept schema = entry.getKey();
			for (Map.Entry<String, String> schemaEntry : entry.getValue().entrySet()) {
				ts.writeToRF2File(altIdSnapshotFilename, generateAltIdentiferRow(schema, schemaEntry.getKey(), schemaEntry.getValue()));
			}
		}
	}

	protected void outputRF2(Concept c) throws TermServerScriptException {
		ts.writeToRF2File(conSnapshotFilename, c.toRF2());

		for (Description d : c.getDescriptions(RF2Constants.ActiveState.BOTH)) {
			outputRF2(d);  //Will output langrefset, inactivation indicators and associations in turn
		}

		for (Relationship r : c.getRelationships(RF2Constants.CharacteristicType.STATED_RELATIONSHIP, RF2Constants.ActiveState.BOTH)) {
			outputRF2(r);
		}

		for (Relationship r : c.getRelationships(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP, RF2Constants.ActiveState.BOTH)) {
			outputRF2(r);
		}

		for (InactivationIndicatorEntry i : c.getInactivationIndicatorEntries()) {
			ts.writeToRF2File(attribValSnapshotFilename, i.toRF2());
		}

		for (AssociationEntry h : c.getAssociationEntries()) {
			ts.writeToRF2File(assocSnapshotFilename, h.toRF2());
		}

		for (AxiomEntry o : c.getAxiomEntries()) {
			ts.writeToRF2File(owlSnapshotFilename, o.toRF2());
		}

		for (ComponentAnnotationEntry a : c.getComponentAnnotationEntries()) {
			ts.writeToRF2File(annotSnapshotFilename, a.toRF2());
		}

	}

	private String[] generateAltIdentiferRow(Concept schema, String altId, String referencedComponentId) {
		return new String[]{
				altId,
				"",
				"1",
				moduleId,
				schema.getId(),
				referencedComponentId};
	}

	protected void outputRF2(Description d) throws TermServerScriptException {
		ts.writeToRF2File(descSnapshotFilename, d.toRF2());

		for (LangRefsetEntry lang : d.getLangRefsetEntries()) {
			ts.writeToRF2File(langSnapshotFilename, lang.toRF2());
		}

		for (InactivationIndicatorEntry inact : d.getInactivationIndicatorEntries()) {
			ts.writeToRF2File(attribValSnapshotFilename, inact.toRF2());
		}

		for (AssociationEntry assoc : d.getAssociationEntries()) {
			ts.writeToRF2File(assocSnapshotFilename, assoc.toRF2());
		}
	}

	protected void outputRF2(Relationship r) throws TermServerScriptException {
		//Relationships that hail from an axiom will not be persisted as relationships
		//We'll re-establish those on loading from the original axioms
		if (r.fromAxiom()) {
			return;
		}
		switch (r.getCharacteristicType()) {
			case STATED_RELATIONSHIP:
				ts.writeToRF2File(sRelSnapshotFilename, r.toRF2());
				break;
			case INFERRED_RELATIONSHIP:
			default:
				outputRF2InferredRel(r);
		}
	}

	private void outputRF2InferredRel(Relationship r) throws TermServerScriptException {
		if (r.isConcrete()) {
			ts.writeToRF2File(relConcreteFilename, r.toRF2());
		} else {
			ts.writeToRF2File(relSnapshotFilename, r.toRF2());
		}
	}
}

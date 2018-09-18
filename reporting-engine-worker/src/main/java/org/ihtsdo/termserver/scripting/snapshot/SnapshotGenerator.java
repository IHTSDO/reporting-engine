package org.ihtsdo.termserver.scripting.snapshot;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

import org.ihtsdo.termserver.scripting.ArchiveManager;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class SnapshotGenerator extends TermServerScript {
	
	protected String outputDirName = "output";
	protected String packageRoot;
	protected String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
	protected String packageDir;
	protected String conSnapshotFilename;
	protected String relSnapshotFilename;
	protected String attribValSnapshotFilename;
	protected String assocSnapshotFilename;
	protected String sRelSnapshotFilename;
	protected String descSnapshotFilename;
	protected String langSnapshotFilename;
	protected String edition = "INT";
	protected boolean leaveArchiveUncompressed = false;
	
	protected String languageCode = "en";
	protected boolean isExtension = false;
	protected boolean newIdsRequired = true;
	protected String moduleId="900000000000207008";
	protected String nameSpace="0";
	protected String[] langRefsetIds = new String[] { "900000000000508004",  //GB
														"900000000000509007" }; //US
	
	protected String[] conHeader = new String[] {"id","effectiveTime","active","moduleId","definitionStatusId"};
	protected String[] descHeader = new String[] {"id","effectiveTime","active","moduleId","conceptId","languageCode","typeId","term","caseSignificanceId"};
	protected String[] relHeader = new String[] {"id","effectiveTime","active","moduleId","sourceId","destinationId","relationshipGroup","typeId","characteristicTypeId","modifierId"};
	protected String[] langHeader = new String[] {"id","effectiveTime","active","moduleId","refsetId","referencedComponentId","acceptabilityId"};
	protected String[] attribValHeader = new String[] {"id","effectiveTime","active","moduleId","refsetId","referencedComponentId","valueId"};
	protected String[] assocHeader = new String[] {"id","effectiveTime","active","moduleId","refsetId","referencedComponentId","targetComponentId"};

	public SnapshotGenerator (ArchiveManager archiveManager) {
		if (archiveManager != null) {
			this.setArchiveManager(archiveManager);
		}
	}
	
	public static void main (String[] args) throws IOException, TermServerScriptException, SnowOwlClientException, InterruptedException {
		SnapshotGenerator snapGen = new SnapshotGenerator(null);
		try {
			snapGen.runStandAlone = true;
			snapGen.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			snapGen.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			snapGen.loadArchive(snapGen.inputFile, false, "Delta");
			snapGen.startTimer();
			snapGen.outputRF2();
			snapGen.flushFiles(false);
			if (!snapGen.leaveArchiveUncompressed) {	
				SnomedUtils.createArchive(new File(snapGen.outputDirName));
			}
		} finally {
			snapGen.finish();
		}
	}
	
	public File generateSnapshot (File previousReleaseSnapshot, File delta, File newLocation) throws TermServerScriptException, SnowOwlClientException {
		File archive = null;
		setQuiet(true);
		init(newLocation, false);
		loadArchive(previousReleaseSnapshot, false, "Snapshot");
		loadArchive(delta, false, "Delta");
		outputRF2();
		flushFiles(false);
		if (!leaveArchiveUncompressed) {	
			archive = SnomedUtils.createArchive(new File(outputDirName));
		}
		setQuiet(false);
		return archive;
	}
	
	protected void init (String[] args) throws IOException, TermServerScriptException, SnowOwlClientException, SnowOwlClientException {
		super.init(args);
		File newLocation = new File("SnomedCT_RF2Release_" + edition);
		init(newLocation, true);
	}
	
	protected void init (File newLocation, boolean addTodaysDate) throws TermServerScriptException {
		File outputDir = new File (outputDirName);
		int increment = 0;
		while (outputDir.exists()) {
			String proposedOutputDirName = outputDirName + "_" + (++increment) ;
			outputDir = new File(proposedOutputDirName);
		}
		
		if (leaveArchiveUncompressed) {
			packageDir = outputDir.getPath() + File.separator;
		} else {
			outputDirName = outputDir.getName();
			packageRoot = outputDirName + File.separator + newLocation;
			packageDir = packageRoot + (addTodaysDate?today:"") + File.separator;
		}
		info ("Outputting data to " + packageDir);
		initialiseFileHeaders();
	}
	
	protected void initialiseFileHeaders() throws TermServerScriptException {
		String termDir = packageDir +"Snapshot/Terminology/";
		String refDir =  packageDir +"Snapshot/Refset/";
		conSnapshotFilename = termDir + "sct2_Concept_Snapshot_"+edition+"_" + today + ".txt";
		writeToRF2File(conSnapshotFilename, conHeader);
		
		relSnapshotFilename = termDir + "sct2_Relationship_Snapshot_"+edition+"_" + today + ".txt";
		writeToRF2File(relSnapshotFilename, relHeader);

		sRelSnapshotFilename = termDir + "sct2_StatedRelationship_Snapshot_"+edition+"_" + today + ".txt";
		writeToRF2File(sRelSnapshotFilename, relHeader);
		
		descSnapshotFilename = termDir + "sct2_Description_Snapshot-"+languageCode+"_"+edition+"_" + today + ".txt";
		writeToRF2File(descSnapshotFilename, descHeader);
		
		langSnapshotFilename = refDir + "Language/der2_cRefset_LanguageSnapshot-"+languageCode+"_"+edition+"_" + today + ".txt";
		writeToRF2File(langSnapshotFilename, langHeader);
		
		attribValSnapshotFilename = refDir + "Content/der2_cRefset_AttributeValueSnapshot_"+edition+"_" + today + ".txt";
		writeToRF2File(attribValSnapshotFilename, attribValHeader);
		
		assocSnapshotFilename = refDir + "Content/der2_cRefset_AssociationSnapshot_"+edition+"_" + today + ".txt";
		writeToRF2File(assocSnapshotFilename, assocHeader);
	}
	
	private void outputRF2() throws TermServerScriptException {
		for (Concept thisConcept : gl.getAllConcepts()) {
			outputRF2(thisConcept);
		}
	}
	
	protected void outputRF2(Concept c) throws TermServerScriptException {
		writeToRF2File(conSnapshotFilename, c.toRF2());
		
		for (Description d : c.getDescriptions(ActiveState.BOTH)) {
			outputRF2(d);  //Will output langrefset and inactivation indicators in turn
		}
		
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.BOTH)) {
			outputRF2(r);
		}
		
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.BOTH)) {
			outputRF2(r);
		}
		
		for (InactivationIndicatorEntry i: c.getInactivationIndicatorEntries()) {
			writeToRF2File(attribValSnapshotFilename, i.toRF2());
		}
		
		for (HistoricalAssociation h: c.getHistorialAssociations()) {
			writeToRF2File(assocSnapshotFilename, h.toRF2());
		}
	}

	protected void outputRF2(Description d) throws TermServerScriptException {
		writeToRF2File(descSnapshotFilename, d.toRF2());
		
		for (LangRefsetEntry lang : d.getLangRefsetEntries()) {
			writeToRF2File(langSnapshotFilename, lang.toRF2());
		}
		
		for (InactivationIndicatorEntry inact : d.getInactivationIndicatorEntries()) {
			writeToRF2File(attribValSnapshotFilename, inact.toRF2());
		}
	}

	protected void outputRF2(Relationship r) throws TermServerScriptException {
		switch (r.getCharacteristicType()) {
			case STATED_RELATIONSHIP : writeToRF2File(sRelSnapshotFilename, r.toRF2());
			break;
			case INFERRED_RELATIONSHIP : 
			default: writeToRF2File(relSnapshotFilename, r.toRF2());
		}
	}
	
	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}

	public void leaveArchiveUncompressed() {
		leaveArchiveUncompressed = true;
	}

	public String getOutputDirName() {
		return outputDirName;
	}

	public void setOutputDirName(String outputDirName) {
		this.outputDirName = outputDirName;
	}

}

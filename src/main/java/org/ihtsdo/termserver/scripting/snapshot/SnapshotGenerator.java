package org.ihtsdo.termserver.scripting.snapshot;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.InactivationIndicatorEntry;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class SnapshotGenerator extends TermServerScript {
	
	protected String outputDirName = "output";
	protected String packageRoot;
	protected String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
	protected String packageDir;
	protected String conSnapshotFilename;
	protected String relSnapshotFilename;
	protected String attribValSnapshotFilename;
	protected String sRelSnapshotFilename;
	protected String descSnapshotFilename;
	protected String langSnapshotFilename;
	protected String edition = "INT";
	
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

	public static void main (String[] args) throws IOException, TermServerScriptException, SnowOwlClientException, InterruptedException {
		SnapshotGenerator snapGen = new SnapshotGenerator();
		try {
			snapGen.runStandAlone = true;
			snapGen.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			snapGen.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			snapGen.loadArchive(snapGen.inputFile, false, "Delta");
			snapGen.startTimer();
			snapGen.outputRF2();
			snapGen.flushFiles(false);
			SnomedUtils.createArchive(new File(snapGen.outputDirName));
		} finally {
			snapGen.finish();
		}
	}
	
	protected void init (String[] args) throws IOException, TermServerScriptException, SnowOwlClientException, SnowOwlClientException {
		super.init(args);
		File outputDir = new File (outputDirName);
		int increment = 0;
		while (outputDir.exists()) {
			String proposedOutputDirName = outputDirName + "_" + (++increment) ;
			outputDir = new File(proposedOutputDirName);
		}
		outputDirName = outputDir.getName();
		packageRoot = outputDirName + File.separator + "SnomedCT_RF2Release_" + edition +"_";
		packageDir = packageRoot + today + File.separator;
		println ("Outputting data to " + packageDir);
		initialiseFileHeaders();
	}
	
	public void finish() throws FileNotFoundException {
		super.finish();
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
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}

}

package org.ihtsdo.termserver.scripting.snapshot;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapshotGenerator extends TermServerScript {

	private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotGenerator.class);

	protected String outputDirName = "output";
	protected String packageRoot;
	protected String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
	protected String packageDir;
	protected String conSnapshotFilename;
	protected String relSnapshotFilename;
	protected String relConcreteFilename;
	protected String attribValSnapshotFilename;
	protected String assocSnapshotFilename;
	protected String owlSnapshotFilename;
	protected String sRelSnapshotFilename;
	protected String descSnapshotFilename;
	protected String langSnapshotFilename;
	protected String edition = "INT";
	protected boolean leaveArchiveUncompressed = false;
	
	protected static boolean runAsynchronously = true;
	protected static boolean skipSave = false;
	
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
	protected String[] relConcreteHeader = new String[] {"id","effectiveTime","active","moduleId","sourceId","value","relationshipGroup","typeId","characteristicTypeId","modifierId"};
	protected String[] langHeader = new String[] {"id","effectiveTime","active","moduleId","refsetId","referencedComponentId","acceptabilityId"};
	protected String[] attribValHeader = new String[] {"id","effectiveTime","active","moduleId","refsetId","referencedComponentId","valueId"};
	protected String[] assocHeader = new String[] {"id","effectiveTime","active","moduleId","refsetId","referencedComponentId","targetComponentId"};
	protected String[] owlHeader = new String[] {"id","effectiveTime","active","moduleId","refsetId","referencedComponentId","owlExpression"};
	
	
	public static void main (String[] args) throws IOException, TermServerScriptException, InterruptedException {
		SnapshotGenerator snapGen = new SnapshotGenerator();
		try {
			snapGen.runStandAlone = true;
			snapGen.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			snapGen.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			snapGen.loadArchive(snapGen.getInputFile(), false, "Delta", false);
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
	
	public void generateSnapshot (TermServerScript ts, File dependencySnapshot, File previousSnapshot, File delta, File newLocation) throws TermServerScriptException {
		setQuiet(true);
		init(newLocation, false);
		if (dependencySnapshot != null) {
			LOGGER.info("Loading dependency snapshot " + dependencySnapshot);
			loadArchive(dependencySnapshot, false, "Snapshot", true);
		}
		
		LOGGER.info("Loading previous snapshot " + previousSnapshot);
		loadArchive(previousSnapshot, false, "Snapshot", true);
		
		LOGGER.info("Loading delta " + delta);
		loadArchive(delta, false, "Delta", false);
		setQuiet(false);
	}

	public void writeSnapshotToCache(TermServerScript ts) {
		//Writing to disk can be done asynchronously and complete at any time.  We have the in-memory copy to work with.
		//The disk copy will save time when we run again for the same project

		//Ah, well that's not completely true because sometimes we want to be really careful we've not modified the data
		//in some process.
		if (!skipSave) {
			if (runAsynchronously) {
				new Thread(new ArchiveWriter(ts)).start();
			} else {
				new ArchiveWriter(ts).run();
			}
		}
	}
	
	protected void init (String[] args) throws TermServerScriptException {
		super.init(args);
		File newLocation = new File("SnomedCT_RF2Release_" + edition);
		init(newLocation, true);
	}
	
	protected void init (File newLocation, boolean addTodaysDate) throws TermServerScriptException {
		//Make sure the Graph Loader is clean
		LOGGER.info("Snapshot Generator ensuring Graph Loader is clean");
		gl.reset();
		if (!skipSave) {
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
			LOGGER.info("Outputting data to " + packageDir);
			initialiseFileHeaders();
		}
	}
	
	protected void initialiseFileHeaders() throws TermServerScriptException {
		String termDir = packageDir +"Snapshot/Terminology/";
		String refDir =  packageDir +"Snapshot/Refset/";
		conSnapshotFilename = termDir + "sct2_Concept_Snapshot_"+edition+"_" + today + ".txt";
		writeToRF2File(conSnapshotFilename, conHeader);
		
		relSnapshotFilename = termDir + "sct2_Relationship_Snapshot_"+edition+"_" + today + ".txt";
		writeToRF2File(relSnapshotFilename, relHeader);
		
		relConcreteFilename = termDir + "sct2_RelationshipConcreteValues_Snapshot_"+edition+"_" + today + ".txt";
		writeToRF2File(relConcreteFilename, relConcreteHeader);

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
		
		owlSnapshotFilename = termDir + "sct2_sRefset_OWLExpressionSnapshot_"+edition+"_" + today + ".txt";
		writeToRF2File(owlSnapshotFilename, owlHeader);
		
		getRF2Manager().flushFiles(false);
	}
	
	private void outputRF2() throws TermServerScriptException {
		//Create new collection in case some other process looks at a new concept
		Set<Concept> allConcepts = new HashSet<>(gl.getAllConcepts());
		for (Concept thisConcept : allConcepts) {
			outputRF2(thisConcept);
		}
	}
	
	protected void outputRF2(Concept c) throws TermServerScriptException {
		writeToRF2File(conSnapshotFilename, c.toRF2());
		
		for (Description d : c.getDescriptions(ActiveState.BOTH)) {
			outputRF2(d);  //Will output langrefset, inactivation indicators and associations in turn
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
		
		for (AssociationEntry h: c.getAssociationEntries()) {
			writeToRF2File(assocSnapshotFilename, h.toRF2());
		}
		
		for (AxiomEntry o: c.getAxiomEntries()) {
			writeToRF2File(owlSnapshotFilename, o.toRF2());
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
		
		for (AssociationEntry assoc : d.getAssociationEntries()) {
			writeToRF2File(assocSnapshotFilename, assoc.toRF2());
		}
	}

	protected void outputRF2(Relationship r) throws TermServerScriptException {
		//Relationships that hail from an axiom will not be persisted as relationships
		//We'll re-establish those on loading from the original axioms
		if (r.fromAxiom()) {
			return;
		}
		switch (r.getCharacteristicType()) {
			case STATED_RELATIONSHIP : writeToRF2File(sRelSnapshotFilename, r.toRF2());
			break;
			case INFERRED_RELATIONSHIP : 
			default: outputRF2InferredRel(r);
		}
	}
	
	private void outputRF2InferredRel(Relationship r) throws TermServerScriptException {
		if (r.isConcrete()) {
			writeToRF2File(relConcreteFilename, r.toRF2());
		} else {
			writeToRF2File(relSnapshotFilename, r.toRF2());
		}
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

	public void setProject(Project project) {
		this.project = project;
	}
	
	public class ArchiveWriter implements Runnable {
		TermServerScript ts;
		
		ArchiveWriter (TermServerScript ts) {
			this.ts = ts;
		}
		public void run() {
			LOGGER.debug("Writing RF2 Snapshot to disk" + (leaveArchiveUncompressed?".":" and compressing."));
			try {
				//Tell our parent that a child is working so it doesn't try and
				//start processing something else.
				ts.asyncSnapshotCacheInProgress(true);
				outputRF2();
				getRF2Manager().flushFiles(true);
				if (!leaveArchiveUncompressed) {	
					SnomedUtils.createArchive(new File(outputDirName));
				}
				LOGGER.debug("Completed writing RF2 Snapshot to disk");
			} catch (Exception e) {
				LOGGER.error ("Failed to write archive to disk",e);
			} finally {
				ts.asyncSnapshotCacheInProgress(false);
			}
		}
	}

	public static void setRunAsynchronously(boolean runAsynchronously) {
		SnapshotGenerator.runAsynchronously = runAsynchronously;
	}
	
	public static void setSkipSave(boolean skipSave) {
		SnapshotGenerator.skipSave = skipSave;
	}

}

package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.AxiomUtils;
import org.ihtsdo.termserver.scripting.IdGenerator;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.snapshot.SnapshotGenerator;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.JobRun;

public abstract class DeltaGenerator extends TermServerScript {
	
	protected String outputDirName = "output";
	protected String packageRoot;
	protected String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
	protected String packageDir;
	protected String conDeltaFilename;
	protected String relDeltaFilename;
	protected String attribValDeltaFilename;
	protected String assocDeltaFilename;
	protected String owlDeltaFilename;
	protected String sRelDeltaFilename;
	protected String descDeltaFilename;
	protected String textDfnDeltaFilename;
	protected String langDeltaFilename;
	protected String altIdDeltaFilename;
	protected String edition = "INT";
	
	protected String eclSubset = null;
	
	protected String languageCode = "en";
	protected boolean isExtension = false;
	protected boolean newIdsRequired = true;
	protected String moduleId="900000000000207008";
	protected String nameSpace="0";
	protected String[] targetLangRefsetIds = new String[] { "900000000000508004",   //GB
															"900000000000509007" }; //US

	protected String[] conHeader = new String[] {"id","effectiveTime","active","moduleId","definitionStatusId"};
	protected String[] descHeader = new String[] {"id","effectiveTime","active","moduleId","conceptId","languageCode","typeId","term","caseSignificanceId"};
	protected String[] relHeader = new String[] {"id","effectiveTime","active","moduleId","sourceId","destinationId","relationshipGroup","typeId","characteristicTypeId","modifierId"};
	protected String[] langHeader = new String[] {"id","effectiveTime","active","moduleId","refsetId","referencedComponentId","acceptabilityId"};
	protected String[] attribValHeader = new String[] {"id","effectiveTime","active","moduleId","refsetId","referencedComponentId","valueId"};
	protected String[] assocHeader = new String[] {"id","effectiveTime","active","moduleId","refsetId","referencedComponentId","targetComponentId"};
	protected String[] owlHeader = new String[] {"id","effectiveTime","active","moduleId","refsetId","referencedComponentId","owlExpression"};
	protected String[] altIdHeader = new String[] {"alternateIdentifier","effectiveTime","active","moduleId","identifierSchemeId","referencedComponentId"};
	
	protected IdGenerator conIdGenerator;
	protected IdGenerator descIdGenerator;
	protected IdGenerator relIdGenerator;
	
	protected Map<ComponentType, String> fileMap = new HashMap<ComponentType, String>();
	
	protected boolean batchDelimitersDetected = false;
	
	protected void init (String[] args) throws TermServerScriptException {
		//We definitely need to finish saving a snapshot to disk before we start making changes
		//Otherwise if we run multiple times, we'll pick up changes from a previous run.
		SnapshotGenerator.setRunAsynchronously(false);
		for (int x=0; x<args.length; x++) {
			if (args[x].equals("-m")) {
				moduleId = args[++x];
			}
			if (args[x].equals("-iC")) {
				conIdGenerator = initialiseIdGenerator(args[++x], PartitionIdentifier.CONCEPT);
			}
			if (args[x].equals("-iD")) {
				descIdGenerator = initialiseIdGenerator(args[++x], PartitionIdentifier.DESCRIPTION);
			}
			if (args[x].equals("-iR")) {
				relIdGenerator = initialiseIdGenerator(args[++x], PartitionIdentifier.RELATIONSHIP);
			}
			if (args[x].equals("-e")) {
				eclSubset = args[++x];
			}
		}
		
		super.init(args);
		if (!dryRun) {
			initialiseOutputDirectory();
		} else {
			info("Dry run, no output expected");
		}
	}
	
	protected IdGenerator initialiseIdGenerator(File file, PartitionIdentifier partition) throws TermServerScriptException {
		String filePath = file == null ? "dummy" : file.getAbsolutePath();
		return initialiseIdGenerator(filePath, partition);
	}
	
	protected IdGenerator initialiseIdGenerator(String filePath, PartitionIdentifier partition) throws TermServerScriptException {
		IdGenerator idGenerator = IdGenerator.initiateIdGenerator(filePath,partition);
		idGenerator.setNamespace(nameSpace);
		idGenerator.isExtension(isExtension);
		return idGenerator;
	}

	protected void initialiseOutputDirectory() {
		//Don't add to previously exported data
		File outputDir = new File (outputDirName);
		int increment = 0;
		while (outputDir.exists()) {
			String proposedOutputDirName = outputDirName + "_" + (++increment) ;
			outputDir = new File(proposedOutputDirName);
		}
		outputDirName = outputDir.getName();
		packageRoot = outputDirName + File.separator + "SnomedCT_RF2Release_" + edition +"_";
		packageDir = packageRoot + today + File.separator;
		info ("Outputting data to " + packageDir);
	}

	protected void checkSettingsWithUser(JobRun jobRun) throws TermServerScriptException {
		super.checkSettingsWithUser(jobRun);
		
		print ("Targetting which namespace? [" + nameSpace + "]: ");
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			nameSpace = response;
		}
		
		print ("Considering which moduleId(s)? [" + moduleId + "]: ");
		response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			moduleId = response;
		}
		
		print ("Targetting which language code? [" + languageCode + "]: ");
		response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			languageCode = response;
		}
		
		String langRefsetIdStr = StringUtils.join(targetLangRefsetIds, ",");  
		print ("Targetting which language refset(s)? [" + langRefsetIdStr + "]: ");
		response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			targetLangRefsetIds = response.split(COMMA);
		}
		
		print ("What's the Edition? [" + edition + "]: ");
		response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			edition = response;
		}
		
		if (moduleId.isEmpty() || targetLangRefsetIds  == null) {
			String msg = "Require both moduleId and langRefset Id to be specified (-m -l parameters)";
			throw new TermServerScriptException(msg);
		}
		
		if (newIdsRequired && descIdGenerator == null && relIdGenerator == null && conIdGenerator == null) {
			throw new TermServerScriptException("Command line arguments must supply a list of available sctid using the -iC/D/R option, or specify newIdsRequired=false");
		}
		
		boolean dependencySpecified = (getDependencyArchive() != null);
		
		if (projectName != null && projectName.endsWith(".zip")) {
			String choice = dependencySpecified? "Y":"N";
			if (!dependencySpecified) {
				info ("Is " + project + " an extension that requires a dependant edition to be loaded first?");
				print ("Choice Y/N: ");
				choice = STDIN.nextLine().trim();
			}
			if (choice.toUpperCase().equals("Y")) {
				print ("Please enter the name of a dependent release archive (in releases or S3) [" + getDependencyArchive() + "]: ");
				response = STDIN.nextLine().trim();
				if (!response.isEmpty()) {
					setDependencyArchive(response);
				}
			}
			getArchiveManager(true).setLoadDependencyPlusExtensionArchive(getDependencyArchive() != null);
		}
	}
	
	public void postInit(String[] tabNames, String[] columnHeadings, boolean csvOutput) throws TermServerScriptException {
		super.postInit(tabNames, columnHeadings, false);
		initialiseFileHeaders();
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[]{
			"SCTID, FSN, SemTag, Severity, Action, Details, Details, , "
		};
		
		String[] tabNames = new String[]{
			"Delta Records Created"
		};
		postInit(tabNames, columnHeadings, false);
	}
	
	public void finish() {
		try {
			super.finish();
		} catch (Exception e) {
			error("Failed to flush files.", e);
		}
		closeIdGenerators();
	}
	
	protected void closeIdGenerators() {
		try {
			if (conIdGenerator != null) {
				info(conIdGenerator.finish());
			}
			if (descIdGenerator != null) {
				info(descIdGenerator.finish());
			}
			if (relIdGenerator != null) {
				info(relIdGenerator.finish());
			}
		} catch (FileNotFoundException e) {
			error ("Failed to close id generators",e);
		}
	}

	protected void initialiseFileHeaders() throws TermServerScriptException {
		String termDir = packageDir +"Delta/Terminology/";
		String refDir =  packageDir +"Delta/Refset/";
		conDeltaFilename = termDir + "sct2_Concept_Delta_"+edition+"_" + today + ".txt";
		fileMap.put(ComponentType.CONCEPT, conDeltaFilename);
		writeToRF2File(conDeltaFilename, conHeader);
		
		relDeltaFilename = termDir + "sct2_Relationship_Delta_"+edition+"_" + today + ".txt";
		fileMap.put(ComponentType.INFERRED_RELATIONSHIP, relDeltaFilename);
		writeToRF2File(relDeltaFilename, relHeader);

		sRelDeltaFilename = termDir + "sct2_StatedRelationship_Delta_"+edition+"_" + today + ".txt";
		fileMap.put(ComponentType.STATED_RELATIONSHIP, sRelDeltaFilename);
		writeToRF2File(sRelDeltaFilename, relHeader);
		
		descDeltaFilename = termDir + "sct2_Description_Delta-"+languageCode+"_"+edition+"_" + today + ".txt";
		fileMap.put(ComponentType.DESCRIPTION, descDeltaFilename);
		writeToRF2File(descDeltaFilename, descHeader);
		
		textDfnDeltaFilename = termDir + "sct2_TextDefinition_Delta-"+languageCode+"_"+edition+"_" + today + ".txt";
		fileMap.put(ComponentType.TEXT_DEFINITION, textDfnDeltaFilename);
		writeToRF2File(textDfnDeltaFilename, descHeader);
		
		owlDeltaFilename = termDir + "sct2_sRefset_OWLExpressionDelta_"+edition+"_" + today + ".txt";
		fileMap.put(ComponentType.AXIOM, owlDeltaFilename);
		writeToRF2File(owlDeltaFilename, owlHeader);
		
		altIdDeltaFilename = termDir + "sct2_Identifier_Delta_"+edition+"_" + today + ".txt";
		fileMap.put(ComponentType.ALTERNATE_IDENTIFIER, altIdDeltaFilename);
		writeToRF2File(altIdDeltaFilename, altIdHeader);
		
		langDeltaFilename = refDir + "Language/der2_cRefset_LanguageDelta-"+languageCode+"_"+edition+"_" + today + ".txt";
		fileMap.put(ComponentType.LANGREFSET, langDeltaFilename);
		writeToRF2File(langDeltaFilename, langHeader);
		
		attribValDeltaFilename = refDir + "Content/der2_cRefset_AttributeValueDelta_"+edition+"_" + today + ".txt";
		fileMap.put(ComponentType.ATTRIBUTE_VALUE, attribValDeltaFilename);
		writeToRF2File(attribValDeltaFilename, attribValHeader);
		
		assocDeltaFilename = refDir + "Content/der2_cRefset_AssociationDelta_"+edition+"_" + today + ".txt";
		fileMap.put(ComponentType.HISTORICAL_ASSOCIATION, assocDeltaFilename);
		writeToRF2File(assocDeltaFilename, assocHeader);
	}
	
	protected void outputModifiedComponents(boolean alwaysCheckSubComponents) throws TermServerScriptException {
		info ("Outputting to RF2 in " + outputDirName + "...");
		for (Concept thisConcept : gl.getAllConcepts()) {
			try {
				outputRF2((Concept)thisConcept, alwaysCheckSubComponents);
			} catch (TermServerScriptException e) {
				report ((Concept)thisConcept, null, Severity.CRITICAL, ReportActionType.API_ERROR, "Exception while processing: " + e.getMessage() + " : " + SnomedUtils.getStackTrace(e));
			}
		}
	}
	
	protected void outputRF2(ComponentType componentType, String[] columns) throws TermServerScriptException {
		String fileName = fileMap.get(componentType);
		writeToRF2File(fileName, columns);
	}

	protected void outputRF2(Description d) throws TermServerScriptException {
		if (d.isDirty()) {
			writeToRF2File(descDeltaFilename, d.toRF2());
		}
		for (LangRefsetEntry lang : d.getLangRefsetEntries()) {
			if (lang.isDirty()) {
				writeToRF2File(langDeltaFilename, lang.toRF2());
			}
		}
		for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries()) {
			if (i.isDirty()) {
				writeToRF2File(attribValDeltaFilename, i.toRF2());
			}
		}
		for (AssociationEntry a : d.getAssociationEntries()) {
			if (a.isDirty()) {
				writeToRF2File(assocDeltaFilename, a.toRF2());
			}
		}
	}
	
	protected void outputRF2(Relationship r) throws TermServerScriptException {
		if (r.isDirty()) {
			switch (r.getCharacteristicType()) {
				case STATED_RELATIONSHIP : writeToRF2File(sRelDeltaFilename, r.toRF2());
				break;
				case INFERRED_RELATIONSHIP : 
				default: writeToRF2File(relDeltaFilename, r.toRF2());
			}
		}
	}
	
	protected void outputRF2(InactivationIndicatorEntry i) throws TermServerScriptException {
		if (i.isDirty()) {
			writeToRF2File(attribValDeltaFilename, i.toRF2());
		}
	}
	
	protected void outputRF2(AssociationEntry h) throws TermServerScriptException {
		if (h.isDirty()) {
			writeToRF2File(assocDeltaFilename, h.toRF2());
		}
	}
	
	protected void outputRF2(AxiomEntry a) throws TermServerScriptException {
		if (a.isDirty()) {
			writeToRF2File(owlDeltaFilename, a.toRF2());
		}
	}
	
	protected void outputRF2(Concept c, boolean checkAllComponents) throws TermServerScriptException {
		if (c.isDirty()) {
			writeToRF2File(conDeltaFilename, c.toRF2());
		} else if (!checkAllComponents) {
			return;
		}
		
		for (Description d : c.getDescriptions(ActiveState.BOTH)) {
			outputRF2(d);  //Will output langrefset, inactivation indicators and associations in turn
		}
		
		for (AlternateIdentifier a : c.getAlternateIdentifiers()) {
			if (a.isDirty()) {
				writeToRF2File(altIdDeltaFilename, a.toRF2());
			}
		}
		
		//Do we have Stated Relationships that need to be converted to axioms?
		//We'll try merging those with any existing axioms.
		if (hasDirtyNotFromAxiomRelationships(c)) {
			convertStatedRelationshipsToAxioms(c, true);
			for (AxiomEntry a : AxiomUtils.convertClassAxiomsToAxiomEntries(c)) {
				//If we're moving relationships into a new module but not the concept, we 
				//might have a wrong module id here
				a.setModuleId(moduleId);
				a.setDirty();
				c.getAxiomEntries().add(a);
			}
		} else {
			for (Relationship r : c.getRelationships()) {
				//Don't output relationships that are part of an axiom
				if (!r.fromAxiom()) {
					outputRF2(r);
				}
			}
		}
		
		for (InactivationIndicatorEntry i: c.getInactivationIndicatorEntries()) {
			outputRF2(i);
		}
		
		for (AssociationEntry h: c.getAssociationEntries()) {
			outputRF2(h);
		}
		
		for (AxiomEntry a: c.getAxiomEntries()) {
			outputRF2(a);
		}
	}

	
	private boolean hasDirtyNotFromAxiomRelationships(Concept c) {
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.isActive() && !r.fromAxiom() && r.isDirty()) {
				return true;
			}
		}
		return false;
	}

	protected void outputRF2(Concept c) throws TermServerScriptException {
		//By default, check for modified descriptions and relationships 
		//even if the concept has not been modified.
		outputRF2(c, true);
	}
	

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		if (lineItems[0].contentEquals(BatchEndMarker.NEW_TASK)) {
			batchDelimitersDetected = true;
			return Collections.singletonList(new BatchEndMarker());
		}
		
		//Default implementation is to take the first column and try that as an SCTID
		//Override for more complex implementation
		return Collections.singletonList(gl.getConcept(lineItems[0]));
	}

}

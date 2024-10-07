package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.rest.client.authoringservices.AuthoringServicesClient;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ComponentAnnotationEntry;
import org.ihtsdo.termserver.scripting.AxiomUtils;
import org.ihtsdo.termserver.scripting.IdGenerator;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.snapshot.SnapshotGenerator;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.JobRun;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DeltaGenerator extends TermServerScript {

	private static final Logger LOGGER = LoggerFactory.getLogger(DeltaGenerator.class);

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
	protected String compAnnotDeltaFilename;
	protected String simpleMapDeltaFilename;
	protected String edition = "INT";
	
	protected String eclSubset = null;
	
	protected String languageCode = "en";
	protected boolean isExtension = false;
	protected boolean newIdsRequired = true;
	protected Set<String> sourceModuleIds = new HashSet<>();
	protected String nameSpace="0";
	protected String targetModuleId = SCTID_CORE_MODULE;
	protected String[] targetLangRefsetIds = new String[] { "900000000000508004",   //GB
															"900000000000509007" }; //US

	protected String[] conHeader = new String[] {COL_ID,COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,"definitionStatusId"};
	protected String[] descHeader = new String[] {COL_ID,COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,"conceptId","languageCode",COL_TYPE_ID,"term","caseSignificanceId"};
	protected String[] relHeader = new String[] {COL_ID,COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,"sourceId","destinationId","relationshipGroup",COL_TYPE_ID,"characteristicTypeId","modifierId"};
	protected String[] langHeader = new String[] {COL_ID,COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,COL_REFSET_ID,COL_REFERENCED_COMPONENT_ID,"acceptabilityId"};
	protected String[] attribValHeader = new String[] {COL_ID,COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,COL_REFSET_ID,COL_REFERENCED_COMPONENT_ID,"valueId"};
	protected String[] assocHeader = new String[] {COL_ID,COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,COL_REFSET_ID,COL_REFERENCED_COMPONENT_ID,"targetComponentId"};
	protected String[] owlHeader = new String[] {COL_ID,COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,COL_REFSET_ID,COL_REFERENCED_COMPONENT_ID,"owlExpression"};
	protected String[] altIdHeader = new String[] {"alternateIdentifier",COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,"identifierSchemeId",COL_REFERENCED_COMPONENT_ID};
	protected String[] compAnnotHeader = new String[] {COL_ID,COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,COL_REFSET_ID,COL_REFERENCED_COMPONENT_ID,"languageDialectCode",COL_TYPE_ID,"value"};
	protected String[] simpleMapHeader = new String[] {COL_ID,COL_EFFECTIVE_TIME,COL_ACTIVE,COL_MODULE_ID,COL_REFSET_ID,COL_REFERENCED_COMPONENT_ID,"mapTarget"};

	protected IdGenerator conIdGenerator;
	protected IdGenerator descIdGenerator;
	protected IdGenerator relIdGenerator;
	
	protected Map<ComponentType, String> fileMap = new HashMap<>();
	
	protected boolean batchDelimitersDetected = false;
	protected int archivesCreated = 0;

	@Override
	protected void init (String[] args) throws TermServerScriptException {
		//We definitely need to finish saving a snapshot to disk before we start making changes
		//Otherwise if we run multiple times, we'll pick up changes from a previous run.
		SnapshotGenerator.setRunAsynchronously(false);
		initialiseGenerators(args);
		
		super.init(args);
		if (!dryRun) {
			initialiseOutputDirectory();
		} else {
			LOGGER.info("Dry run, no output expected");
		}
	}
	
	public void initialiseGenerators(String[] args) throws TermServerScriptException {
		for (int x=0; x<args.length; x++) {
			if (args[x].equals("-nS")) {
				nameSpace = args[++x];
			}
			if (args[x].equals("-m")) {
				sourceModuleIds = Set.of(args[++x].split(","));
				targetModuleId = sourceModuleIds.iterator().next();
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
	}
	
	protected IdGenerator initialiseIdGenerator(File file, PartitionIdentifier partition, String namespace) throws TermServerScriptException {
		IdGenerator idGenerator = initialiseIdGenerator(file, partition);
		idGenerator.setNamespace(namespace);
		return idGenerator;
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
		LOGGER.info("Outputting data to {}", packageDir);
	}

	@Override
	protected void checkSettingsWithUser(JobRun jobRun) throws TermServerScriptException {
		super.checkSettingsWithUser(jobRun);

		determineMostLikelySourceModuleFromProject();
		if (sourceModuleIds.isEmpty()) {
			sourceModuleIds = Set.of(SCTID_CORE_MODULE, SCTID_MODEL_MODULE);
		}

		checkSourceAndTargetModulesWithUser();
		checkDependencySettingsWithUser();
		if (newIdsRequired && descIdGenerator == null && relIdGenerator == null && conIdGenerator == null) {
			throw new TermServerScriptException("Command line arguments must supply a list of available sctid using the -iC/D/R option, or specify newIdsRequired=false");
		}
	}

	private void determineMostLikelySourceModuleFromProject() {
		//If we're working in an extension and the source modules haven't been set, suggest
		//the default module for that project
		if (!projectName.endsWith(".zip") && sourceModuleIds.isEmpty()){
			try{
				scaClient = new AuthoringServicesClient(url, authenticatedCookie);
				//MAIN is not a project, but we know what the default module is
				if (projectName.equalsIgnoreCase("MAIN")) {
					sourceModuleIds = Set.of(INTERNATIONAL_MODULES);
				} else {
					project = scaClient.getProject(projectName);
					if (project.getBranchPath().contains("SNOMEDCT-")
							&& project.getMetadata() != null) {
						sourceModuleIds.add(project.getMetadata().getDefaultModuleId());
						if (project.getMetadata().getExpectedExtensionModules() != null) {
							sourceModuleIds.addAll(project.getMetadata().getExpectedExtensionModules());
						}
					}
				}
			} catch (Exception e) {
				LOGGER.error("Failed to retrieve project metadata for " + projectName, e);
			}
		}
	}

	private void checkSourceAndTargetModulesWithUser() throws TermServerScriptException {
		print ("Targetting which namespace? [" + nameSpace + "]: ");
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			nameSpace = response;
		}

		print ("Considering which source moduleId(s)? [" + StringUtils.join(sourceModuleIds, ",") + "]: ");
		response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			sourceModuleIds = Set.of(response.split(COMMA));
		}

		//Are we targeting a namespace that indicates we're doing an extract without shifting the source module?
		String firstSourceModule = sourceModuleIds.iterator().next();
		if (nameSpace.length() > 4 && firstSourceModule.contains(nameSpace)) {
			println("Target namespace indicates that we're keeping the source module the same.");
			targetModuleId = firstSourceModule;
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

		if (sourceModuleIds.isEmpty() || targetLangRefsetIds  == null) {
			String msg = "Require both moduleId and langRefset Id to be specified (-m -l parameters)";
			throw new TermServerScriptException(msg);
		}
	}

	private void checkDependencySettingsWithUser() {
		boolean dependencySpecified = (getDependencyArchive() != null);
		if (projectName != null && projectName.endsWith(".zip")) {
			String choice = dependencySpecified? "Y":"N";
			if (!dependencySpecified) {
				println("Is " + projectName + " an extension that requires a dependant edition to be loaded first?");
				print("Choice Y/N: ");
				choice = STDIN.nextLine().trim();
			}

			if (choice.equalsIgnoreCase("Y")) {
				print("Please enter the name of a dependent release archive (in releases or S3) [" + getDependencyArchive() + "]: ");
				String response = STDIN.nextLine().trim();
				if (!response.isEmpty()) {
					setDependencyArchive(response);
				}
			}
			getArchiveManager().setLoadDependencyPlusExtensionArchive(getDependencyArchive() != null);
		}
	}

	public void postInit(String[] tabNames, String[] columnHeadings) throws TermServerScriptException {
		super.postInit(tabNames, columnHeadings, false);
		if (!dryRun) {
			initialiseFileHeaders();
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[]{
			"SCTID, FSN, SemTag, Severity, Action, Details," + additionalReportColumns
		};
		
		String[] tabNames = new String[]{
			"Delta Records Created"
		};
		postInit(tabNames, columnHeadings);
	}

	@Override
	public void finish() {
		try {
			super.finish();
		} catch (Exception e) {
			LOGGER.error("Failed to flush files.", e);
		}
		closeIdGenerators();
	}
	
	protected void closeIdGenerators() {
		try {
			if (conIdGenerator != null) {
				conIdGenerator.finish();
			}
			if (descIdGenerator != null) {
				descIdGenerator.finish();
			}
			if (relIdGenerator != null) {
				relIdGenerator.finish();
			}
		} catch (FileNotFoundException e) {
			LOGGER.error("Failed to close id generators", e);
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

		simpleMapDeltaFilename = refDir + "Content/der2_sRefset_SimpleMapDelta_"+edition+"_" + today + ".txt";
		fileMap.put(ComponentType.SIMPLE_MAP, simpleMapDeltaFilename);
		writeToRF2File(simpleMapDeltaFilename, simpleMapHeader);
		
		attribValDeltaFilename = refDir + "Content/der2_cRefset_AttributeValueDelta_"+edition+"_" + today + ".txt";
		fileMap.put(ComponentType.ATTRIBUTE_VALUE, attribValDeltaFilename);
		writeToRF2File(attribValDeltaFilename, attribValHeader);
		
		assocDeltaFilename = refDir + "Content/der2_cRefset_AssociationDelta_"+edition+"_" + today + ".txt";
		fileMap.put(ComponentType.HISTORICAL_ASSOCIATION, assocDeltaFilename);
		writeToRF2File(assocDeltaFilename, assocHeader);

		compAnnotDeltaFilename = refDir + "Metadata/der2_scsRefset_ComponentAnnotationStringValueDelta_"+edition+"_" + today + ".txt";
		fileMap.put(ComponentType.COMPONENT_ANNOTATION, compAnnotDeltaFilename);
		writeToRF2File(compAnnotDeltaFilename, compAnnotHeader);
	}
	
	protected int outputModifiedComponents(boolean alwaysCheckSubComponents) throws TermServerScriptException {
		LOGGER.info("Outputting to RF2 in {}...", outputDirName);
		int conceptsOutput = 0;
		for (Concept thisConcept : gl.getAllConcepts()) {
			try {
				if (outputRF2(thisConcept, alwaysCheckSubComponents)) {
					conceptsOutput++;
				}
			} catch (TermServerScriptException e) {
				report(thisConcept, null, Severity.CRITICAL, ReportActionType.API_ERROR, "Exception while processing: " + e.getMessage() + " : " + SnomedUtils.getStackTrace(e));
			}
		}
		return conceptsOutput;
	}
	
	protected void outputRF2(ComponentType componentType, String[] columns) throws TermServerScriptException {
		String fileName = fileMap.get(componentType);
		writeToRF2File(fileName, columns);
	}

	protected boolean outputRF2(Description d) throws TermServerScriptException {
		boolean componentOutput = false;
		if (d.isDirty()) {
			writeToRF2File(descDeltaFilename, d.toRF2());
			componentOutput = true;
		}
		//Does this component itself have an associated annotations?
		outputComponentAnnotations(d);

		for (LangRefsetEntry lang : d.getLangRefsetEntries()) {
			if (lang.isDirty()) {
				writeToRF2File(langDeltaFilename, lang.toRF2());
				componentOutput = true;
			}
			//Does this component itself have an associated annotations?
			outputComponentAnnotations(lang);
		}
		for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries()) {
			if (i.isDirty()) {
				writeToRF2File(attribValDeltaFilename, i.toRF2());
				componentOutput = true;
			}
			//Does this component itself have an associated annotations?
			outputComponentAnnotations(i);
		}
		for (AssociationEntry a : d.getAssociationEntries()) {
			if (a.isDirty()) {
				writeToRF2File(assocDeltaFilename, a.toRF2());
				componentOutput = true;
			}
			//Does this component itself have an associated annotations?
			outputComponentAnnotations(a);
		}
		return componentOutput;
	}
	
	protected boolean outputRF2(Relationship r) throws TermServerScriptException {
		boolean componentOutput = false;
		if (r.isDirty()) {
			switch (r.getCharacteristicType()) {
				case STATED_RELATIONSHIP : writeToRF2File(sRelDeltaFilename, r.toRF2());
				break;
				case INFERRED_RELATIONSHIP : 
				default: writeToRF2File(relDeltaFilename, r.toRF2());
			}
			componentOutput = true;
		}
		//Does this component itself have an associated annotations?
		componentOutput |= outputComponentAnnotations(r);
		return componentOutput;
	}
	
	protected boolean outputRF2(InactivationIndicatorEntry i) throws TermServerScriptException {
		if (i.isDirty()) {
			writeToRF2File(attribValDeltaFilename, i.toRF2());
		}
		//Does this component itself have an associated annotations?
		outputComponentAnnotations(i);
		return i.isDirty();
	}

	private boolean outputComponentAnnotations(Component c) throws TermServerScriptException {
		boolean componentOutput = false;
		for (ComponentAnnotationEntry cae: c.getComponentAnnotationEntries()) {
			componentOutput |= outputRF2(cae);
		}
		return componentOutput;
	}

	protected boolean outputRF2(AssociationEntry h) throws TermServerScriptException {
		if (h.isDirty()) {
			writeToRF2File(assocDeltaFilename, h.toRF2());
		}
		//Does this component itself have an associated annotations?
		outputComponentAnnotations(h);
		return h.isDirty();
	}
	
	protected boolean outputRF2(AxiomEntry a) throws TermServerScriptException {
		if (a.isDirty()) {
			writeToRF2File(owlDeltaFilename, a.toRF2());
		}
		//Does this component itself have an associated annotations?
		outputComponentAnnotations(a);
		return a.isDirty();
	}

	protected boolean outputRF2(ComponentAnnotationEntry cae) throws TermServerScriptException {
		if (cae.isDirty() && !dryRun) {
			writeToRF2File(compAnnotDeltaFilename, cae.toRF2());
		}
		//Does this component itself have an associated annotations?
		//This will be an annotation on an annotation - MCHMOOS
		outputComponentAnnotations(cae);
		return cae.isDirty();
	}
	
	protected boolean outputRF2(Concept c, boolean checkAllComponents) throws TermServerScriptException {
		boolean conceptComponentOutput = false;
		if (c.isDirty()) {
			writeToRF2File(conDeltaFilename, c.toRF2());
			conceptComponentOutput = true;
		} else if (!checkAllComponents) {
			return conceptComponentOutput;
		}
		
		for (Description d : c.getDescriptions(ActiveState.BOTH)) {
			conceptComponentOutput |= outputRF2(d);  //Will output langrefset, inactivation indicators and associations in turn
		}
		
		for (AlternateIdentifier a : c.getAlternateIdentifiers()) {
			if (a.isDirty()) {
				writeToRF2File(altIdDeltaFilename, a.toRF2());
				conceptComponentOutput = true;
			}
		}
		
		//Do we have Stated Relationships that need to be converted to axioms?
		//We'll try merging those with any existing axioms.
		if (hasDirtyStatedRelationships(c)) {
			//In the case of eg reasserting an inactive axiom, we don't want to merge axioms
			convertStatedRelationshipsToAxioms(c, true);
			for (AxiomEntry a : AxiomUtils.convertClassAxiomsToAxiomEntries(c)) {
				String axiomModuleId = targetModuleId == null ? c.getModuleId() : targetModuleId;
				a.setModuleId(axiomModuleId);
				a.setEffectiveTime(null);
				c.getAxiomEntries().add(a);
				if (!c.getModuleId().equals(axiomModuleId)) {
					LOGGER.warn("Mismatch between Concept and Axiom module: {} vs {}", c, a);
				}
				conceptComponentOutput = true;
			}
			
			//Now output inferred relationships
			for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.BOTH)) {
				conceptComponentOutput |= outputRF2(r);
			}
		} else {
			for (Relationship r : c.getRelationships()) {
				//Don't output relationships that are part of an axiom
				if (!r.fromAxiom()) {
					conceptComponentOutput |= outputRF2(r);
				}
			}
		}
		
		for (InactivationIndicatorEntry i: c.getInactivationIndicatorEntries()) {
			conceptComponentOutput |= outputRF2(i);
		}
		
		for (AssociationEntry h: c.getAssociationEntries()) {
			conceptComponentOutput |= outputRF2(h);
		}
		
		for (AxiomEntry a: c.getAxiomEntries()) {
			conceptComponentOutput |= outputRF2(a);
		}

		conceptComponentOutput |= outputComponentAnnotations(c);
		return conceptComponentOutput;
	}

	private boolean hasDirtyStatedRelationships(Concept c) {
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.BOTH)) {
			if (r.isDirty()) {
				return true;
			}
		}
		return false;
	}

	protected boolean outputRF2(Concept c) throws TermServerScriptException {
		//By default, check for modified descriptions and relationships 
		//even if the concept has not been modified.
		if (!dryRun) {
			return outputRF2(c, true);
		}
		return true;
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

	protected int createOutputArchive() throws TermServerScriptException {
		//Make sure our output is up to date before we start creating the archive
		flushFilesWithWait(false);
		return createOutputArchive(true);
	}

	protected int createOutputArchive(boolean outputModifiedComponents) throws TermServerScriptException {
		return createOutputArchive(outputModifiedComponents, 0);
	}

	protected int createOutputArchive(boolean outputModifiedComponents, int conceptsOutput) throws TermServerScriptException {
		archivesCreated++;
		if (dryRun) {
			String msg = "Dry run, skipping archive No. " + archivesCreated + " creation";
			LOGGER.info(msg);
			report((Concept) null, Severity.NONE, ReportActionType.INFO, msg);
		} else {
			if (outputModifiedComponents) {
				conceptsOutput += outputModifiedComponents(true);
			}
			getRF2Manager().flushFiles(true); //Just flush the RF2, we might want to keep the report going
			File archive = SnomedUtils.createArchive(new File(outputDirName));
			String msg = "Created " + archive.getName() + " containing " + conceptsOutput + " concepts";
			LOGGER.info(msg);
			report((Concept) null, Severity.NONE, ReportActionType.INFO, msg);
			return conceptsOutput;
		}
		return 0;
	}

	@Override
	protected boolean inScope(Component c, boolean includeExpectedExtensionModules) {
		if (project.getKey().endsWith(".zip")) {
				//If we're working from a zip file, then we're targeting whatever module we said as part of this Delta generation
				return sourceModuleIds.contains(c.getModuleId());
		}
		return super.inScope(c, includeExpectedExtensionModules);
	}

	protected void process() throws TermServerScriptException {
		throw new UnsupportedOperationException("Override process() in your DeltaGenerator");
	}

	protected void standardExecutionWithIds(String[] args) throws TermServerScriptException {
		standardExecution(args, true);
	}

	protected void standardExecution(String[] args) throws TermServerScriptException {
		standardExecution(args, false);
	}

	protected void standardExecution(String[] args, boolean newIdsRequired) throws TermServerScriptException{
		try {
			runStandAlone = false;
			this.newIdsRequired = newIdsRequired;
			init(args);
			loadProjectSnapshot(false);
			postInit();
			process();
			getRF2Manager().flushFiles(true);  //Flush and Close
			if (!dryRun) {
				SnomedUtils.createArchive(new File(outputDirName));
			}
		} finally {
			finish();
		}
	}
	
	
	protected void close(ZipInputStream zis) {
		try {
			zis.closeEntry();
			zis.close();
		} catch (Exception e) {
			//Well, we tried.
		} 
	}
}
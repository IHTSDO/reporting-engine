package org.ihtsdo.termserver.scripting.delta;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.ihtsdo.termserver.scripting.IdGenerator;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.domain.Relationship;

public abstract class DeltaGenerator extends TermServerScript {
	
	String outputDirName = "output";
	String packageRoot;
	String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
	String packageDir;
	String conDeltaFilename;
	String relDeltaFilename;
	String sRelDeltaFilename;
	String descDeltaFilename;
	String langDeltaFilename;
	String edition = "INT";
	
	protected String languageCode = "en";
	protected boolean isExtension = false;
	
	String[] conHeader = new String[] {"id","effectiveTime","active","moduleId","definitionStatusId"};
	String[] descHeader = new String[] {"id","effectiveTime","active","moduleId","conceptId","languageCode","typeId","term","caseSignificanceId"};
	String[] relHeader = new String[] {"id","effectiveTime","active","moduleId","sourceId","destinationId","relationshipGroup","typeId","characteristicTypeId","modifierId"};
	String[] langHeader = new String[] {"id","effectiveTime","active","moduleId","refsetId","referencedComponentId","acceptabilityId"};
	IdGenerator idGenerator;
	
	protected void report(Concept concept, Description d, Severity severity, ReportActionType actionType, String actionDetail) {
		String line = "";
		
		if (d==null) {
			line = concept.getConceptId() + COMMA + COMMA_QUOTE + 
					concept.getFsn() + QUOTE_COMMA_QUOTE; 
		} else {
			line = concept.getConceptId() + COMMA + 
				d.getDescriptionId() + COMMA_QUOTE + 
				d.getTerm() + QUOTE_COMMA_QUOTE ; 
		}
		line += severity + QUOTE_COMMA_QUOTE + 
				actionType.toString() + QUOTE_COMMA_QUOTE +
				actionDetail + QUOTE;
		writeToFile(line);
	}
	
	protected void init (String[] args) throws IOException, TermServerScriptException {
		super.init(args);
		
		for (int x=0; x<args.length; x++) {
			if (args[x].equals("-i")) {
				idGenerator = IdGenerator.initiateIdGenerator(args[++x]);
			}
		}
		if (idGenerator == null) {
			throw new TermServerScriptException("Command line arguments must supply a list of available sctid using the -i option");
		}
		initialiseReportFile("Concept,DescSctId,Term,Severity,Action,Detail");
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
		println ("Outputting data to " + packageDir);
		initialiseFileHeaders();
	}
	
	private void initialiseFileHeaders() throws TermServerScriptException {
		String termDir = packageDir +"Delta/Terminology/";
		String refDir =  packageDir +"Delta/Refset/";
		conDeltaFilename = termDir + "sct2_Concept_Delta_"+edition+"_" + today + ".txt";
		writeToRF2File(conDeltaFilename, conHeader);
		
		relDeltaFilename = termDir + "sct2_Relationship_Delta_"+edition+"_" + today + ".txt";
		writeToRF2File(relDeltaFilename, relHeader);
		
		sRelDeltaFilename = termDir + "sct2_StatedRelationship_Delta_"+edition+"_" + today + ".txt";
		writeToRF2File(sRelDeltaFilename, relHeader);
		
		descDeltaFilename = termDir + "sct2_Description_Delta-"+languageCode+"_"+edition+"_" + today + ".txt";
		writeToRF2File(descDeltaFilename, descHeader);
		
		langDeltaFilename = refDir + "Language/der2_cRefset_LanguageDelta-"+languageCode+"_"+edition+"_" + today + ".txt";
		writeToRF2File(langDeltaFilename, langHeader);
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
	
	protected void outputRF2(Concept c) throws TermServerScriptException {
		/*if (c.isDirty()) {
			writeToRF2File(conDeltaFilenam, c.toRF2());
		}*/
		
		for (Description d : c.getDescriptions(ActiveState.BOTH)) {
			outputRF2(d);
		}
		
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.BOTH)) {
			outputRF2(r);
		}
	}

}

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
	
	String[] conHeader = new String[] {"id","effectiveTime","active","moduleId","definitionStatusId"};
	String[] descHeader = new String[] {"id","effectiveTime","active","moduleId","conceptId","languageCode","typeId","term","caseSignificanceId"};
	String[] relHeader = new String[] {"id","effectiveTime","active","moduleId","sourceId","destinationId","relationshipGroup","typeId","characteristicTypeId","modifierId"};
	String[] langHeader = new String[] {"id","effectiveTime","active","moduleId","refsetId","referencedComponentId","acceptabilityId"};
	IdGenerator descIdGenerator;
	
	protected void report(Concept concept, Description d, SEVERITY severity, REPORT_ACTION_TYPE actionType, String actionDetail) {
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
				descIdGenerator = IdGenerator.initiateIdGenerator(args[++x]);
			}
		}
		if (descIdGenerator == null) {
			throw new TermServerScriptException("Command line arguments must supply a list of available sctid using the -i option");
		}
		initialiseReportFile("Concept,DescSctId,Term,Severity,Action,Detail");
		//Don't add to previously exported data
		File outputDir = new File (outputDirName);
		int increment = 0;
		while (outputDir.exists()) {
			outputDirName = outputDirName + "_" + (++increment) ;
			outputDir = new File(outputDirName);
		}
		packageRoot = outputDirName + File.separator + "SnomedCT_RF2Release_INT_";
		packageDir = packageRoot + today + File.separator;
		println ("Outputting data to " + packageDir);
		initialiseFileHeaders();
	}
	
	private void initialiseFileHeaders() throws IOException {
		String termDir = packageDir +"Delta/Terminology/";
		String refDir =  packageDir +"Delta/Refset/";
		conDeltaFilename = termDir + "sct2_Concept_Delta_INT_" + today + ".txt";
		writeToRF2File(conDeltaFilename, conHeader);
		
		relDeltaFilename = termDir + "sct2_Relationship_Delta_INT_" + today + ".txt";
		writeToRF2File(relDeltaFilename, relHeader);
		
		sRelDeltaFilename = termDir + "sct2_StatedRelationship_Delta_INT_" + today + ".txt";
		writeToRF2File(sRelDeltaFilename, relHeader);
		
		descDeltaFilename = termDir + "sct2_Description_Delta-en_INT_" + today + ".txt";
		writeToRF2File(descDeltaFilename, descHeader);
		
		langDeltaFilename = refDir + "Language/der2_cRefset_LanguageDelta-en_INT_" + today + ".txt";
		writeToRF2File(langDeltaFilename, langHeader);
	}

	protected void outputRF2(Description d) throws TermServerScriptException, IOException {
		if (d.isDirty()) {
			writeToRF2File(descDeltaFilename, d.toRF2());
		}
		for (LangRefsetEntry lang : d.getLangRefsetEntries()) {
			if (lang.isDirty()) {
				writeToRF2File(langDeltaFilename, lang.toRF2());
			}
		}
	}
	
	protected void writeToRF2File(String fileName, String[] columns) throws IOException {
		File file = ensureFileExists(fileName);
		try(	OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8);
				BufferedWriter bw = new BufferedWriter(osw);
				PrintWriter out = new PrintWriter(bw))
		{
			StringBuffer line = new StringBuffer();
			for (int x=0; x<columns.length; x++) {
				if (x > 0) {
					line.append(TSV_FIELD_DELIMITER);
				}
				line.append(columns[x]==null?"":columns[x]);
			}
			out.print(line.toString() + LINE_DELIMITER);
		} catch (Exception e) {
			println ("Unable to output report rf2 line due to " + e.getMessage());
		}
	}

}

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

public abstract class DeltaGenerator extends TermServerScript {
	
	String packageRoot = "SnomedCT_RF2Release_INT_";
	String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
	String packageDir =  packageRoot + today + File.separator;
	String termDir = packageDir +"/Delta/Terminology/";
	String refDir =  packageDir +"/Delta/Refset/";
	String descDeltaFilename = termDir + "sct2_Description_Delta-en_INT_" + today + ".txt";
	String langDeltaFilename = refDir + "language/der2_cRefset_LanguageDelta-en_INT_" + today + ".txt";
	String[] descHeader = new String[] {"id","effectiveTime","active","moduleId","conceptId","languageCode","typeId","term","caseSignificanceId"};
	String[] langHeader = new String[] {"id","effectiveTime","active","moduleId","refsetId","referencedComponentId","acceptabilityId"};
	IdGenerator idGen = new IdGenerator();
	
	protected void report(Concept concept, Description d, SEVERITY severity, REPORT_ACTION_TYPE actionType, String actionDetail) {
		String line = "";
		
		if (d==null) {
			line = concept.getConceptId() + COMMA + COMMA_QUOTE + 
					concept.getFsn() + QUOTE_COMMA; 
		} else {
			line = concept.getConceptId() + COMMA + 
				d.getDescriptionId() + COMMA_QUOTE + 
				d.getTerm() + QUOTE_COMMA ; 
		}
		line += QUOTE + actionType.toString() + QUOTE_COMMA_QUOTE +
				actionDetail + QUOTE;
		writeToFile(line);
	}
	
	protected void init (String[] args) throws IOException, TermServerScriptException {
		super.init(args);
		initialiseReportFile("Concept,DescSctId,Term,Severity,Action,Detail");
		//Don't add to previously exported data
		File outputRoot = new File (packageDir);
		int increment = 0;
		while (outputRoot.exists() && outputRoot.isDirectory()) {
			packageDir = packageRoot + today + "_" + (++increment) + File.separator;
			outputRoot = new File(packageDir);
		}
		println ("Outputting data to " + packageDir);
	}
	
	protected void outputRF2(Description d) throws TermServerScriptException, IOException {
		writeToRF2File(descDeltaFilename, d.toRF2Desc());
		//TODO Work restarts here
		//writeToRF2File(langDeltaFilename, d.toRF2Lang());		
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
			out.println(line.toString());
		} catch (Exception e) {
			println ("Unable to output report rf2 line due to " + e.getMessage());
		}
	}

}

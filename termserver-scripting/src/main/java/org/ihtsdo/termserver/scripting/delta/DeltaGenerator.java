package org.ihtsdo.termserver.scripting.delta;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

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
		String line = concept.getConceptId() + COMMA + 
				d.getDescriptionId() + COMMA_QUOTE + 
				d.getTerm() + QUOTE_COMMA_QUOTE + 
				actionType.toString() + QUOTE_COMMA_QUOTE +
				actionDetail + QUOTE;
		writeToFile(line);
	}
	
	protected void outputRF2(Description d) throws TermServerScriptException {
		writeToRF2File(descDeltaFilename, d.toRF2Desc());
		//TODO Work restarts here
		//writeToRF2File(langDeltaFilename, d.toRF2Lang());		
	}
	
	protected void writeToRF2File(String fileName, String[] columns) {
		try(	OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(fileName, true), StandardCharsets.UTF_8);
				BufferedWriter bw = new BufferedWriter(osw);
				PrintWriter out = new PrintWriter(bw))
		{
			StringBuffer line = new StringBuffer();
			for (int x=0; x<columns.length; x++) {
				if (x > 0) {
					line.append(TSV_FIELD_DELIMITER);
				}
				line.append(columns[x]);
			}
			out.println(line.toString());
		} catch (Exception e) {
			print ("Unable to output report rf2 line due to " + e.getMessage());
		}
	}
}

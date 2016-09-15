package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.ihtsdo.termserver.scripting.TermServerScript;
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
	
	protected void report(Concept concept, Description d, SEVERITY severity, REPORT_ACTION_TYPE actionType, String actionDetail) {
		String line = concept.getConceptId() + COMMA + 
				d.getDescriptionId() + COMMA_QUOTE + 
				d.getTerm() + QUOTE_COMMA_QUOTE + 
				actionType.toString() + QUOTE_COMMA_QUOTE +
				actionDetail + QUOTE;
		writeToFile(line);
	}
}

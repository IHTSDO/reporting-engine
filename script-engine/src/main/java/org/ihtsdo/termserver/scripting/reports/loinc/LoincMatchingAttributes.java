package org.ihtsdo.termserver.scripting.reports.loinc;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * Reports all concepts in the LOINC module that feature some attribute + value
 * INFRA-4838
 */
public class LoincMatchingAttributes extends TermServerScript{
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		LoincMatchingAttributes report = new LoincMatchingAttributes();
		try {
			report.init(args);
			report.loadProjectSnapshot(true);  
			report.reportMatchingConcepts();
		} finally {
			report.finish();
		}
	}

	private void reportMatchingConcepts() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			//if (c.isActive() && c.getModuleId().equals()) {
				
			//}
		}
	}

}

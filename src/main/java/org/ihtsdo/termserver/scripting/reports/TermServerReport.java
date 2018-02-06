package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;

public abstract class TermServerReport extends TermServerScript {
	
	protected String headers = "Concept,FSN,";
	
	protected void init(String[] args) throws TermServerScriptException, SnowOwlClientException {
		try {
			super.init(args);
			initialiseReportFile(headers + additionalReportColumns);
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to initialise output report",e);
		}
	}
	

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return gl.getConcept(lineItems[0]);
	}
	
	protected void report (Concept c, String... details) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE;
		
		for (String detail : details) {
			 line += COMMA_QUOTE + detail + QUOTE;
		}
		writeToReportFile(line);
	}

}

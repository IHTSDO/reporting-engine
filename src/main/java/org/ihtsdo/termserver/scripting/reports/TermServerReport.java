package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;

public abstract class TermServerReport extends TermServerScript {
	
	protected String headers = "Concept,FSN,";
	protected boolean quiet = false; 
	
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
		String field = lineItems[0];
		//Do we have the FSN in here?
		if (field.contains(PIPE)) {
			String[] parts = field.split(ESCAPED_PIPE);
			field = parts[0].trim();
		}
		return gl.getConcept(field);
	}
	
	protected void report (Concept c, Object... details) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE;
		
		for (Object detail : details) {
			 line += COMMA_QUOTE + detail.toString() + QUOTE;
		}
		writeToReportFile(line);
	}
	
	public void setQuiet(boolean quiet) {
		this.quiet = quiet;
	}
	
	@Override
	public void incrementSummaryInformation(String key) {
		if (!quiet) {
			super.incrementSummaryInformation(key);
		}
	}

}

package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;

public abstract class TermServerReport extends TermServerScript {
	
	protected String headers = "Concept,";
	
	protected void init(String[] args) throws TermServerScriptException, SnowOwlClientException {
		try {
			super.init(args);
			initialiseReportFiles( new String[] {headers + additionalReportColumns, headers + secondaryReportColumns, headers + tertiaryReportColumns});
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to initialise output report",e);
		}
	}

	@Override
	protected List<Concept> loadLine(String[] lineItems)
			throws TermServerScriptException {
		String field = lineItems[0];
		//Do we have the FSN in here?
		if (field.contains(PIPE)) {
			String[] parts = field.split(ESCAPED_PIPE);
			field = parts[0].trim();
		}
		return Collections.singletonList(gl.getConcept(field));
	}
	
	protected void report (int reportIdx, Component c, Object... details) {
		String line = "";
		if (c instanceof Concept) {
			Concept concept = (Concept) c;
			line = concept.getConceptId() + COMMA_QUOTE + 
					 concept.getFsn() + QUOTE;
		} else if (c instanceof Relationship) {
			Relationship r = (Relationship) c;
			line = r.getSourceId() + COMMA_QUOTE + 
					r.toString() + QUOTE;
		}
		
		for (Object detail : details) {
			if (detail instanceof Collection) {
				for (Object subDetail : (Collection<?>)detail) {
					line += COMMA_QUOTE + subDetail.toString() + QUOTE;
				}
			} else {
				line += COMMA_QUOTE + (detail == null ? "" : detail.toString()) + QUOTE;
			}
		}
		writeToReportFile(reportIdx, line);
	}
	
	protected void report (Component c, Object... details) {
		report (0, c, details);
	}

	@Override
	public void incrementSummaryInformation(String key) {
		if (!quiet) {
			super.incrementSummaryInformation(key);
		}
	}

}

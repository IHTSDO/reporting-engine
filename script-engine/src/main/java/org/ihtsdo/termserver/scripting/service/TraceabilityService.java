package org.ihtsdo.termserver.scripting.service;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;

public interface TraceabilityService {
	
	void flush() throws TermServerScriptException;
	
	void populateTraceabilityAndReport(int tabIdx, Component c, Object... details) throws TermServerScriptException;

	void populateTraceabilityAndReport(int tabIdx, Concept c, Object... details) throws TermServerScriptException;

	void populateTraceabilityAndReport(String fromDate, String toDate, int tab, Concept c, Object... details) throws TermServerScriptException;

	void tidyUp() throws TermServerScriptException;
	
	void setBranchPath(String onBranch);

}
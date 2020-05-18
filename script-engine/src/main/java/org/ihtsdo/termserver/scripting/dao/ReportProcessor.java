package org.ihtsdo.termserver.scripting.dao;

import org.ihtsdo.otf.exception.TermServerScriptException;

// TODO (this needs to be completed fully -- it's just here basically as a marker right now)
public interface ReportProcessor {

    void initialiseReportFiles(String[] columnHeaders) throws TermServerScriptException;

    void writeToReportFile(int reportIdx, String line, boolean delayWrite) throws TermServerScriptException;
}

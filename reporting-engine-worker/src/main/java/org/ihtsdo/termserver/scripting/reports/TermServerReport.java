package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.snomed.otf.scheduler.domain.JobRun;

public abstract class TermServerReport extends TermServerScript {
	
	protected void init(String[] args) throws TermServerScriptException, SnowOwlClientException {
		init(args, false); //Don't delay initialisation of report files by default
	}
	protected void init(String[] args, boolean delayReportInitialisation) throws TermServerScriptException, SnowOwlClientException {
		try {
			super.init(args);
			if (!delayReportInitialisation) {
				getReportManager().initialiseReportFiles( new String[] {headers + additionalReportColumns});
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to initialise output report",e);
		}
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		String field = lineItems[0];
		//Do we have the FSN in here?
		if (field.contains(PIPE)) {
			String[] parts = field.split(ESCAPED_PIPE);
			field = parts[0].trim();
		}
		return Collections.singletonList(gl.getConcept(field));
	}
	
	protected void report (int reportIdx, Component c, Object... details) throws TermServerScriptException {
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
			if (detail instanceof String[]) {
				for (Object subDetail : (String[])detail) {
					String item = subDetail.toString();
					if (StringUtils.isNumeric(item)) {
						line += COMMA + item;
					} else {
						line += COMMA_QUOTE + item + QUOTE;
					}
				}
			} else if (detail instanceof int[]) {
				for (int subDetail : (int[])detail) {
						line += COMMA + subDetail;
				}
			} else if (detail instanceof Collection) {
				for (Object subDetail : (Collection<?>)detail) {
					line += COMMA_QUOTE + subDetail.toString() + QUOTE;
				}
			} else {
				String item = detail.toString();
				if (StringUtils.isNumeric(item)) {
					line += COMMA + (detail == null ? "" : detail.toString());
				} else {
					line += COMMA_QUOTE + (detail == null ? "" : detail.toString()) + QUOTE;
				}
			} 
		}
		writeToReportFile(reportIdx, line);
	}
	
	protected void reportSafely (int reportIdx, Component c, Object... details) {
		try {
			report (reportIdx, c, details);
		} catch (TermServerScriptException e) {
			throw new IllegalStateException("Failed to write to report", e);
	}
	}
	
	protected void report (Component c, Object... details) throws TermServerScriptException {
		report (0, c, details);
	}

	@Override
	public void incrementSummaryInformation(String key) {
		if (!quiet) {
			super.incrementSummaryInformation(key);
		}
	}
	
	public static void run(Class<? extends ReportClass> reportClass, String[] args) throws TermServerScriptException {
		run(reportClass, args, null);
	}
	
	public static void run(Class<? extends ReportClass> reportClass, String[] args, Map<String, String> parameters) throws TermServerScriptException {
		JobRun jobRun = createJobRunFromArgs(reportClass.getSimpleName(), args);
		if (parameters != null) {
			for (Map.Entry<String, String> entry : parameters.entrySet()) {
				jobRun.setParameter(entry.getKey(), entry.getValue());
			}
		}
		ReportClass report = null;
		try {
			report = reportClass.newInstance();
			((TermServerReport)report).checkSettingsWithUser(jobRun);
		} catch ( InstantiationException | IllegalAccessException e) {
			throw new TermServerScriptException("Unable to instantiate " + reportClass.getSimpleName(), e);
		}
		report.instantiate(jobRun);
	}
}

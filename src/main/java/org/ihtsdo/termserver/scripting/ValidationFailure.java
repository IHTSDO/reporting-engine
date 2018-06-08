package org.ihtsdo.termserver.scripting;

import org.ihtsdo.termserver.scripting.TermServerScript.ReportActionType;
import org.ihtsdo.termserver.scripting.TermServerScript.Severity;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Task;

public class ValidationFailure extends TermServerScriptException {

	private static final long serialVersionUID = 9031426261460131112L;
	
	Task task;
	Concept concept;
	Severity severity;
	ReportActionType reportActionType;

	public ValidationFailure(Concept concept, Severity severity, ReportActionType rat, String msg) {
		super(msg);
		this.concept = concept;
		this.severity = severity;
		this.reportActionType = rat;
	}
	
	public ValidationFailure(Task task, Concept concept, Severity severity, ReportActionType rat, String msg) {
		super(msg);
		this.task = task;
		this.concept = concept;
		this.severity = severity;
		this.reportActionType = rat;
	}

	public ValidationFailure(Concept c, String msg) {
		this(c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, msg);
	}

	public Concept getConcept() {
		return concept;
	}

	public Severity getSeverity() {
		return severity;
	}

	public ReportActionType getReportActionType() {
		return reportActionType;
	}

	public Task getTask() {
		return task;
	}

	public void setTask(Task task) {
		this.task = task;
	}
}

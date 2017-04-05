package org.ihtsdo.termserver.scripting;

import org.ihtsdo.termserver.scripting.TermServerScript.ReportActionType;
import org.ihtsdo.termserver.scripting.TermServerScript.Severity;
import org.ihtsdo.termserver.scripting.domain.Concept;

public class ValidationFailure extends Exception {

	private static final long serialVersionUID = 9031426261460131112L;
	
	Concept concept;
	Severity severity;
	ReportActionType reportActionType;

	public ValidationFailure(Concept concept, Severity severity, ReportActionType rat, String msg) {
		super(msg);
		this.concept = concept;
		this.severity = severity;
		this.reportActionType = rat;
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
}

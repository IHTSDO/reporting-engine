package org.ihtsdo.termserver.scripting.service;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.traceability.TraceabilityServiceClient;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.snomed.otf.traceability.domain.Activity;

import java.text.SimpleDateFormat;
import java.util.List;

public abstract class CommonTraceabilityService implements TraceabilityService {
	protected TraceabilityServiceClient client;
	protected TermServerScript ts;

	protected String onBranch = null;

	protected List<String> userFilter;
	protected List<String> projectFilter;
	protected String fromEffectiveTime;

	private SimpleDateFormat etDateFormat = new SimpleDateFormat("yyyyMMdd");


	public TraceabilityService withUserFilter(List<String> users) {
		this.userFilter = users;
		return this;
	}

	public TraceabilityService withProjectFilter(List<String> projects) {
		this.projectFilter = projects;
		return this;
	}

	public TraceabilityService fromEffectiveTime(String fromEffectiveTime) {
		this.fromEffectiveTime = fromEffectiveTime;
		return this;
	}

	@Override
	public void setBranchPath(String onBranch) {
		this.onBranch = onBranch;
	}

	@Override
	public void flush() throws TermServerScriptException {
		//Default implementation does nothing, override if required
	}

	@Override
	public void tidyUp() throws TermServerScriptException {
		//Default implementation does nothing, override if required
	}


	protected List<Activity> filter(List<Activity> activities) {
		return activities.stream()
				.filter(a -> userFilter == null || userFilter.contains(a.getUsername()))
				.filter(a -> projectFilter == null || projectFilter.contains(a.getBranch()))
				.filter(a -> fromEffectiveTime == null || commitWithinDateRange(a))
				.toList();
	}

	private boolean commitWithinDateRange(Activity a) {
		String activityCommitDate = etDateFormat.format(a.getCommitDate());
		return activityCommitDate.compareTo(fromEffectiveTime) >= 0;
	}

}


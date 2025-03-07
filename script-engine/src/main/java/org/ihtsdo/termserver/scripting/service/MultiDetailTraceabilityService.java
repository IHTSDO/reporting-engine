package org.ihtsdo.termserver.scripting.service;

import java.util.List;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.traceability.TraceabilityServiceClient;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.JobRun;
import org.snomed.otf.traceability.domain.Activity;
import org.snomed.otf.traceability.domain.ComponentChange;
import org.snomed.otf.traceability.domain.ConceptChange;

public class MultiDetailTraceabilityService implements TraceabilityService {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(MultiDetailTraceabilityService.class);

	private TraceabilityServiceClient client;
	private TermServerScript ts;
	private String onBranch = null;
	
	public MultiDetailTraceabilityService(JobRun jobRun, TermServerScript ts) {
		this.client = new TraceabilityServiceClient(jobRun.getTerminologyServerUrl(), jobRun.getAuthToken());
		this.ts = ts;
	}
	
	public void tidyUp() {
		
	}
	
	public int populateTraceabilityAndReport(int tabIdx, Component c, Object... details) throws TermServerScriptException {
		int rowsReported = 0;
		Concept owningConcept = ts.getGraphLoader().getComponentOwner(c.getId());
		try {
			List<Activity> activities = client.getComponentActivity(c.getId(), onBranch);
			for (Activity activity : activities) {
				for (ConceptChange conceptchange : activity.getConceptChanges()) {
					for (ComponentChange compChange: conceptchange.getComponentChanges()) {
						if (compChange.getComponentId().equals(c.getId())) {
							ts.report(tabIdx, c.getId(),
									c.getComponentType(),
									compChange.getChangeType(),
									BooleanUtils.toString(compChange.superseded(), "Y", "N", "N"),
									activity.getCommitDate(), 
									activity.getPromotionDate(), 
									activity.getBranch(), 
									activity.getUsername(),
									activity.getHighestPromotedBranch(),
									(owningConcept == null ? "Unknown" : owningConcept),
									c);
							rowsReported++;
						}
					}
					
				}
			}
		} catch (Exception e) {
			ts.report(tabIdx, c.getId(), c.getComponentType(), e);
			LOGGER.error("Error reported", e);
		}
		return rowsReported;
	}

	@Override
	public void populateTraceabilityAndReport(String fromDate, String toDate, int tab, Concept c, Object... details) {
		throw new NotImplementedException("This class uses bulk method, not single concept lookup");
	}

	@Override
	public void setBranchPath(String onBranch) {
		this.onBranch = onBranch;
	}

	@Override
	public void flush() throws TermServerScriptException {
	}

	@Override
	public void populateTraceabilityAndReport(int tabIdx, Concept c, Object... details)
			throws TermServerScriptException {
		throw new NotImplementedException("This class works with components, not concepts.");
	}

}

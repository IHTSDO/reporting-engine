package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * Lists all active descriptions that have no acceptability
 */
public class UnacceptableDescriptions extends TermServerReport{
	
	List<Concept> targetHierarchies = new ArrayList<Concept>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		UnacceptableDescriptions report = new UnacceptableDescriptions();
		try {
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			info ("Producing active unacceptable terms report...");
			report.checkCaseSignificance();
		} finally {
			report.finish();
		}
	}

	private void checkCaseSignificance() throws TermServerScriptException {
		//Work through all active descriptions of all hierarchies
		Collection<Concept> topLevelHierarchies = gl.getConcept(SCTID_ROOT_CONCEPT).getDescendents(IMMEDIATE_CHILD);
		targetHierarchies.addAll(topLevelHierarchies);
		int count = 0;
		int conceptsChecked = 0;
		for (Concept targetHierarchy : targetHierarchies) {
			for (Concept c : targetHierarchy.getDescendents(NOT_SET)) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (d.getLangRefsetEntries(ActiveState.ACTIVE).isEmpty()) {
						report (c, d, d.getEffectiveTime(), "Active term has no acceptability");
						count++;
					}
				}
				conceptsChecked++;
			}
		}
		info (count + " unacceptable descriptions reported in " + conceptsChecked + " concepts.");
	}
	
	private void report(Concept c, Description d, String effectiveTime, String issue) throws TermServerScriptException {
		StringBuffer sb = new StringBuffer();
		sb.append(c.getConceptId()).append(COMMA_QUOTE)
			.append(c.getFsn()).append(QUOTE_COMMA)
			.append(d.getDescriptionId()).append(COMMA_QUOTE)
			.append(effectiveTime).append(QUOTE_COMMA_QUOTE)
			.append(issue).append(QUOTE_COMMA_QUOTE)
			.append(d.getTerm()).append(QUOTE);
		writeToReportFile(sb.toString());
	}

	protected void init(String[] args) throws TermServerScriptException, SnowOwlClientException {
		super.init(args);
		writeToReportFile("concept, fsn, descId, effectiveTime, issue, description");
	}

}

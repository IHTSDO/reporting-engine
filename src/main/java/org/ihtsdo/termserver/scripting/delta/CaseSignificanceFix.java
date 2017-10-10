package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Class to replace relationships with alternatives
 * Example TS Task: MAIN/2017-01-31/SNOMEDCT-US/USTEST/USTEST-6002
 * INFRA-1232
 */
public class CaseSignificanceFix extends DeltaGenerator implements RF2Constants {

	private List<String> exceptions = new ArrayList<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		CaseSignificanceFix delta = new CaseSignificanceFix();
		try {
			delta.newIdsRequired = false; // We'll only be inactivating existing relationships
			delta.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			delta.loadProjectSnapshot(false); 
			//We won't include the project export in our timings
			delta.startTimer();
			delta.process();
			delta.flushFiles(false);
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException, SnowOwlClientException {
		super.init(args);
		exceptions.add("10692681000119108");
		exceptions.add("734937009");
		exceptions.add("735239006");
		exceptions.add("735238003");
		exceptions.add("725582001");
		exceptions.add("137421000119106");
	}

	private void process() throws TermServerScriptException {
		println("Processing...");
		for (Concept c : GraphLoader.getGraphLoader().getAllConcepts()) {
			if (c.isActive()) {
				if (exceptions.contains(c.getId())) {
					report (c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Concept manually listed as an exception");
				} else {
					for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
						if (d.getCaseSignificance().equals(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE.toString()) &&
								!SnomedUtils.isCaseSensitive(d.getTerm())) {
							d.setCaseSignificance(SCTID_ENTIRE_TERM_CASE_INSENSITIVE);
							report(c,d,Severity.LOW,ReportActionType.DESCRIPTION_CHANGE_MADE,  "Set to entire term case insensitive.  Last modified " + d.getEffectiveTime());
							d.setEffectiveTime(null);
							d.setDirty();
							c.setModified();  //Indicates concept contains changes, without necessarily needing a concept RF2 line output
							incrementSummaryInformation("Descriptions modified", 1);
						}
					}
				}
			}
			if (c.isModified()) {
				incrementSummaryInformation("Concepts modified", 1);
				outputRF2(c);  //Will only output dirty fields.
			}
		}
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}

}

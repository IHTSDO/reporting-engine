package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.ihtsdo.termserver.scripting.util.StringUtils;

/**
 * Class to fix Case Significance issues.
 */
public class CaseSignificanceFix extends DeltaGenerator implements RF2Constants {

	private List<String> exceptions = new ArrayList<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException, InterruptedException {
		CaseSignificanceFix delta = new CaseSignificanceFix();
		try {
			delta.newIdsRequired = false; // We'll only be modifying existing descriptions
			delta.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			delta.loadProjectSnapshot(false); 
			//We won't include the project export in our timings
			delta.startTimer();
			delta.process();
			delta.flushFiles(false, true); //Need to flush files before zipping
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException {
		super.init(args);
		exceptions.add("10692681000119108");
		exceptions.add("108796006");
		exceptions.add("108797002");
		exceptions.add("108798007");
		exceptions.add("10997931000119101");
		exceptions.add("129477009");
		exceptions.add("137421000119106");
		exceptions.add("386914007");
		exceptions.add("386915008");
		exceptions.add("398841009");
		exceptions.add("419598008");
		exceptions.add("421559001");
		exceptions.add("718688008");
		exceptions.add("724096007");
		exceptions.add("725026008");
		exceptions.add("725027004");
		exceptions.add("725028009");
		exceptions.add("725078006");
		exceptions.add("725079003");
		exceptions.add("725390002");
		exceptions.add("725391003");
		exceptions.add("725582001");
		exceptions.add("725930008");
		exceptions.add("726705007");
		exceptions.add("726706008");
		exceptions.add("726707004");
		exceptions.add("732252005");
		exceptions.add("732259001");
		exceptions.add("733083006");
		exceptions.add("733084000");
		exceptions.add("733085004");
		exceptions.add("733111000");
		exceptions.add("733112007");
		exceptions.add("733115009");
		exceptions.add("733473000");
		exceptions.add("733518000");
		exceptions.add("733519008");
		exceptions.add("733520002");
		exceptions.add("733521003");
		exceptions.add("733598001");
		exceptions.add("733843002");
		exceptions.add("734016004");
		exceptions.add("734029004");
		exceptions.add("734030009");
		exceptions.add("734937009");
		exceptions.add("734977001");
		exceptions.add("734978006");
		exceptions.add("735238003");
		exceptions.add("735239006");
		exceptions.add("737545003");
		exceptions.add("737546002");
		exceptions.add("737547006");
	}

	private void process() throws TermServerScriptException {
		info("Processing...");
		for (Concept c : GraphLoader.getGraphLoader().getAllConcepts()) {
			if (c.isActive()) {
				if (exceptions.contains(c.getId())) {
					report (c, null, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Concept manually listed as an exception");
				} else {
					for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
						if (d.getCaseSignificance().equals(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE) &&
								!StringUtils.isCaseSensitive(d.getTerm())) {
							d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
							String msg = "Set to entire term case insensitive.  Last modified " + d.getEffectiveTime();
							report(c,d,Severity.LOW,ReportActionType.DESCRIPTION_CHANGE_MADE, msg);
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
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}

}

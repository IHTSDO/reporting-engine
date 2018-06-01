package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import us.monoid.json.JSONObject;

/*
 * Addresses case significance issues in the target sub-hierarchy
 */
public class CaseSignificanceFix extends BatchFix implements RF2Constants{
	
	boolean unpublishedContentOnly = true;
	
	protected CaseSignificanceFix(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		CaseSignificanceFix fix = new CaseSignificanceFix(null);
		try {
			fix.selfDetermining = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			Batch batch = fix.formIntoBatch();
			fix.batchProcess(batch);
		} finally {
			fix.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException, IOException {
		super.init(args);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = fixCaseSignifianceIssues(task, loadedConcept);
		if (changesMade > 0) {
			try {
				String conceptSerialised = gson.toJson(loadedConcept);
				debug ((dryRun ?"Dry run ":"Updating state of ") + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + e.getMessage());
			}
		}
		return changesMade;
	}

	private int fixCaseSignifianceIssues(Task task, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if ( (!unpublishedContentOnly || !d.isReleased()) &&
					d.getTerm().contains("Product containing") && d.getTerm().contains("milliliter")) {
				String caseSig = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
				String firstLetter = d.getTerm().substring(0,1);
				String chopped = d.getTerm().substring(1);
				//Lower case first letters must be entire term case sensitive
				if (Character.isLetter(firstLetter.charAt(0)) && firstLetter.equals(firstLetter.toLowerCase()) && !caseSig.equals(CS)) {
					//Not dealing with this situation right now
					//report (c, d, preferred, caseSig, "Terms starting with lower case letter must be CS");
				} else if (caseSig.equals(CS) || caseSig.equals(cI)) {
					if (chopped.equals(chopped.toLowerCase())) {
						report (task, c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, d, "-> ci" );
						d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
						changesMade++;
					}
				} else {
					//For case insensitive terms, we're on the look out for capitial letters after the first letter
					if (!chopped.equals(chopped.toLowerCase())) {
						//Not dealing with this situation right now
						//report (c, d, preferred, caseSig, "Case insensitive term has a capital after first letter");
						//incrementSummaryInformation("issues");
					}
				}
			}
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> processMe = new ArrayList<Component>();
		print ("Identifying incorrect case signficance settings");
		this.setQuiet(true);
		for (Concept concept : PHARM_BIO_PRODUCT.getDescendents(NOT_SET)) {
			if (concept.isActive()) {
				if (fixCaseSignifianceIssues(null, concept.cloneWithIds()) > 0) {
					processMe.add(concept);
				}
			}
		}
		debug ("Identified " + processMe.size() + " concepts to process");
		this.setQuiet(false);
		return processMe;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}

}

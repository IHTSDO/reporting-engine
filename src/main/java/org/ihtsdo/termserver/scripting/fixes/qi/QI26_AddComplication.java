package org.ihtsdo.termserver.scripting.fixes.qi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.template.AncestorsCache;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import us.monoid.json.JSONObject;
/**
 * QI-26
 * for << 404684003 |Clinical finding (finding)| set additional parent to be 116223007 |Complication (disorder)| 
 * where concept has an active 42752001 |Due to (attribute)| = << 404684003 |Clinical finding (finding)| 
 * and existing PPP can be calculated as Complication or Disease.
 * Also (possibly in combination)
 * After relationship → PPP of Sequela
 */
public class QI26_AddComplication extends BatchFix {
	
	Concept newProximalPrimitiveParent = COMPLICATION;
	List<Concept> acceptablePPPs;
	
	protected QI26_AddComplication(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		QI26_AddComplication fix = new QI26_AddComplication(null);
		try {
			fix.populateEditPanel = false;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "Action Detail";
			fix.init(args);
			fix.loadProjectSnapshot(true);
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() {
		acceptablePPPs = new ArrayList<>();
		acceptablePPPs.add(COMPLICATION);
		acceptablePPPs.add(DISEASE);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = checkAndSetProximalPrimitiveParent(task, loadedConcept, COMPLICATION);
			String conceptSerialised = gson.toJson(loadedConcept);
			if (changesMade > 0) {
				debug ((dryRun ?"Dry run ":"Updating state of ") + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			}
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

	@Override
	/* ECL to find candidates:
	 *  << 404684003 : 42752001 = << 404684003
	 */
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> processMe = new ArrayList<>();
		//Find all descendants of 404684003 |Clinical finding (finding)| 
		for (Concept c : CLINICAL_FINDING.getDescendents(NOT_SET)) {
			/*if (c.getConceptId().equals("238794007")) {
				debug ("Check 238794007 |Ischemic foot ulcer (disorder)|");
			}*/
			//which have Due To = Clinical Finding
			if (SnomedUtils.getSubsumedRelationships(c, DUE_TO, CLINICAL_FINDING, CharacteristicType.INFERRED_RELATIONSHIP, ancestorsCache).size() > 0) {
				//and do not have Complication as an existing parent
				if (!c.getParents(CharacteristicType.STATED_RELATIONSHIP).contains(COMPLICATION)) {
					//And exisiting PPP can be calculated as acceptable
					List<Concept> ppps = determineProximalPrimitiveParents(c);
					if (ppps.size() != 1) {
						incrementSummaryInformation("Multiple proximal primitive parents");
					} else {
						Concept ppp = ppps.get(0);
						if (acceptablePPPs.contains(ppp)) {
							processMe.add(c);
						} else {
							incrementSummaryInformation("Intermediate Primitive blocked calculation of correct PPP");	
						}
					}
					
				}
			}
		}
		return processMe;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		throw new NotImplementedException();
	}

}

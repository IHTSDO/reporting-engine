package org.ihtsdo.termserver.scripting.fixes.qi;

import java.util.*;

import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * QI-26
 * for << 404684003 |Clinical finding (finding)| set additional parent to be 116223007 |Complication (disorder)| 
 * where concept has an active 42752001 |Due to (attribute)| = << 404684003 |Clinical finding (finding)| 
 * and existing PPP can be calculated as Complication or Disease.
 * Also (possibly in combination)
 * After relationship → PPP of Sequela
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QI26_AddComplication extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(QI26_AddComplication.class);

	Concept newProximalPrimitiveParent = COMPLICATION;
	List<Concept> acceptablePPPs;
	
	protected QI26_AddComplication(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		QI26_AddComplication fix = new QI26_AddComplication(null);
		try {
			fix.populateEditPanel = false;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.classifyTasks = true;
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
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		changesMade = checkAndSetProximalPrimitiveParent(t, loadedConcept, COMPLICATION);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
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
		for (Concept c : CLINICAL_FINDING.getDescendants(NOT_SET)) {
			/*if (c.getConceptId().equals("238794007")) {
				LOGGER.debug("Check 238794007 |Ischemic foot ulcer (disorder)|");
			}*/
			//which have Due To = Clinical Finding
			if (SnomedUtils.getSubsumedRelationships(c, DUE_TO, CLINICAL_FINDING, CharacteristicType.INFERRED_RELATIONSHIP, gl.getAncestorsCache()).size() > 0) {
				//and do not have Complication as an existing parent
				if (!c.getParents(CharacteristicType.STATED_RELATIONSHIP).contains(COMPLICATION)) {
					//And exisiting PPP can be calculated as acceptable
					List<Concept> ppps = determineProximalPrimitiveParents(c);
					if (ppps.size() != 1) {
						incrementSummaryInformation("Multiple proximal primitive parents");
					} else {
						Concept ppp = ppps.iterator().next();
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

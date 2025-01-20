package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
 * When a core axiom has been modified in an extension (due to merge issues)
 * revert the axiom to it's previously published state
 * 
 * Hmm.  This shows up nothing.  So either we have no axioms to revert, or the 
 * inconsistencies are not stored against axioms with no effective time, in which 
 * case we have a reconcilliation problem.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RevertCoreAxioms extends BatchFix implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(RevertCoreAxioms.class);

	Map<String, RefsetMember> changeMap = new HashMap<>();

	protected RevertCoreAxioms(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		RevertCoreAxioms fix = new RevertCoreAxioms(null);
		try {
			fix.selfDetermining = true;
			fix.populateEditPanel = false;
			fix.init(args);
			fix.loadProjectSnapshot(false); //Load all description
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		LOGGER.debug("Processing " + c);
		for (AxiomEntry a : c.getAxiomEntries()) {
			if (!a.isActive() && StringUtils.isEmpty(a.getEffectiveTime())) {
				RefsetMember currentMember = loadRefsetMember(a.getId());
				RefsetMember previousMember = changeMap.get(a.getId());
				
				//If we haven't stored this previous member, it's not one we need to worry about
				if (previousMember == null) {
					continue;
				}
				String prev = previousMember.getField("owlExpression");
				String current = currentMember.getField("owlExpression");

				if (!currentMember.getId().equals(previousMember.getId())) {
					throw new TermServerScriptException("Member id mismatch at " + c);
				} else if (prev.equals(current)) {
					throw new TermServerScriptException("No changed detectded at " + c);
				}
				updateRefsetMember(previousMember);
				report(t, c, Severity.LOW, ReportActionType.AXIOM_CHANGE_MADE, currentMember.getId(), prev, current);
				changesMade++;
			}
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		Set<Concept> allAffected = new TreeSet<Concept>();  //We want to process in the same order each time, in case we restart and skip some.
		
		//How many are we going to check?
		int toCheck = 0;
		for (Concept c : gl.getAllConcepts()) {
			for (AxiomEntry a : c.getAxiomEntries()) {
				if (SnomedUtils.isCore(a) && StringUtils.isEmpty(a.getEffectiveTime())) {
					toCheck++;
				}
			}
		}
		LOGGER.info("Checking " + toCheck + " axioms");
		
		int checked = 0;
		for (Concept c : gl.getAllConcepts()) {
			for (AxiomEntry a : c.getAxiomEntries()) {
				if (SnomedUtils.isCore(a) && StringUtils.isEmpty(a.getEffectiveTime())) {
					//Recover this axiom from the previous release and see if it's been changed
					changeMap.put(a.getId(), loadPreviousRefsetMember(a.getId()));
					if (++checked % 100 == 0) {
						LOGGER.debug("Completed " + checked + " / " + toCheck);
					}
				}
			}
		}
		LOGGER.info (allAffected.size() + " concepts affected");
		return new ArrayList<Component>(allAffected);
	}
}

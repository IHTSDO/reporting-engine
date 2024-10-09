package org.ihtsdo.termserver.scripting.util;

import java.util.Map;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;

public abstract class TermGenerator implements ScriptConstants {

	public static final String LITER = "liter";

	protected TermServerScript parent;
	
	protected boolean quiet;
	
	public int ensureTermsConform(Task t, Concept c, CharacteristicType charType) throws TermServerScriptException {
		return ensureTermsConform(t, c, null, charType);   //No X term 
	}
	
	public abstract int ensureTermsConform(Task t, Concept c, String X, CharacteristicType charType) throws TermServerScriptException;

	protected void report(Task task, Component component, Severity severity, ReportActionType actionType, Object... details) throws TermServerScriptException {
		if (!quiet) {
			parent.report(task, component, severity, actionType, details);
		}
	}

	public void setQuiet(boolean quiet) {
		this.quiet = quiet;
	}
	
	protected boolean removeDescription(Concept c, Description d) {
		if (d.isReleased() == null) {
			throw new IllegalStateException ("'Released' flag must be populated to safely remove description in: " + c);
		} else if (d.isReleasedSafely()) {
			d.setActive(false);
			d.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
			d.setAcceptabilityMap(null);
			return true;
		} else {
			c.getDescriptions().remove(d);
			return false;
		}
	}
	
	protected int addTerm(Task t, Concept c, Description newTerm) throws TermServerScriptException {
		return replaceTerm(t, c, null, newTerm, false);
	}

	protected int replaceTerm(Task t, Concept c, Description removing, Description replacement, boolean mergeAcceptability) throws TermServerScriptException {
		int changesMade = 0;
		boolean doReplacement = true;
		if (SnomedUtils.termAlreadyExists(c, replacement.getTerm())) {
			//But does it exist inactive?
			if (SnomedUtils.termAlreadyExists(c, replacement.getTerm(), ActiveState.INACTIVE)) {
				reactivateMatchingTerm(t, c, replacement);
				changesMade++;
			} else {
				report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Replacement term already exists: '" + replacement.getTerm() + "' inactivating unwanted term only.");
			}
			//If we're removing a PT, merge the acceptability into the existing term, also any from the replacement
			if (mergeAcceptability && removing!= null && removing.isPreferred()) {
				mergeAcceptability(t, c, removing, replacement);
			}
			doReplacement = false;
		}
		
		if (removing != null) {
			processRemovingDescription(t, c, removing);
			changesMade++;
		}
		
		if (doReplacement) {
			report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, replacement);
			replacement.setConceptId(c.getConceptId());
			c.addDescription(replacement);
			changesMade++;
		}
		
		return changesMade;
	}

	private void processRemovingDescription(Task t, Concept c, Description removing) throws TermServerScriptException {
		//Has our description been published?  Remove entirely if not
		boolean isInactivated = removeDescription(c,removing);
		String msg = (isInactivated?"Inactivated desc ":"Deleted desc ") +  removing;

		//We're only going to report this if the term really existed, silently delete null terms
		if (removing.getTerm() != null) {
			Severity severity = removing.getType().equals(DescriptionType.FSN)?Severity.MEDIUM:Severity.LOW;
			report(t, c, severity, ReportActionType.DESCRIPTION_INACTIVATED, msg);
		}
	}

	private void mergeAcceptability(Task t, Concept c, Description removing, Description replacement) throws TermServerScriptException {
		//Find the matching term that is not removing and merge that with the acceptability of removing
		boolean merged = false;
		for (Description match : c.getDescriptions(ActiveState.ACTIVE)) {
			if (!removing.equals(match) && match.getTerm().equals(replacement.getTerm())) {
				Map<String,Acceptability> mergedMap = SnomedUtils.mergeAcceptabilityMap(removing.getAcceptabilityMap(), match.getAcceptabilityMap());
				match.setAcceptabilityMap(mergedMap);
				//Now add in any improved acceptability that was coming from the replacement 
				mergedMap = SnomedUtils.mergeAcceptabilityMap(match.getAcceptabilityMap(), replacement.getAcceptabilityMap());
				match.setAcceptabilityMap(mergedMap);
				report(t, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "Merged acceptability from description being replaced and replacement into term that already exists: " + match);
				merged = true;
			}
		}
		if (!merged) {
			report(t, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to find existing term to receive accepabilty merge with " + removing);
		}
	}

	private void reactivateMatchingTerm(Task t, Concept c, Description replacement) throws TermServerScriptException {
		//Loop through the inactive terms and reactivate the one that matches the replacement
		for (Description d : c.getDescriptions(ActiveState.INACTIVE)) {
			if (d.getTerm().equals(replacement.getTerm())) {
				d.setActive(true);
				d.setCaseSignificance(replacement.getCaseSignificance());
				d.setAcceptabilityMap(replacement.getAcceptabilityMap());
				report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE, "Re-activated inactive term " + d);
				return;
			}
		}
	}

}

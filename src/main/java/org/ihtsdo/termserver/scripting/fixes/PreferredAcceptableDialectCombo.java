package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.*;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.*;

/*
 * SUBST-226 PTs that are preferred in one dialect should be acceptable in the other
 * So P/N will become P/A
*/
public class PreferredAcceptableDialectCombo extends BatchFix implements RF2Constants{
	
	String subHierarchy = "105590001 |Substance (substance)|"; 
	
	protected PreferredAcceptableDialectCombo(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		PreferredAcceptableDialectCombo fix = new PreferredAcceptableDialectCombo(null);
		try {
			fix.additionalReportColumns = "New Value, Old Value";
			fix.selfDetermining = true;
			fix.init(args);
			fix.loadProjectSnapshot(false); 
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(c, t.getBranchPath());
		int changesMade = normalizeAcceptability(t,loadedConcept);
		if (changesMade > 0) {
			try {
				saveConcept(t, loadedConcept, "");
			} catch (Exception e) {
				report(t, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		return changesMade;
	}

	private int normalizeAcceptability(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE)) {
			String before = d.toString();
			//Which dialect are we preferred in?
			if (d.hasAcceptability(Acceptability.PREFERRED, US_ENG_LANG_REFSET) && 
					d.hasAcceptability(Acceptability.NONE, GB_ENG_LANG_REFSET)) {
				d.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(AcceptabilityMode.PREFERRED_US));
				changesMade++;	
			} else if (d.hasAcceptability(Acceptability.PREFERRED, GB_ENG_LANG_REFSET) && 
					d.hasAcceptability(Acceptability.NONE, US_ENG_LANG_REFSET)) {
				d.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(AcceptabilityMode.PREFERRED_GB));
				changesMade++;	
			}
			String after = d.toString();
			report (t, c, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE, after, before);
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		info ("Identifying concepts to process");
		List<Component> processMe = new ArrayList<Component>();
		nextConcept:
		for (Concept c : gl.getConcept(subHierarchy).getDescendents(NOT_SET)) {
			if (c.getConceptId().equals("126071000")) {
				//debug("Debug here");
			}
			for (Description d : c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE)) {
				//If we only have one acceptability indicator, then it's a P/N situation
				if (d.getLangRefsetEntries(ActiveState.ACTIVE).size() == 1) {
					processMe.add(c);
					continue nextConcept;
				}
			}
		}
		info ("Identified " + processMe.size() + " concepts to process");
		return processMe;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		throw new NotImplementedException("This class self determines concepts to process");
	}
}

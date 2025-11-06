package org.ihtsdo.termserver.scripting.fixes.organism;

import java.util.*;

import org.ihtsdo.otf.rest.client.authoringservices.AuthoringServicesClient;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
CTR-19
automatically fix the case sensitivity as following:

1) Where the description is "[Taxon rank*] X" → Change/keep case sensitivity as "cI"

Please see the list of Taxon ranks at the end of this comment
2) Where the description is X (by itself or followed by any other term) → Change/keep case sensitivity to "CS"

Can you assign the associated authoring tasks to me for review and promotion (please use the Organism project in the Authoring tool for these tasks)?

If any of the concepts has description(s) that don't follow the above noted patterns, I would like a report of all of them (including Concept ID, FSN, Description) for manual review.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CTR19_CaseSensitivity extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(CTR19_CaseSensitivity.class);

	String[] textsToMatch = new String[] { "Clade","Class","Division",
			"Domain","Family","Genus","Infraclass",
			"Infraclass","Infrakingdom","Infraorder",
			"Infraorder","Kingdom","Order","Phylum",
			"Species","Subclass","Subdivision",
			"Subfamily","Subgenus","Subkingdom",
			"Suborder","Subphylum","Subspecies",
			"Superclass","Superdivision","Superfamily",
			"Superkingdom","Superorder"};
	Concept subHierarchy = ORGANISM;
	
	protected CTR19_CaseSensitivity(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws Exception {
		CTR19_CaseSensitivity fix = new CTR19_CaseSensitivity(null);
		try {
			fix.populateEditPanel = false;
			fix.selfDetermining = true;
			fix.init(args);
			//fix.testAS();
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	private void testAS() throws Exception {
		//AuthoringServicesClient testClient = new AuthoringServicesClient ("https://webhook.site/006a59c3-c39a-430b-8239-95141732190a","yummy");
		//AuthoringServicesClient testClient = new AuthoringServicesClient ("http://localhost/","p3BlUbLEq2Q0snA0pY5mDQ00");
		//AuthoringServicesClient testClient = new AuthoringServicesClient ("https://dev-authoring.ihtsdotools.org/","dev-ims-ihtsdo=p3BlUbLEq2Q0snA0pY5mDQ00");
		AuthoringServicesClient testClient = scaClient;
		try {
			testClient.updateTask("DRUG2017", "DRUG2017-259", null, "foo desc", null, null);
		} catch (Exception e) {
			LOGGER.debug("Exception " + e);
		}
		
		try {
			//testClient = new AuthoringServicesClient ("https://dev-authoring.ihtsdotools.org/","dev-ims-ihtsdo=p3BlUbLEq2Q0snA0pY5mDQ00");
			//testClient = testClient.clone();
			testClient.updateTask("DRUG2017", "DRUG2017-259", null, "bar desc", null, null);
		} catch (Exception e) {
			LOGGER.debug("Exception " + e);
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = setCaseSensitivity(task, loadedConcept);
		if (changesMade > 0) {
			try {
				updateConcept(task, loadedConcept, info);
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + e.getMessage());
			}
		}
		return changesMade;
	}

	private int setCaseSensitivity(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		
		if (c.getConceptId().equals("106783002")) {
			//LOGGER.debug("Debug Me!");
		}
		//First given that FSN is <Taxon> X, what is X?
		String X = findX(c.getFsn());
		
		nextDesc:
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			//Are we starting with a taxon, or starting with X or Reporting an Anomaly
			for (String matchText : textsToMatch) {
				if (d.getTerm().toLowerCase().startsWith(matchText.toLowerCase() + " ")) {
					if (!d.getCaseSignificance().equals(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE)) {
						d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
						report(t, c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, d);
						changesMade++;
					}
					continue nextDesc;
				}
			}
			
			if (d.getTerm().toLowerCase().startsWith(X.toLowerCase())) {
				if (!d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
					d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
					report(t, c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, d);
					changesMade++;
				}
				continue nextDesc;
			}
			
			report(t,c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, d);
		}
		return changesMade;
	}

	private String findX(String fsn) {
		String term = SnomedUtils.deconstructFSN(fsn)[0];
		for (String matchText : textsToMatch) {
			if (fsn.startsWith(matchText + " ")) {
				return term.substring(matchText.length() + 1);
			}
		}
		throw new IllegalArgumentException("Unable to find X in " + fsn);
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Concept> processMe = new ArrayList<Concept>();
		setQuiet(true);
		for (Concept thisConcept :  subHierarchy.getDescendants(NOT_SET)) {
			//Does this concept need any changes?  Modify a clone
			Concept c = thisConcept.cloneWithIds();
			
			//Does this concept start with a taxon?
			for (String matchText : textsToMatch) {
				if (c.getFsn().toLowerCase().startsWith(matchText.toLowerCase())) {
					//Are any changes required?
					if (setCaseSensitivity(null, c) > 0) {
						processMe.add(c);
					}
				}
			}
		}
		setQuiet(false);
		processMe.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<Component>(processMe);
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}
}

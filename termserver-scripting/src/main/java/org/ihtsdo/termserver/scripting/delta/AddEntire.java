package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Class to read in a spreadsheet of terms and generate new FSN and PTs 
 * - with associated Acceptability - adding the word "Entire" and
 * inactivate the existing terms.
 */
public class AddEntire extends DeltaGenerator {
	
	static Map<String, String> findReplace = new HashMap<String,String>();
	static {
		findReplace.put("Between region joint of vertebral bodies","joint of vertebral bodies between regions");
		findReplace.put("Within region joint of vertebral bodies", "joint of vertebral bodies within region");
	}
	
	static final String ENTIRE = "Entire";

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		AddEntire delta = new AddEntire();
		try {
			delta.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			delta.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			//We won't incude the project export in our timings
			delta.startTimer();
			delta.processFile();
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}
	
	protected List<Concept> processFile() throws TermServerScriptException {
		List<Concept> allConcepts = super.processFile();
		//Since our code works through all descriptions for a concept, we can remove duplicate entires of concepts, 
		Set<Concept> uniqueConcepts = new LinkedHashSet<Concept>(allConcepts);
		for (Concept thisConcept : uniqueConcepts) {
			if (!thisConcept.isActive()) {
				report (thisConcept, null, SEVERITY.MEDIUM, REPORT_ACTION_TYPE.VALIDATION_ERROR, "Concept is inactive, skipping");
				
			}
			try {
				addEntireToFSN(thisConcept);
				addEntireToPrefTerms(thisConcept);
			} catch (Exception e) {
				report (thisConcept, null, SEVERITY.CRITICAL, REPORT_ACTION_TYPE.API_ERROR, "Exception while processing: " + e.getMessage() + " : " + SnomedUtils.getStackTrace(e));
			}
		}
		return allConcepts;
	}

	private void addEntireToPrefTerms(Concept c) throws TermServerScriptException, IOException {
		List<Description> prefTerms = c.getPrefTerms();
		for (Description d : prefTerms) {
			addEntireToTerm(c, d, false);
		}
	}

	private void addEntireToFSN(Concept c) throws TermServerScriptException, IOException {
		Description fsn = c.getFSNDescription();
		addEntireToTerm(c, fsn, true);	
	}

	private void addEntireToTerm(Concept c, Description d, boolean isFSN) throws TermServerScriptException, IOException {

		//We only work with active terms
		if (d == null) {
			report (c,d,SEVERITY.HIGH, REPORT_ACTION_TYPE.VALIDATION_ERROR, "No active " + (isFSN?"FSN":"SYN") + " found for concept");
			return;
		}
		
		String[] newTermParts = new String[2];
		if (isFSN) {
			newTermParts = SnomedUtils.deconstructFSN(d.getTerm());
		} else {
			newTermParts[0] = d.getTerm();
		}
		
		//Do we in fact need to do anything?
		if (newTermParts[0].toLowerCase().contains(ENTIRE.toLowerCase())) {
			report (c,d,SEVERITY.NONE, REPORT_ACTION_TYPE.NO_CHANGE, "Term already contains 'entire'");
			return;
		}
		
		//Does this term match any of our search and replace cases?
		for (Map.Entry<String, String> entry : findReplace.entrySet()) {
			if (newTermParts[0].equals(entry.getKey())) {
				newTermParts[0] = entry.getValue();
			}
		}
		
		String newTerm = ENTIRE + " " + SnomedUtils.deCapitalize(newTermParts[0]);
		if (isFSN) {
			newTerm += " " + newTermParts[1];
		}
		replaceDescription (c,d,newTerm);
	}

	private void replaceDescription(Concept c, Description d, String newTerm) throws TermServerScriptException, IOException {
		//Do we already have this description, even inactivated?
		for (Description thisDesc : c.getDescriptions()) {
			if (thisDesc.getTerm().equals(newTerm)) {
				String msg = "Replacement term already exists in " + thisDesc.toString();
				report (c,d,SEVERITY.HIGH, REPORT_ACTION_TYPE.VALIDATION_ERROR, msg);
				return;
			}
		}
		if (!d.isActive()) {
			String msg = "Attempting to inactivate and already inactive description";
			report (c,d,SEVERITY.HIGH, REPORT_ACTION_TYPE.API_ERROR, msg);
			return;
		}
		d.setEffectiveTime(null);
		Description replacement = d.clone();

		d.setActive(false);
		report (c,d,SEVERITY.MEDIUM, REPORT_ACTION_TYPE.DESCRIPTION_CHANGE_MADE, "Inactivated Description");
		replacement.setDescriptionId(descIdGenerator.getSCTID(PartionIdentifier.DESCRIPTION));
		replacement.setTerm(newTerm);
		c.addDescription(replacement);
		report (c,replacement,SEVERITY.MEDIUM, REPORT_ACTION_TYPE.DESCRIPTION_CHANGE_MADE, "Added new Description");
		outputRF2(d);
		outputRF2(replacement);
	}

	@Override
	public String getFixName() {
		return "Add Entire";
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		Concept c = graph.getConcept(lineItems[0]);
		return c;
	}
}

package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.termserver.scripting.IdGenerator;
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
	
	enum MODE { FSN_PT, SYN };
	private MODE mode = null;
	
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
	
	protected void init (String[] args) throws IOException, TermServerScriptException {
		super.init(args);
		
		for (int x=0; x<args.length; x++) {
			if (args[x].equals("-m")) {
				mode = MODE.valueOf(args[++x]);
			}
		}
		if (mode == null) {
			String msg = "Require a mode as a -m argument - FSN_PT or SYN";
			throw new TermServerScriptException(msg);
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
				switch (mode) {
				case FSN_PT: 	addEntireToFSN(thisConcept);
								addEntireToPrefTerms(thisConcept);
								break;
				case SYN:		addEntireToSynonyms(thisConcept);
				}

			} catch (Exception e) {
				report (thisConcept, null, SEVERITY.CRITICAL, REPORT_ACTION_TYPE.API_ERROR, "Exception while processing: " + e.getMessage() + " : " + SnomedUtils.getStackTrace(e));
			}
		}
		return allConcepts;
	}

	private void addEntireToPrefTerms(Concept c) throws TermServerScriptException, IOException {
		List<Description> prefTerms = c.getSynonyms(ACCEPTABILITY.PREFERRED);
		for (Description d : prefTerms) {
			addEntireToTerm(c, d, false);
		}
	}

	private void addEntireToFSN(Concept c) throws TermServerScriptException, IOException {
		Description fsn = c.getFSNDescription();
		addEntireToTerm(c, fsn, true);	
	}
	

	private void addEntireToSynonyms(Concept c) throws TermServerScriptException, IOException {
		List<Description> synonyms = c.getSynonyms(ACCEPTABILITY.ACCEPTABLE);
		for (Description d : synonyms) {
			addEntireToTerm(c, d, false);
		}
		
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
		
		//If the entire term is case sensitive, then we don't want to decapitalize the first letter
		boolean caseSensitive = d.getCaseSignificance().equals(ENITRE_TERM_CASE_SENSITIVE);
		String newTerm = ENTIRE + " " + (caseSensitive? newTermParts[0]:SnomedUtils.deCapitalize(newTermParts[0]));
		if (isFSN) {
			newTerm += " " + newTermParts[1];
		}
		
		replaceDescription (c,d,newTerm);
	}

	private void replaceDescription(Concept c, Description d, String newTerm) throws TermServerScriptException, IOException {
		
		if (!d.isActive()) {
			String msg = "Attempting to inactivate and already inactive description";
			report (c,d,SEVERITY.HIGH, REPORT_ACTION_TYPE.API_ERROR, msg);
			return;
		}
		
		//Do we already have this description, even inactivated?
		boolean alreadyExists = false;
		for (Description thisDesc : c.getDescriptions()) {
			if (thisDesc.getTerm().equalsIgnoreCase(newTerm)) {
				String msg = "Replacement term already exists: '" + thisDesc.toString() + "', inactivating original";
				report (c,d,SEVERITY.HIGH, REPORT_ACTION_TYPE.VALIDATION_ERROR, msg);
				alreadyExists = true;;
			}
		}

		if (!alreadyExists) {
			String newSCTID = descIdGenerator.getSCTID(PartionIdentifier.DESCRIPTION);
			Description replacement = d.clone(newSCTID);
			replacement.setTerm(newTerm);
			c.addDescription(replacement);
			report (c,replacement,SEVERITY.MEDIUM, REPORT_ACTION_TYPE.DESCRIPTION_CHANGE_MADE, "Added new Description");
			outputRF2(replacement);
		}
		d.setActive(false);
		report (c,d,SEVERITY.MEDIUM, REPORT_ACTION_TYPE.DESCRIPTION_CHANGE_MADE, "Inactivated Description");
		outputRF2(d);
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

package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
/**
 *Used from SE Translation.  Namespace 1000052
 *Updated for Belgium.  Namespace 1000172
 */
public class GenerateTranslation extends DeltaGenerator {
	
	KnownTranslations thisTranslation = KnownTranslations.BELGIUM;
	enum KnownTranslations { SWEDEN, BELGIUM };
	Map<String, String> langToRefsetMap = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		GenerateTranslation delta = new GenerateTranslation();
		try {
			delta.config();
			delta.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			delta.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			delta.startTimer();
			delta.processFile();
			delta.flushFiles(false);
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}

	private void config() {
		inputFileHasHeaderRow = true;
		inputFileDelimiter = TSV_FIELD_DELIMITER;
		isExtension = true; //Ensures the correct partition identifier is used.
		switch (thisTranslation) {
			case SWEDEN:
				nameSpace = "1000052";
				languageCode="sv";
				edition="SE1000052";
				langToRefsetMap.put(languageCode, "46011000052107");
				moduleId = "45991000052106";
				break;
			case BELGIUM:
				nameSpace = "1000172";
				languageCode="en";
				edition="BE1000172";
				langToRefsetMap.put("fr", "21000172104");
				langToRefsetMap.put("nl", "31000172101");
				moduleId = "11000172109";
				break;
		}
	}

	protected List<Component> processFile() throws TermServerScriptException {
		List<Component> newTranslationsLoaded = super.processFile();
		Set<Component> newTranslations = new HashSet<Component>(newTranslationsLoaded);
		if (newTranslationsLoaded.size() != newTranslations.size()) {
			throw new TermServerScriptException("Duplicate concepts found in file");
		}
		
		for (Component thisConcept : newTranslations) {
			try {
				outputRF2((Concept)thisConcept);
			} catch (TermServerScriptException e) {
				//Only catching TermServerScript exception because we want unchecked RuntimeExceptions eg
				//NullPointer and TotalCatastrophicFailure to stop processing
				report ((Concept)thisConcept, null, Severity.CRITICAL, ReportActionType.API_ERROR, "Exception while processing: " + e.getMessage() + " : " + SnomedUtils.getStackTrace(e));
			}
		}
		return null;
	}


	/*private void promoteTerm(Description d) {
		//Do we already have a langrefset entry for this dialect?
		boolean langRefSetEntryCorrect = false;
		for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
			if (l.getRefsetId().equals(langRefsetId)) {
				if (!l.getAcceptabilityId().equals(PREFERRED_TERM)) {
					l.setAcceptabilityId(PREFERRED_TERM);
					l.setDirty();
					langRefSetEntryCorrect = true;
				}
			}
		}
		if (!langRefSetEntryCorrect) {
			addLangRefsetEntry(d);
		}
	}*/
	
	private void addTranslation(Concept concept, String expectedUSTerm, Description newDescription) throws TermServerScriptException {
		if (!concept.isActive()) {
			String reason = concept.getInactivationIndicator() != null ? concept.getInactivationIndicator().toString() : "Reason unknown.";
			String assoc = getPrettyHistoricalAssociation(concept);
			report (concept, null, Severity.MEDIUM, ReportActionType.VALIDATION_ERROR, "Concept is inactive, skipping. " + reason + ", " + assoc);
			return;
		}
		
		if (expectedUSTerm != null) {
			//Check that the current preferred term matches what the translation file thinks it is.
			//We're no longer receiving the current US term
			Description usPrefTerm = getUsPrefTerm(concept);
			if (!usPrefTerm.getTerm().equals(expectedUSTerm)) {
				report (concept, usPrefTerm, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Current term is not what was translated.  Translation file expected: " + expectedUSTerm);
				return;
			}
		}
		
		//Do we already have this term?  Just add the langrefset entry if so.
		/*if (currentState.hasTerm(newState.getNewPreferredTerm())) {
			Description d = currentState.findTerm(newState.getNewPreferredTerm());
			promoteTerm (d);
			report (currentState, d, SEVERITY.HIGH, REPORT_ACTION_TYPE.DESCRIPTION_CHANGE_MADE, "Promoted langrefset on existing term: " + d);
			outputRF2(d);
		} else {*/
		
		String msg = "Created new description";
		Severity severity = Severity.LOW;
		
		//Do we already have this term? 
		Description duplicate = concept.findTerm(newDescription.getTerm(), newDescription.getLang());
		String descAccept = "";
		if (duplicate != null) {
			//We won't create a duplicate, but any preferred term will take priority
			msg = "Attempt to duplicate description: '" + newDescription.getTerm() + "'";
			if (!duplicate.isPreferred() && newDescription.isPreferred()) {
				SnomedUtils.mergeLangRefsetEntries(newDescription, duplicate);
				msg += " - first term promoted to Preferred";
			} else {
				msg += " - ignoring duplicate acceptable term";
			}
			severity = Severity.HIGH;
		} else {
			descAccept = newDescription.isPreferred() ? "P":"A";
			
			//If not rejecting completely due to being an exact duplicate,
			//are we trying to add a PT when we already have one in that language?
			if (newDescription.isPreferred()) {
				String refsetId = langToRefsetMap.get(newDescription.getLang());
				Description existingPT = concept.getPreferredSynonym(refsetId);
				if (existingPT != null) {
					msg = "Existing PT existing for " + newDescription.getLang() + ", demoting to acceptable.";
					demoteLangRefsetEntry(newDescription);
					severity = Severity.HIGH;
				}
			}
		}
		
		concept.addDescription(newDescription);
		
		String cs = " (" + newDescription.getLang() + " " +  descAccept + " - "+ SnomedUtils.translateCaseSignificanceFromSctId(newDescription.getCaseSignificance()) + ")";
		report (concept, newDescription, severity, ReportActionType.DESCRIPTION_ADDED, msg +  cs + ": " + newDescription.getTerm());
	}

	private Description getUsPrefTerm(Concept currentState) throws TermServerScriptException {
		List<Description> terms = currentState.getDescriptions(US_ENG_LANG_REFSET, Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE);
		if (terms.size() != 1) {
			throw new TermServerScriptException("Expected to find 1 x US preferred term, found " + terms.size());
		}
		return terms.get(0);
	}

	@Override

	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
		switch (thisTranslation) {
			case SWEDEN:  return LoadSELine(lineItems);
			case BELGIUM: return LoadBELine(lineItems);
		}
		return null;
	}

	//SE File format: Concept_Id	Swedish_Term	Case_Significance
	private Concept LoadSELine(String[] lineItems) throws TermServerScriptException {
		if (lineItems.length == 5) {
			Description d = createDescription (lineItems[0],  //conceptId
					lineItems[3], //swedish term
					languageCode,
					SnomedUtils.translateCaseSignificanceToSctId(lineItems[4]), //case significance
					SCTID_PREFERRED_TERM
					);
			Concept concept = gl.getConcept(lineItems[0]);
			addTranslation(concept, lineItems[1], d); 
			return concept;
		}
		return null;
	}
	
	//BE File format: ConceptID	Source term	Target term	Language	Type	Comment
	private Concept LoadBELine(String[] lineItems) throws TermServerScriptException {
		if (lineItems.length >= 3) {
			String acceptabilityId = SCTID_ACCEPTABLE_TERM;
			if (lineItems[4].contains("Preferred")) {
				acceptabilityId = SCTID_PREFERRED_TERM;
			}
			String lang = lineItems[3].substring(0, 2);
			String term = lineItems[2].trim().replace("  ", " ");
			Description d = createDescription (lineItems[0],  //conceptId
												term,
												lang,
												calculateBEcaseSignificanceSCTID(term),
												acceptabilityId
												);
			Concept concept = gl.getConcept(lineItems[0]);
			addTranslation(concept, lineItems[1], d);
			return concept;
		}
		return null;
	}
	
	private String calculateBEcaseSignificanceSCTID(String term) {
		//BE Terms are in general lower case, so any term that starts with a capital letter
		//can be considered CS.   Otherwise if it is case sensitive then cI
		String firstLetter = term.substring(0, 1);
		if (!firstLetter.equals(firstLetter.toLowerCase())) {
			return SCTID_ENTIRE_TERM_CASE_SENSITIVE;
		} else if (SnomedUtils.isCaseSensitive(term)) {
			return SCTID_ONLY_INITIAL_CHAR_CASE_INSENSITIVE;
		}
		return SCTID_ENTIRE_TERM_CASE_INSENSITIVE;
	}

	private Description createDescription(String conceptId, String term, String lang, String caseSensitityId, String acceptabilityId) throws TermServerScriptException {
		Description d = new Description();
		d.setDescriptionId(descIdGenerator.getSCTID());
		d.setConceptId(conceptId);
		d.setActive(true);
		d.setEffectiveTime(null);
		d.setLang(lang);
		d.setTerm(term);
		d.setType(DescriptionType.SYNONYM);
		d.setCaseSignificance(caseSensitityId);
		d.setModuleId(moduleId);
		d.setDirty();
		addLangRefsetEntry(d, acceptabilityId);
		return d;
	}

	private void addLangRefsetEntry(Description d, String acceptabilitySCTID) {
		LangRefsetEntry l = new LangRefsetEntry();
		l.setId(UUID.randomUUID().toString());
		l.setRefsetId(langToRefsetMap.get(d.getLang()));
		l.setActive(true);
		l.setEffectiveTime(null);
		l.setModuleId(moduleId);
		l.setAcceptabilityId(acceptabilitySCTID);
		l.setReferencedComponentId(d.getDescriptionId());
		l.setDirty();
		d.getLangRefsetEntries().add(l);
	}
	
	private void demoteLangRefsetEntry(Description d) {
		//find the language refset entry for this description which is expected to be PT
		//and modify to be acceptable
		int changesMade = 0;
		for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE, langToRefsetMap.get(d.getLang()))) {
			if (l.getAcceptabilityId().equals(SCTID_PREFERRED_TERM)) {
				l.setAcceptabilityId(SCTID_ACCEPTABLE_TERM);
				changesMade++;
			}
		}
		if (changesMade != 1) {
			System.out.println ("*** Warning " + changesMade + " lang refset changes made to " + d);
		}
	}

}

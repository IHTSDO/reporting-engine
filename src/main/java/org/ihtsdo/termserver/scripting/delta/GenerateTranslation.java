package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ConceptChange;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
/**
 * Last used from SE Translation.  Namespace 1000052
 */
public class GenerateTranslation extends DeltaGenerator {
	
	public String moduleId="";
	public String langRefsetId="";
	public String nameSpace="1000052";
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		GenerateTranslation delta = new GenerateTranslation();
		try {
			delta.useAuthenticatedCookie = true; //ManagedService uses different authentication.  
			delta.inputFileHasHeaderRow = true;
			delta.tsRoot="MAIN/2017-01-31/SNOMEDCT-SE/";
			delta.edition="SE1000052";
			delta.languageCode="sv";
			delta.inputFileDelimiter = TSV_FIELD_DELIMITER;
			delta.nameSpace = "1000052";
			delta.isExtension = true; //Ensures the correct partition identifier is used.
			SnowOwlClient.supportsIncludeUnpublished = false;   //This code not yet available in MS
			delta.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			delta.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			//We won't incude the project export in our timings
			//InputStream is = new FileInputStream(delta.usLangRefsetFilePath);
			//graph.loadLanguageFile(is);
			delta.startTimer();
			delta.processFile();
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
			if (delta.descIdGenerator != null) {
				println(delta.descIdGenerator.finish());
			}
		}
	}

	protected List<Concept> processFile() throws TermServerScriptException {
		List<Concept> newTranslationsLoaded = super.processFile();
		Set<Concept> newTranslations = new HashSet<Concept>(newTranslationsLoaded);
		
		if (newTranslationsLoaded.size() != newTranslations.size()) {
			throw new TermServerScriptException("Duplicate concepts found in file");
		}
		for (Concept thisConcept : newTranslations) {
			Concept currentState = graph.getConcept(thisConcept.getConceptId());
			ConceptChange newState = (ConceptChange) thisConcept;
			if (!currentState.isActive()) {
				report (thisConcept, null, Severity.MEDIUM, ReportActionType.VALIDATION_ERROR, "Concept is inactive, skipping");
			}
			try {
				generateTranslation(currentState, newState);
			} catch (TermServerScriptException e) {
				//Only catching TermServerScript exception because we want unchecked RuntimeExceptions eg
				//NullPointer and TotalCatastrophicFailure to stop processing
				report (thisConcept, null, Severity.CRITICAL, ReportActionType.API_ERROR, "Exception while processing: " + e.getMessage() + " : " + SnomedUtils.getStackTrace(e));
			}
		}
		return null;
	}

	private void generateTranslation(Concept currentState,
			ConceptChange newState) throws TermServerScriptException {
		//Check that the concept is currently active
		if (!currentState.isActive()) {
			report (currentState, null, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept is inactive, skipping");
			return;
		}
		
		//Check that the current preferred term matches what the translation file thinks it is.
		//We're no longer receiving the current US term
		/*Description usPrefTerm = getUsPrefTerm(currentState);
		if (!usPrefTerm.getTerm().equals(newState.getCurrentTerm())) {
			report (currentState, usPrefTerm, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Current term is not what was translated: " + newState.getCurrentTerm());
		}*/
		
		//Do we already have this term?  Just add the langrefset entry if so.
		/*if (currentState.hasTerm(newState.getNewPreferredTerm())) {
			Description d = currentState.findTerm(newState.getNewPreferredTerm());
			promoteTerm (d);
			report (currentState, d, SEVERITY.HIGH, REPORT_ACTION_TYPE.DESCRIPTION_CHANGE_MADE, "Promoted langrefset on existing term: " + d);
			outputRF2(d);
		} else {*/
		
		String msg = "Created new description: ";
		Severity severity = Severity.LOW;
		//Do we already have this term?  Add a warning if so.
		if (currentState.hasTerm(newState.getNewTerm())) {
			severity = Severity.HIGH;
			msg = "Created duplicate new description: ";
		}
		
		//Create a new Description to attach to the concept
		Description d = createTranslatedDescription(newState);
		String cs = ", with case significance " + SnomedUtils.translateCaseSignificanceFromSctId(d.getCaseSignificance());
		report (currentState, d, severity, ReportActionType.DESCRIPTION_CHANGE_MADE, msg + d + cs);
		outputRF2(d);
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

	private Description createTranslatedDescription(ConceptChange newState) throws TermServerScriptException {
		Description d = new Description();
		d.setDescriptionId(descIdGenerator.getSCTID());
		d.setConceptId(newState.getConceptId());
		d.setActive(true);
		d.setEffectiveTime(null);
		d.setLang(languageCode);
		d.setTerm(newState.getNewTerm());
		d.setType(DescriptionType.SYNONYM);
		d.setCaseSignificance(newState.getCaseSensitivitySctId());
		d.setModuleId(moduleId);
		d.setDirty();
		
		addLangRefsetEntry(d);
		return d;
	}

	private void addLangRefsetEntry(Description d) {
		LangRefsetEntry l = new LangRefsetEntry();
		l.setId(UUID.randomUUID().toString());
		l.setRefsetId(langRefsetId);
		l.setActive(true);
		l.setEffectiveTime(null);
		l.setModuleId(moduleId);
		l.setAcceptabilityId(SCTID_PREFERRED_TERM);
		l.setReferencedComponentId(d.getDescriptionId());
		l.setDirty();
		d.getLangRefsetEntries().add(l);
	}

/*	private Description getUsPrefTerm(Concept currentState) throws TermServerScriptException {
		List<Description> terms = currentState.getDescriptions(US_ENG_LANG_REFSET, Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE);
		if (terms.size() != 1) {
			throw new TermServerScriptException("Expected to find 1 x US preferred term, found " + terms.size());
		}
		return terms.get(0);
	}*/

	@Override
	//SE File format: Concept_Id	Swedish_Term	Case_Significance
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		if (lineItems.length == 3) {
			ConceptChange c = new ConceptChange(lineItems[0]);
			//c.setCurrentTerm(lineItems[1]);
			c.setNewTerm(lineItems[1]);
			//String caseSignificanceSctId = SnomedUtils.translateCaseSignificanceToSctId(lineItems[5]);
			//c.setCaseSignificanceSctId(caseSignificanceSctId);
			c.setCaseSignificanceSctId(lineItems[2]);
			return c;
		}
		return null;
	}

}

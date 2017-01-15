package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
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
			delta.tsRoot="MAIN/2016-07-31/SNOMEDCT-SE/";
			delta.edition="SE1000052";
			delta.languageCode="sv";
			delta.inputFileDelimiter = TSV_FIELD_DELIMITER;
			delta.isExtension = true; //Ensures the correct partition identifier is used.
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
			if (delta.idGenerator != null) {
				println(delta.idGenerator.finish());
			}
		}
	}
	
	protected void init (String[] args) throws IOException, TermServerScriptException {
		super.init(args);
		idGenerator.setNamespace("1000052");
		idGenerator.isExtension(true);
		
		for (int x=0; x<args.length; x++) {
			if (args[x].equals("-m")) {
				moduleId = args[++x];
			}
			if (args[x].equals("-l")) {
				langRefsetId = args[++x];
			}
		}
		
		print ("Targetting which namespace? [" + nameSpace + "]: ");
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			nameSpace = response;
		}
		
		print ("Targetting which moduleId? [" + moduleId + "]: ");
		response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			moduleId = response;
		}
		
		print ("Targetting which language code? [" + languageCode + "]: ");
		response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			languageCode = response;
		}
		
		print ("Targetting which language refset? [" + langRefsetId + "]: ");
		response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			langRefsetId = response;
		}
		
		print ("What's the Termserver root? [" + tsRoot + "]: ");
		response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			tsRoot = response;
		}
		
		print ("What's the Edition? [" + edition + "]: ");
		response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			edition = response;
		}
		
		/*print ("What's the US Langrefset file? [" + usLangRefsetFilePath + "]: ");
		response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			usLangRefsetFilePath = response;
		}*/
		
		if (moduleId.isEmpty() || langRefsetId.isEmpty()) {
			String msg = "Require both moduleId and langRefset Id to be specified (-m -l parameters)";
			throw new TermServerScriptException(msg);
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
				report (thisConcept, null, SEVERITY.MEDIUM, REPORT_ACTION_TYPE.VALIDATION_ERROR, "Concept is inactive, skipping");
			}
			try {
				generateTranslation(currentState, newState);
			} catch (TermServerScriptException e) {
				//Only catching TermServerScript exception because we want unchecked RuntimeExceptions eg
				//NullPointer and TotalCatastrophicFailure to stop processing
				report (thisConcept, null, SEVERITY.CRITICAL, REPORT_ACTION_TYPE.API_ERROR, "Exception while processing: " + e.getMessage() + " : " + SnomedUtils.getStackTrace(e));
			}
		}
		return null;
	}

	private void generateTranslation(Concept currentState,
			ConceptChange newState) throws TermServerScriptException {
		//Check that the concept is currently active
		if (!currentState.isActive()) {
			report (currentState, null, SEVERITY.HIGH, REPORT_ACTION_TYPE.VALIDATION_ERROR, "Concept is inactive, skipping");
			return;
		}
		
		//Check that the current preferred term matches what the translation file thinks it is.
		Description usPrefTerm = getUsPrefTerm(currentState);
		if (!usPrefTerm.getTerm().equals(newState.getExpectedCurrentPreferredTerm())) {
			report (currentState, usPrefTerm, SEVERITY.HIGH, REPORT_ACTION_TYPE.VALIDATION_ERROR, "Current term is not what was translated: " + newState.getExpectedCurrentPreferredTerm());
		}
		
		//Do we already have this term?  Just add the langrefset entry if so.
		/*if (currentState.hasTerm(newState.getNewPreferredTerm())) {
			Description d = currentState.findTerm(newState.getNewPreferredTerm());
			promoteTerm (d);
			report (currentState, d, SEVERITY.HIGH, REPORT_ACTION_TYPE.DESCRIPTION_CHANGE_MADE, "Promoted langrefset on existing term: " + d);
			outputRF2(d);
		} else {*/
		
		String msg = "Created new description: ";
		SEVERITY severity = SEVERITY.LOW;
		//Do we already have this term?  Add a warning if so.
		if (currentState.hasTerm(newState.getNewPreferredTerm())) {
			severity = SEVERITY.HIGH;
			msg = "Created duplicate new description: ";
		}
		
		//Create a new Description to attach to the concept
		Description d = createTranslatedDescription(newState);
		String cs = ", with case significance " + SnomedUtils.translateCaseSignificanceFromSctId(d.getCaseSignificance());
		report (currentState, d, severity, REPORT_ACTION_TYPE.DESCRIPTION_CHANGE_MADE, msg + d + cs);
		outputRF2(d);
	}

	private void promoteTerm(Description d) {
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
	}

	private Description createTranslatedDescription(ConceptChange newState) throws TermServerScriptException {
		Description d = new Description();
		d.setDescriptionId(idGenerator.getSCTID(PartionIdentifier.DESCRIPTION));
		d.setConceptId(newState.getConceptId());
		d.setActive(true);
		d.setEffectiveTime(null);
		d.setLang(languageCode);
		d.setTerm(newState.getNewPreferredTerm());
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
		l.setAcceptabilityId(PREFERRED_TERM);
		l.setReferencedComponentId(d.getDescriptionId());
		l.setDirty();
		d.getLangRefsetEntries().add(l);
	}

	private Description getUsPrefTerm(Concept currentState) throws TermServerScriptException {
		List<Description> terms = currentState.getDescriptions(US_ENG_LANG_REFSET, Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE);
		if (terms.size() != 1) {
			throw new TermServerScriptException("Expected to find 1 x US preferred term, found " + terms.size());
		}
		return terms.get(0);
	}

	@Override
	//SE File format: Concept_Id	English	Case_Significant	Fully_Specified_Name	Swedish	Case_Significant
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		if (lineItems.length > 3) {
			ConceptChange c = new ConceptChange(lineItems[0]);
			c.setExpectedCurrentPreferredTerm(lineItems[1]);
			c.setNewPreferredTerm(lineItems[4]);
			String caseSignificanceSctId = SnomedUtils.translateCaseSignificanceToSctId(lineItems[5]);
			c.setCaseSignificanceSctId(caseSignificanceSctId);
			return c;
		}
		return null;
	}

}

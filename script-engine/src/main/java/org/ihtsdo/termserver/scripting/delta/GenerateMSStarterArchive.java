package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
/**
 *Created for Belgium.  Namespace 1000172
 */
public class GenerateMSStarterArchive extends DeltaGenerator {
	
	KnownExtensions thisExtension = KnownExtensions.BELGIUM;
	enum KnownExtensions { SWEDEN, BELGIUM };
	Map<String, String> langToRefsetMap = new HashMap<>();
	String langRefset;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		GenerateMSStarterArchive delta = new GenerateMSStarterArchive();
		try {
			delta.config(KnownExtensions.BELGIUM);
			delta.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			delta.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			delta.startTimer();
			List<Concept> newConcepts = delta.generateStarter();
			delta.outputRF2(newConcepts);
			delta.flushFiles(false, true); //Need to flush files before zipping
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}

	private void config(KnownExtensions country) {
		inputFileHasHeaderRow = true;
		inputFileDelimiter = TSV_FIELD_DELIMITER;
		isExtension = true; //Ensures the correct partition identifier is used.
		switch (country) {
			case SWEDEN:
				nameSpace = "1000052";
				languageCode="sv";
				edition="SE1000052";
				langToRefsetMap.put(languageCode, "46011000052107");
				moduleId = "45991000052106";
				break;
			case BELGIUM:
				nameSpace = "1000172";
				languageCode="en";  //We'll be creating FSNs and PTs using only US-English
				edition="BE1000172";
				langToRefsetMap.put("en", "900000000000509007");
				langToRefsetMap.put("fr", "21000172104");
				langToRefsetMap.put("nl", "31000172101");
				moduleId = "11000172109";
				break;
		}
	}

	protected List<Concept> generateStarter() throws TermServerScriptException {
		List<Concept> newConcepts = new ArrayList<Concept>();
		
		//Module Concept
		Concept moduleConcept = new Concept("11000172109");
		
		//TODO We also need to have a description appear in each new language refset so the TS can become aware of it.
		

		return newConcepts;
	}
	
	private void outputRF2(List<Concept> newConcepts) throws TermServerScriptException {
		for (Concept thisConcept : newConcepts) {
			try {
				outputRF2(thisConcept);
			} catch (TermServerScriptException e) {
				report (thisConcept, null, Severity.CRITICAL, ReportActionType.API_ERROR, "Exception while processing: " + e.getMessage() + " : " + SnomedUtils.getStackTrace(e));
			}
		}
	}
	
	private Description createDescription(String conceptId, String term, String lang, CaseSignificance caseSignificance, String acceptabilityId) throws TermServerScriptException {
		Description d = new Description();
		d.setDescriptionId(descIdGenerator.getSCTID());
		d.setConceptId(conceptId);
		d.setActive(true);
		d.setEffectiveTime(null);
		d.setLang(lang);
		d.setTerm(term);
		d.setType(DescriptionType.SYNONYM);
		d.setCaseSignificance(caseSignificance);
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

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}

}

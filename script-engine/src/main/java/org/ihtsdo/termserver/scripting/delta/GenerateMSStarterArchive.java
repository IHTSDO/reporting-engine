package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;

import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class GenerateMSStarterArchive extends DeltaGenerator {

	Map<String, String> langToRefsetMap = new HashMap<>();

	public static void main(String[] args) throws TermServerScriptException {
		GenerateMSStarterArchive delta = new GenerateMSStarterArchive();
		try {
			delta.config();
			delta.init(args);
			delta.postInit(GFOLDER_ADHOC_UPDATES);
			List<Concept> newConcepts = delta.generateStarter();
			delta.outputRF2(newConcepts);
			delta.flushFiles(false); //Need to flush files before zipping
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}

	private void config() {
		isExtension = true; //Ensures the correct partition identifier is used.
		nameSpace = "1002000";
		languageCode="en";
		edition="NUVA1002000";
		langToRefsetMap.put(languageCode, US_ENG_LANG_REFSET);
		langToRefsetMap.put("fr", "21000241105"); //| Common French language reference set (foundation metadata concept) |
	}

	protected List<Concept> generateStarter() throws TermServerScriptException {
		List<Concept> newConcepts = new ArrayList<>();
		
		//Module Concept
		Concept moduleConcept = Concept.withDefaults(conIdGenerator.getSCTID());
		moduleConcept.setModuleId(moduleConcept.getId());
		targetModuleId = moduleConcept.getId();
		addFsnAndPt(moduleConcept, "NUVA Extension Module (core metadata concept)");
		newConcepts.add(moduleConcept);

		Concept parent = gl.getConcept("1201891009 |SNOMED CT Community content module (core metadata concept)|");
		Relationship parentRel = new Relationship(moduleConcept, IS_A, parent, UNGROUPED);
		parentRel.setModuleId(targetModuleId);
		moduleConcept.addRelationship(parentRel);

		return newConcepts;
	}

	private void outputRF2(List<Concept> newConcepts) throws TermServerScriptException {
		for (Concept thisConcept : newConcepts) {
			try {
				outputRF2(thisConcept);
			} catch (TermServerScriptException e) {
				report(thisConcept, null, Severity.CRITICAL, ReportActionType.API_ERROR, "Exception while processing: " + e.getMessage() + " : " + SnomedUtils.getStackTrace(e));
			}
		}
	}

	private void addFsnAndPt(Concept c, String term) throws TermServerScriptException {
		addDescription(c, term, DescriptionType.FSN, languageCode);

		String ptTerm = SnomedUtilsBase.deconstructFSN(term)[0];
		addDescription(c, ptTerm, DescriptionType.SYNONYM, languageCode);
	}
	
	private Description addDescription(Concept c, String term, DescriptionType type, String lang) throws TermServerScriptException {
		Description d = Description.withDefaults(term, type, Acceptability.PREFERRED);
		c.addDescription(d);
		d.setLang(lang);
		d.setDescriptionId(descIdGenerator.getSCTID());
		d.setConceptId(c.getConceptId());
		d.setModuleId(targetModuleId);
		//Sort out the default langrefsets
		List<LangRefsetEntry> langRefsetEntries = new ArrayList<>(d.getLangRefsetEntries());
		for (LangRefsetEntry l : langRefsetEntries) {
			if (l.getRefsetId().equals(GB_ENG_LANG_REFSET)) {
				d.removeLangRefsetEntry(l);
			} else {
				l.setModuleId(targetModuleId);
				l.setReferencedComponentId(d.getId());
			}
		}
		return d;
	}



}

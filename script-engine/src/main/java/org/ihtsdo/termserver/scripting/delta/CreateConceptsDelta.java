package org.ihtsdo.termserver.scripting.delta;

import java.io.IOException;
import java.util.UUID;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateConceptsDelta extends DeltaGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(CreateConceptsDelta.class);

	DefinitionStatus defStatus = DefinitionStatus.FULLY_DEFINED;

	String[] fsns = new String[] { "NUVA code identifier (core metadata concept)", "Valence (disposition)" };
	
	String [] parents = new String[] {"900000000000453004 |Identifier scheme|", "726711005 |Disposition|"};
	
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		CreateConceptsDelta delta = new CreateConceptsDelta();
		try {
			delta.inputFileHasHeaderRow = true;
			delta.edition="INT";
			delta.languageCode="en";
			delta.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			delta.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			delta.startTimer();
			delta.createConcepts();
			delta.createOutputArchive();
		} finally {
			delta.finish();
		}
	}

	private void createConcepts() throws TermServerScriptException {
		for (int x = 0; x < fsns.length; x++) {
			Concept c = new Concept(conIdGenerator.getSCTID());
			addDescriptions(c, fsns[x]);
			addRelationships(c, parents[x]);
		}
	}

	private void addDescriptions(Concept c, String string) {
		// TODO Auto-generated method stub
		
	}

	private void addRelationships(Concept c, String string) {
		// TODO Auto-generated method stub
		
	}

	private void addLangRefsetEntry(Description d, String sctIdAcceptability) {
		for (String langRefsetId : targetLangRefsetIds) {
			LangRefsetEntry l = new LangRefsetEntry();
			l.setId(UUID.randomUUID().toString());
			l.setRefsetId(langRefsetId);
			l.setActive(true);
			l.setEffectiveTime(null);
			l.setModuleId(targetModuleId);
			l.setAcceptabilityId(sctIdAcceptability);
			l.setReferencedComponentId(d.getDescriptionId());
			l.setDirty();
			d.getLangRefsetEntries().add(l);
		}
	}


	private void addFsnAndPT(Concept concept, String term) throws TermServerScriptException {
		Description pt = new Description(descIdGenerator.getSCTID());
		pt.setTerm(term);
		pt.setActive(true);
		pt.setLang(languageCode);
		pt.setConceptId(concept.getConceptId());
		pt.setType(DescriptionType.SYNONYM);
		pt.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
		pt.setModuleId(targetModuleId);
		pt.setDirty();
		addLangRefsetEntry(pt, SCTID_PREFERRED_TERM);
		concept.addDescription(pt);
		Description fsn = pt.clone(descIdGenerator.getSCTID());
		fsn.setType(DescriptionType.FSN);
		fsn.setTerm(term + " (observable entity)");
		concept.setFsn(fsn.getTerm());
		concept.addDescription(fsn);
	}
	
	private void addSyn(Concept concept, String prefix, String data) throws TermServerScriptException {
		Description syn = new Description(descIdGenerator.getSCTID());
		syn.setTerm(prefix + data);
		syn.setActive(true);
		syn.setModuleId(targetModuleId);
		syn.setLang(languageCode);
		syn.setType(DescriptionType.SYNONYM);
		syn.setConceptId(concept.getConceptId());
		syn.setDirty();
		syn.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
		addLangRefsetEntry(syn, SCTID_ACCEPTABLE_TERM);
		concept.addDescription(syn);
	}
	

}

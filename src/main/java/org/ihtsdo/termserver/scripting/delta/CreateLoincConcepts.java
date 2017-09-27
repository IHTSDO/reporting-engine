package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class CreateLoincConcepts extends DeltaGenerator {
	
	DefinitionStatus defStatus = DefinitionStatus.FULLY_DEFINED;
	Map<LoincElement, Concept> loincAttributes;
	
	enum LoincElement { Component(0),
						PropertyType(1),
						TimeAspect(2),
						DirectSite(3),
						InheresIn(4),
						ScaleType(5),
						LOINC_FSN(6),
						LOINC_Unique_ID (7),
						Correlation_ID(8);
		private int idx;
		LoincElement(int idx) {
			this.idx = idx;
		}

		public int geIdx() {
			return idx;
		}
	}
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		CreateLoincConcepts delta = new CreateLoincConcepts();
		try {
			delta.inputFileHasHeaderRow = true;
			delta.edition="INT";
			delta.languageCode="en";
			delta.inputFileDelimiter = TSV_FIELD_DELIMITER;
			delta.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			delta.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			delta.initMap();
			delta.startTimer();
			delta.processFile();
			delta.closeFiles(); //Need to flush files before zipping
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}
	
	private void initMap() throws TermServerScriptException {
		loincAttributes = new HashMap<>();
		loincAttributes.put(LoincElement.Component, gl.getConcept(246093002L));
		loincAttributes.put(LoincElement.PropertyType, gl.getConcept(704318007L));
		loincAttributes.put(LoincElement.TimeAspect, gl.getConcept(370134009L));
		loincAttributes.put(LoincElement.DirectSite, gl.getConcept(704327008L));
		loincAttributes.put(LoincElement.InheresIn, gl.getConcept(704319004L));
		loincAttributes.put(LoincElement.ScaleType, gl.getConcept(370132008L));
	}

	protected List<Concept> processFile() throws TermServerScriptException {
		List<Concept> newConcepts = super.processFile();
		print ("Outputting RF2 files");
		for (Concept thisConcept : newConcepts) {
			outputRF2(thisConcept);
		}
		println("\nOutputting complete");
		return null;
	}

	private void addLangRefsetEntry(Description d, String sctIdAcceptability) {
		for (String langRefsetId : langRefsetIds) {
			LangRefsetEntry l = new LangRefsetEntry();
			l.setId(UUID.randomUUID().toString());
			l.setRefsetId(langRefsetId);
			l.setActive(true);
			l.setEffectiveTime(null);
			l.setModuleId(moduleId);
			l.setAcceptabilityId(sctIdAcceptability);
			l.setReferencedComponentId(d.getDescriptionId());
			l.setDirty();
			d.getLangRefsetEntries().add(l);
		}
	}

	@Override
	//Component	PropertyType	TimeAspect	DirectSite	InheresIn	ScaleType	LOINC_FSN	LOINC_Unique_ID	Correlation_ID
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		if (lineItems.length > 3) {
			Concept concept = new Concept(conIdGenerator.getSCTID());
			concept.setModuleId(moduleId);
			concept.setDirty();
			addFsnAndPT(concept, lineItems[LoincElement.LOINC_FSN.geIdx()]);
			addSyn(concept, "Correlation ID:", lineItems[LoincElement.Correlation_ID.geIdx()]);
			addSyn(concept, "LOINC Unique ID:", lineItems[LoincElement.LOINC_Unique_ID.geIdx()]);
			addAttributes(concept, lineItems);
			concept.setDefinitionStatus(defStatus);
			return concept;
		}
		return null;
	}

	private void addFsnAndPT(Concept concept, String term) throws TermServerScriptException {
		Description pt = new Description(descIdGenerator.getSCTID());
		pt.setTerm(term);
		pt.setActive(true);
		pt.setLang(languageCode);
		pt.setConceptId(concept.getConceptId());
		pt.setType(DescriptionType.SYNONYM);
		pt.setCaseSignificance(SCTID_ENTIRE_TERM_CASE_SENSITIVE);
		pt.setModuleId(moduleId);
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
		syn.setModuleId(moduleId);
		syn.setLang(languageCode);
		syn.setType(DescriptionType.SYNONYM);
		syn.setConceptId(concept.getConceptId());
		syn.setCaseSignificance(SCTID_ENTIRE_TERM_CASE_SENSITIVE);
		addLangRefsetEntry(syn, SCTID_ACCEPTABLE_TERM);
		concept.addDescription(syn);
	}
	

	private void addAttributes(Concept concept, String[] lineItems) throws TermServerScriptException {
		//Work through the attribute map and create stated relationships of the appropriate type
		for (Map.Entry<LoincElement, Concept> entry : loincAttributes.entrySet()) {
			Concept source = concept;
			Concept type = entry.getValue();
			String targetSctid = lineItems[entry.getKey().geIdx()];
			Concept target = gl.getConcept(targetSctid);
			Relationship rel = new Relationship (source, type, target, UNGROUPED);
			rel.setRelationshipId(relIdGenerator.getSCTID());
			rel.setModuleId(moduleId);
			rel.setActive(true);
			rel.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
			rel.setModifier(Modifier.EXISTENTIAL);
			rel.setDirty();
			concept.addRelationship(rel);
		}
	}

}

package org.ihtsdo.termserver.scripting.delta.negative;


import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.InactivationIndicatorEntry;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.domain.Relationship;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class NegativeDeltaGenerator extends DeltaGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(NegativeDeltaGenerator.class);

	String[] conHeader = new String[] {"id","effectiveTime","deletionEffectiveTime","active", "deletionActive","moduleId","definitionStatusId"};
	String[] descHeader = new String[] {"id","effectiveTime","deletionEffectiveTime","active", "deletionActive","moduleId","conceptId","languageCode","typeId","term","caseSignificanceId"};
	String[] relHeader = new String[] {"id","effectiveTime","deletionEffectiveTime","active", "deletionActive","moduleId","sourceId","destinationId","relationshipGroup","typeId","characteristicTypeId","modifierId"};
	String[] langHeader = new String[] {"id","effectiveTime","deletionEffectiveTime","active", "deletionActive","moduleId","refsetId","referencedComponentId","acceptabilityId"};
	String[] attribValHeader = new String[] {"id","effectiveTime","deletionEffectiveTime","active", "deletionActive","moduleId","refsetId","referencedComponentId","valueId"};

	protected void initialiseFileHeaders() throws TermServerScriptException {
		String termDir = packageDir +"Deleted/Terminology/";
		String refDir =  packageDir +"Deleted/Refset/";
		conDeltaFilename = termDir + "dsct2_Concept_Delta_"+edition+"_" + today + ".txt";
		writeToRF2File(conDeltaFilename, conHeader);
		
		relDeltaFilename = termDir + "dsct2_Relationship_Delta_"+edition+"_" + today + ".txt";
		writeToRF2File(relDeltaFilename, relHeader);
		
		sRelDeltaFilename = termDir + "dsct2_StatedRelationship_Delta_"+edition+"_" + today + ".txt";
		writeToRF2File(sRelDeltaFilename, relHeader);
		
		descDeltaFilename = termDir + "dsct2_Description_Delta-"+languageCode+"_"+edition+"_" + today + ".txt";
		writeToRF2File(descDeltaFilename, descHeader);
		
		langDeltaFilename = refDir + "Language/dder2_cRefset_LanguageDelta-"+languageCode+"_"+edition+"_" + today + ".txt";
		writeToRF2File(langDeltaFilename, langHeader);
		
		attribValDeltaFilename = refDir + "Content/dder2_cRefset_AttributeValueDelta_"+edition+"_" + today + ".txt";
		writeToRF2File(attribValDeltaFilename, attribValHeader);
	}

	protected boolean outputRF2(Description d) throws TermServerScriptException {
		boolean componentOutput = false;
		if (d.isDeleted()) {
			writeToRF2File(descDeltaFilename, d.toRF2Deletion());
			componentOutput = true;
		}
		for (LangRefsetEntry lang : d.getLangRefsetEntries()) {
			if (lang.isDeleted()) {
				writeToRF2File(langDeltaFilename, lang.toRF2Deletion());
				componentOutput = true;
			}
		}
		return componentOutput;
	}

	protected boolean outputRF2(Relationship r) throws TermServerScriptException {
		if (r.isDeleted()) {
			switch (r.getCharacteristicType()) {
				case STATED_RELATIONSHIP : writeToRF2File(sRelDeltaFilename, r.toRF2Deletion());
				break;
				case INFERRED_RELATIONSHIP : 
				default: writeToRF2File(relDeltaFilename, r.toRF2Deletion());
			}
			return true;
		}
		return false;
	}
	
	protected boolean outputRF2(InactivationIndicatorEntry i) throws TermServerScriptException {
		if (i.isDeleted()) {
			writeToRF2File(attribValDeltaFilename, i.toRF2Deletion());
			return true;
		}
		return false;
	}

	
	protected boolean outputRF2(Concept c) throws TermServerScriptException {
		boolean componentOutput = false;
		if (c.isDeleted()) {
			writeToRF2File(conDeltaFilename, c.toRF2Deletion());
			componentOutput = true;
		}
		
		for (Description d : c.getDescriptions(ActiveState.BOTH)) {
			componentOutput |= outputRF2(d);
		}
		
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.BOTH)) {
			componentOutput |= outputRF2(r);
		}
		
		for (InactivationIndicatorEntry i: c.getInactivationIndicatorEntries()) {
			componentOutput |= outputRF2(i);
		}
		return componentOutput;
	}

}

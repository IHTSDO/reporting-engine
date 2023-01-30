package org.ihtsdo.termserver.scripting.reports.loinc;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public abstract class LoincTemplatedConcept implements ScriptConstants {
	
	private static final String semTag = " (observable entity)";
	private static Set<String> partNumsMapped = new HashSet<>();
	private static Set<String> partNumsUnmapped = new HashSet<>();
	private static int mapped = 0;
	private static int unmapped = 0;
	private static int skipped = 0;
	
	protected static TermServerScript ts;
	protected static GraphLoader gl;
	protected static Map<String, RelationshipTemplate> loincPartAttributeMap = new HashMap<>();
	
	
	protected String loincNum;
	protected Map<String, Concept> typeMap = new HashMap<>();
	protected String preferredTermTemplate;
	protected Concept concept;

	public static void initialise(TermServerScript ts, GraphLoader gl, Map<String, RelationshipTemplate> loincPartAttributeMap) {
		LoincTemplatedConcept.ts = ts;
		LoincTemplatedConcept.gl = gl;
		LoincTemplatedConcept.loincPartAttributeMap = loincPartAttributeMap;
	}

	protected LoincTemplatedConcept (String loincNum) {
		this.loincNum = loincNum;
	}

	public static LoincTemplatedConcept populateModel(String loincNum, List<LoincPart> loincParts) throws TermServerScriptException {
		LoincTemplatedConcept templatedConcept = getAppropriateTemplate(loincNum, loincParts);
		templatedConcept.populateParts(loincParts);
		templatedConcept.populateTerms(loincNum, loincParts);
		return templatedConcept;
	}

	private void populateTerms(String loincNum, List<LoincPart> loincParts) throws TermServerScriptException {
		//Start with the template PT and swap out as many parts as we come across
		String ptStr = preferredTermTemplate;
		for (LoincPart loincPart : loincParts) {
			if (loincPart.getPartTypeName().equals("CLASS") || 
					loincPart.getPartTypeName().equals("SUFFIX")) {
				continue;
			}
			String templateItem = "\\[" + loincPart.getPartTypeName() + "\\]";
			RelationshipTemplate rt = getAttributeForLoincPart(NOT_SET, loincPart);
			if (rt != null) {
				//TODO Detect GB Spelling and break out another term
				Description targetPt = rt.getTarget().getPreferredSynonym(US_ENG_LANG_REFSET);
				String itemStr = targetPt.getTerm();
				//Can we make this lower case?
				if (targetPt.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE) || 
						targetPt.getCaseSignificance().equals(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE)) {
					itemStr = StringUtils.decapitalizeFirstLetter(itemStr);
				}
				ptStr = ptStr.replaceAll(templateItem, itemStr);
			} else {
				TermServerScript.debug("No attribute during FSN gen for " + loincNum + " / " + loincPart);
			}
		}
		ptStr = tidyUpTerm(loincNum, ptStr);
		ptStr = StringUtils.capitalizeFirstLetter(ptStr);
		Description pt = Description.withDefaults(ptStr, DescriptionType.SYNONYM, Acceptability.PREFERRED);
		Description fsn = Description.withDefaults(ptStr + semTag, DescriptionType.FSN, Acceptability.PREFERRED);
		concept.addDescription(pt);
		concept.addDescription(fsn);
	}

	protected String tidyUpTerm(String loincNum, String term) {
		term = replaceAndWarn(loincNum, term, " at [TIME]");
		term = replaceAndWarn(loincNum, term, " by [METHOD]");
		term = replaceAndWarn(loincNum, term, " to [DIVISOR]");
		term = replaceAndWarn(loincNum, term, " in [SYSTEM]");
		return term;
	}

	private String replaceAndWarn(String loincNum, String term, String str) {
		if (term.contains(str)) {
			TermServerScript.warn(loincNum + " did not provide '" + str + "'");
			//Need to make string regex safe
			str = str.replaceAll("\\[","\\\\\\[").replaceAll("\\]","\\\\\\]");
			term = term.replaceAll(str, "");
		}
		return term;
	}

	private void populateParts(List<LoincPart> loincParts) throws TermServerScriptException {
		concept = new Concept("0");
		concept.addRelationship(IS_A, OBSERVABLE_ENTITY);
		concept.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
		Set<LoincPart> loincPartsSeen = new HashSet<>();
		for (LoincPart loincPart : loincParts) {
			//Have we seen this loincPart before?
			if (loincPartsSeen.contains(loincPart)) {
				TermServerScript.warn("Duplicate LOINC Part seen: " + loincPart);
				continue;
			}
			loincPartsSeen.add(loincPart);
			RelationshipTemplate rt = getAttributeForLoincPart(SECONDARY_REPORT, loincPart);
			if (rt != null) {
				mapped++;
				ts.report(SECONDARY_REPORT,
					loincNum,
					loincPart.getPartNumber(),
					"Mapped OK",
					loincPart.getPartName(),
					rt);
				partNumsMapped.add(loincPart.getPartNumber());
				concept.addRelationship(rt.getType(), rt.getTarget(), SnomedUtils.getFirstFreeGroup(concept));
			} else {
				unmapped++;
				ts.report(SECONDARY_REPORT,
					loincNum,
					loincPart.getPartNumber(),
					"Not Mapped - " + loincPart.getPartTypeName() + " | " + loincPart.getPartNumber(),
					loincPart.getPartName());
				partNumsUnmapped.add(loincPart.getPartNumber());
			}
		}
	}

	private RelationshipTemplate getAttributeForLoincPart(int idxTab, LoincPart loincPart) throws TermServerScriptException {
		RelationshipTemplate rt = loincPartAttributeMap.get(loincPart.getPartNumber());
		if (loincPart.getPartTypeName().equals("CLASS")) {
			skipped++;
			return null;
		}
		
		//Now given the template that we've chosen for this LOINC Term, what attribute
		//type would we use?
		Concept attributeType = typeMap.get(loincPart.getPartTypeName());
		if (attributeType == null) {
			if (idxTab != NOT_SET) {
				ts.report(idxTab,
					loincNum,
					loincPart.getPartNumber(),
					"Type in context not identified - " + loincPart.getPartTypeName() + " | " + this.getClass().getSimpleName(),
					loincPart.getPartName());
			}
		} else if (rt != null) {
			rt.setType(attributeType);
		}
		return rt;
	}

	private static LoincTemplatedConcept getAppropriateTemplate(String loincNum, List<LoincPart> loincParts) throws TermServerScriptException {
		for (LoincPart loincPart : loincParts) {
			switch (loincPart.getPartName()) {
				case "Ratio" :
				case "MRto" :
				case "VFr" :
				case "NFr" : return LoincTemplatedConceptWithRelative.create(loincNum);
				case "PrThr" :
				case "PPres" :
				case "CCnc" :
				case "MCnc" :
				case "SCnc" :
				case "ACnc" :
				case "NCnc" :
				case "LsCnc" :
				case "Naric" :
				case "MoM" :
				case "EntMass" : return LoincTemplatedConceptWithComponent.create(loincNum);
				case "Prid" :
				case "EntVol" :
				case "Vol" :
				case "Type" : return LoincTemplatedConceptWithInheres.create(loincNum);
			}
		}
		//throw new TermServerScriptException("Unable to determine template for " + loincNum);
		return LoincTemplatedConceptWithDefaultMap.create(loincNum);
	}

	public Concept getConcept() {
		return concept;
	}

	public static void reportStats() throws TermServerScriptException {
		ts.report(SECONDARY_REPORT, "");
		ts.report(SECONDARY_REPORT, "Parts mapped", mapped);
		ts.report(SECONDARY_REPORT, "Parts unmapped", unmapped);
		ts.report(SECONDARY_REPORT, "Parts skipped", skipped);
		ts.report(SECONDARY_REPORT, "Unique PartNums mapped", partNumsMapped.size());
		ts.report(SECONDARY_REPORT, "Unique PartNums unmapped", partNumsUnmapped.size());
	}
}

package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.domain.ConcreteValue.ConcreteValueType;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.apache.commons.lang.StringUtils;

/**
 * INFRA-6266 Report to analyse one or more sheets of country drugs
 * Assumptions to check with author
 * 1.  That a film coated tablet is a 420378007 |Prolonged-release film-coated oral tablet (dose form)|
 * TODO: Normalise strengths prior to checking overlap
 * TODO: Note how many times (for each file) a particular concept is required.
 * TODO: Special cases for numbers and brackets in substance names eg Carbon 13
 * TODO: Include BoSS in comparison
 * TODO: For the overlapped concepts, indicate what we'd need to add (Substance, DoseForm) to SNOMED
 * TODO: Map the manufactured dose form to the appropriate presentation eg "Tablet"
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DrugSheetsAnalysis extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(DrugSheetsAnalysis.class);

	private List<File> drugSheets;
	private Map<File, List<DrugLine>> fileLineMap;
	private Set<String> requiredConcepts = new HashSet<>();
	
	enum OverlapScore { NONE, PARTIAL_MP1, PARTIAL_MP2, PARTIAL_MP3, MP, MPF, 
		PARTIAL_CD1, PARTIAL_CD2, PARTIAL_CD3, CD }
	
	//See https://regex101.com/r/773UPz/1
	final static String regex = "(?<substance>.*?) (?<boss>\\(.*\\))? ?(?<strength>\\d*\\.?\\d*) (?<unit>[µ\\w\\-\\%]*)?( ?\\/(?<conc>\\d*\\.?\\d*) (?<concUnit>\\w*))? ?(?<doseForm>.*)?";
	static Pattern pattern;
	int conceptsRequriedTab;
	int overlapTab;
	int currentTab;

	public static void main(String[] args) throws TermServerScriptException {
		DrugSheetsAnalysis report = new DrugSheetsAnalysis();
		try {
			pattern = Pattern.compile(regex);
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			report.analyzeDrugSheets();
			report.calculateOverlap();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report", e);
		} finally {
			report.finish();
		}
	}

	@Override
	public void init(String[] args) throws TermServerScriptException {
		drugSheets = new ArrayList<>();
		fileLineMap = new HashMap<>();
		for (int i=0; i < args.length; i++) {
			if (args[i].startsWith("-f")) {
				File f = new File(args[i+1]);
				if (!f.canRead()) {
					throw new IllegalArgumentException("Can't read " + f);
				}
				drugSheets.add(f);
			}
		}
		super.init(args);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1H7T_dqmvQ-zaaRtfrd-T3QCUMD7_K8st");
		List<String> tabNames = new ArrayList<>();
		List<String> columnHeadings = new ArrayList<>();
		//How many files are we reporting on?
		for (File f : drugSheets) {
			tabNames.add(f.getName());
			columnHeadings.add("Drug, Detail");
		}
		tabNames.add("Concepts Required");
		columnHeadings.add("Element Type, Required, Source, File");
		conceptsRequriedTab = tabNames.size() - 1;
		
		tabNames.add("Overlap");
		columnHeadings.add("FileA, DrugA, Overlap, DrugB, FileB");
		overlapTab = tabNames.size() - 1;
		
		super.postInit(tabNames.toArray(new String[0]), columnHeadings.toArray(new String[0]));
	}

	private void analyzeDrugSheets() throws TermServerScriptException, FileNotFoundException {
		currentTab = PRIMARY_REPORT;
		for (File f : drugSheets) {
			List<DrugLine> drugLines = new ArrayList<>();
			fileLineMap.put(f, drugLines);
			Scanner scanner = new Scanner(f);
			while (scanner.hasNextLine()) {
				String line = normalise(scanner.nextLine());
				String[] lineItems = line.split(TAB);
				drugLines.add(parse(lineItems[0], f));
			}
			scanner.close();
			currentTab++;
		}
	}
	
	
	private void calculateOverlap() throws TermServerScriptException {
		Set<File> alreadyProcessed = new HashSet<>();
		for (Map.Entry<File, List<DrugLine>> outer : fileLineMap.entrySet()) {
			for (Map.Entry<File, List<DrugLine>> inner : fileLineMap.entrySet()) {
				if (!outer.getKey().equals(inner.getKey()) && !alreadyProcessed.contains(inner.getKey())) {
					for (DrugLine outerLine : outer.getValue()) {
						OverlapScore bestScore = OverlapScore.NONE;
						DrugLine bestMatch = null;
						for (DrugLine innerLine : inner.getValue()) {
							OverlapScore thisScore = compareDrugs(outerLine.concept, innerLine.concept);
							if (thisScore.compareTo(bestScore) > 1) {
								bestScore = thisScore;
								bestMatch = innerLine;
							}
						}
						if (bestMatch != null) {
							report(overlapTab, outer.getKey().getName(), outerLine.sourceText, bestScore.name(), bestMatch.sourceText, inner.getKey().getName());
						}
					}
				}
			}
			alreadyProcessed.add(outer.getKey());
		}
	}

	private String normalise(String line) {
		//Normalise some known patterns
		line = line.replace("(as ", "(");
		line = line.replace(" and ", " / ");
		line = line.replace("/g ", "/1 g ");
		line = line.replace("/kg ", "/1 kg ");
		line = line.replace("/mL ", "/1 mL ");
		line = line.replace("/bottle ", "/1 bottle ");
		line = line.replace("/actuation ", "/1 actuation ");
		return line;
	}

	private DrugLine parse(String line, File file) throws TermServerScriptException {
		DrugLine drugLine = new DrugLine(line, file);
		String lastIngredient = "";
		try {
			//Can we split ingredients based on a slash with spaces?
			//Slash without spaces is used in concentration
			String[] ingredientSources = line.split (" / ");
			for (String ingredientSource : ingredientSources) {
				lastIngredient = ingredientSource;
				parseIngredient(drugLine, ingredientSource);
			}
			//What's our count of active ingredient here?
			int count = ingredientSources.length;
			Relationship r = new Relationship(drugLine.concept, COUNT_BASE_ACTIVE_INGREDIENT, Integer.toString(count), UNGROUPED, ConcreteValueType.INTEGER);
			drugLine.concept.addRelationship(r);
			report(currentTab, drugLine.sourceText, drugLine.concept.toExpression(CharacteristicType.STATED_RELATIONSHIP));
		} catch (IllegalStateException e) {
			LOGGER.error ("Failure while processing: " + line + ", specifically " + lastIngredient, e);
		}
		return drugLine;
	}

	private void parseIngredient(DrugLine drugLine, String ingredientSource) throws TermServerScriptException {
		Matcher matcher = pattern.matcher(ingredientSource);
		matcher.find();
		int groupId = SnomedUtils.getFirstFreeGroup(drugLine.concept);
		
		if (has(matcher,"doseForm")) {
			match(drugLine, matcher, "doseForm", ingredientSource, UNGROUPED, HAS_UNIT_OF_PRESENTATION);
			match(drugLine, matcher, "doseForm", ingredientSource, UNGROUPED, HAS_MANUFACTURED_DOSE_FORM);
		}
		
		match(drugLine, matcher, "substance", ingredientSource, groupId, HAS_PRECISE_INGRED);
		
		//If we found a BoSS in brackets then use that, otherwise repeat the substance
		if (has(matcher,"boss")) {
			match(drugLine, matcher, "boss", ingredientSource, groupId, HAS_BOSS);
		} else {
			match(drugLine, matcher, "substance", ingredientSource, groupId, HAS_BOSS);
		}
		
		//Is this a % case?
		boolean isPercentage = false;
		if (has(matcher, "unit") && get(matcher, "unit").equals("%")) {
			isPercentage = true;
			Relationship r = new Relationship (drugLine.concept, HAS_CONC_STRENGTH_DENOM_VALUE, gl.getConcept("420528006 |100 (qualifier value)|"), groupId);
			drugLine.concept.addRelationship(r);
		}
		
		//Are we working with a concentration or a flat strength?
		if (has(matcher, "conc") || isPercentage) {
			match(drugLine, matcher, "strength", ingredientSource, groupId, HAS_CONC_STRENGTH_VALUE);
			if (!isPercentage) {
				match(drugLine, matcher, "unit", ingredientSource, groupId, HAS_CONC_STRENGTH_UNIT);
				match(drugLine, matcher, "conc", ingredientSource, groupId, HAS_CONC_STRENGTH_DENOM_VALUE);
				match(drugLine, matcher, "concUnit", ingredientSource, groupId, HAS_CONC_STRENGTH_DENOM_UNIT);
			}
		} else {
			match(drugLine, matcher, "strength", ingredientSource, groupId, HAS_PRES_STRENGTH_VALUE);
			match(drugLine, matcher, "unit", ingredientSource, groupId, HAS_PRES_STRENGTH_UNIT);
		}
		
	}

	private void match(DrugLine drugLine, Matcher matcher, String matchName, String ingredientSource, int groupId, Concept type) throws TermServerScriptException {
		String matchedText;
		try {
			matchedText = matcher.group(matchName);
			if (StringUtils.isEmpty(matchedText) || matchedText.trim().length() == 0) {
				return;
			}
		} catch (Exception e) {
			String msg = "Failed to parse " + matchName + " in " + ingredientSource;
			report(currentTab, drugLine.sourceText, msg);
			return;
		}
		
		Concept target=null;
		try {
			target = translateToConcept(matchName, matchedText);
		} catch (Exception e) {}
		
		if (target==null) {
			noteRequirement(matchName, matchedText, ingredientSource, drugLine.sourceFile);
			target = new Concept ("05550" + matchedText.hashCode() + "005", matchedText);
		}
		Relationship r = new Relationship (drugLine.concept, type, target, groupId);
		drugLine.concept.addRelationship(r);
	}
	
	private OverlapScore compareDrugs(Concept drugA, Concept drugB) throws TermServerScriptException {
		//How many ingredients do drugA and drugB have in common?
		OverlapScore score = checkIngredientOverlap(drugA, drugB);
		
		//If we've only a partial overlap of ingredients, don't check further
		if (score.equals(OverlapScore.NONE) || !score.equals(OverlapScore.MP)) {
			return score;
		}
		
		//Do we share a dose Form?
		Concept doseFormA = SnomedUtils.getTarget(drugA, new Concept[] { HAS_MANUFACTURED_DOSE_FORM } , UNGROUPED, CharacteristicType.STATED_RELATIONSHIP, true);
		Concept doseFormB = SnomedUtils.getTarget(drugB, new Concept[] { HAS_MANUFACTURED_DOSE_FORM } , UNGROUPED, CharacteristicType.STATED_RELATIONSHIP, true);
		if (doseFormA == null || doseFormB == null || !doseFormA.equals(doseFormB)) {
			return score;
		}
		
		//How many of the strengths match?
		return checkStrengthOverlap(drugA, drugB);
	}

	private OverlapScore checkIngredientOverlap(Concept drugA, Concept drugB) throws TermServerScriptException {
		OverlapScore score = OverlapScore.NONE;
		int aCount = drugA.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_PRECISE_INGRED, ActiveState.ACTIVE).size();
		int bCount = drugB.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_PRECISE_INGRED, ActiveState.ACTIVE).size();
		int maxDrugsCount = aCount > bCount ? aCount : bCount;
		Concept largerDrug = aCount > bCount ? drugA : drugB;
		Concept smallerDrug = aCount > bCount ? drugB : drugA;
		
		int matchedIngreds = 0;
		for (RelationshipGroup drugGroup : largerDrug.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, false)) {
			RelationshipGroup matchedDrugGroup = SnomedUtils.findMatchingGroup(smallerDrug, drugGroup, HAS_PRECISE_INGRED, CharacteristicType.STATED_RELATIONSHIP, true);
			if (matchedDrugGroup != null ) {
				matchedIngreds++;
				if (score.compareTo(OverlapScore.PARTIAL_MP3) < 0) {
					score = OverlapScore.values()[score.ordinal() + 1];
				}
			}
		}
		
		//Did we get all of them?
		if (matchedIngreds == maxDrugsCount) {
			return OverlapScore.MP;
		}
		return score;
	}
	

	private OverlapScore checkStrengthOverlap(Concept drugA, Concept drugB) throws TermServerScriptException {
		//To be here, we have a 1:1 match on ingredients so we can loop through either
		OverlapScore score = OverlapScore.MPF;
		int maxMatches = drugB.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_PRECISE_INGRED, ActiveState.ACTIVE).size();
		int matchedStrengths = 0;
		for (RelationshipGroup drugGroup : drugA.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, false)) {
			RelationshipGroup matchedDrugGroup = SnomedUtils.findMatchingGroup(drugB, drugGroup, HAS_PRECISE_INGRED, CharacteristicType.STATED_RELATIONSHIP, true);
			if (matchedDrugGroup != null && matchStrengths(drugGroup, matchedDrugGroup)) {
				matchedStrengths++;
				if (score.compareTo(OverlapScore.PARTIAL_CD3) < 0) {
					score = OverlapScore.values()[score.ordinal() + 1];
				}
			}
		}
		if (matchedStrengths == maxMatches) {
			return OverlapScore.CD;
		}
		return score;
	}

	private boolean matchStrengths(RelationshipGroup groupA, RelationshipGroup groupB) throws TermServerScriptException {
		if (matchAttribute(groupA, groupB, HAS_PRES_STRENGTH_VALUE)) {
			return false;
		}
		if (matchAttribute(groupA, groupB, HAS_PRES_STRENGTH_UNIT)) {
			return false;
		}
		if (matchAttribute(groupA, groupB, HAS_PRES_STRENGTH_DENOM_VALUE)) {
			return false;
		}
		if (matchAttribute(groupA, groupB, HAS_PRES_STRENGTH_DENOM_UNIT)) {
			return false;
		}
		if (matchAttribute(groupA, groupB, HAS_CONC_STRENGTH_VALUE)) {
			return false;
		}
		if (matchAttribute(groupA, groupB, HAS_CONC_STRENGTH_UNIT)) {
			return false;
		}
		if (matchAttribute(groupA, groupB, HAS_CONC_STRENGTH_DENOM_VALUE)) {
			return false;
		}
		if (matchAttribute(groupA, groupB, HAS_CONC_STRENGTH_DENOM_UNIT)) {
			return false;
		}
		return true;
	}

	private boolean matchAttribute(RelationshipGroup groupA, RelationshipGroup groupB, Concept type) throws TermServerScriptException {
		Concept targetValueA = SnomedUtils.getTarget(groupA, new Concept[] { type }, true);
		Concept targetValueB = SnomedUtils.getTarget(groupB, new Concept[] { type }, true);
		
		//Now if neither group has this attribute type, we'll call that a match
		//But if one does and the other doesn't then it's a fail
		//And if they both have it, then we can check for a match
		if (targetValueA == null) {
			if (targetValueB == null) {
				return true;
			} else {
				return false;
			}
		} else if (targetValueB == null) {
			return false;
		} else {
			return targetValueA.equals(targetValueB);
		}
	}

	private void noteRequirement(String elementType, String conceptName, String source, File sourceFile) throws TermServerScriptException {
		if (!requiredConcepts.contains(conceptName)) {
			report(conceptsRequriedTab, elementType, conceptName, source, sourceFile.getName());
			requiredConcepts.add(conceptName);
		}
	}
	
	/**
	 * @return true if the match has found the particular named group
	 */
	private boolean has(Matcher matcher, String matchName) {
		try {
			return matcher.group(matchName) != null;
		} catch (IllegalStateException e) {}
		return false;
	}
	
	private String get(Matcher matcher, String matchName) {
		try {
			return matcher.group(matchName) ;
		} catch (IllegalStateException e) {}
		return null;
	}

	private Concept translateToConcept(String matchName, String text) throws TermServerScriptException {
		switch (matchName) {
			case "doseForm" : return DrugUtils.findDoseFormFromSynonym(text);
			case "substance" : 
			case "boss" : return DrugUtils.findSubstance(text.replace("(", "").replace(")", ""));
			case "conc" :
			case "strength" : throw new IllegalArgumentException("Strength is no longer represented by a concept");
			case "concUnit" : 
			case "unit" : return DrugUtils.findUnitOfMeasure(text);
			default : throw new IllegalArgumentException("Unrecognised match name: " + matchName);
		}
	}

	class DrugLine {
		DrugLine (String sourceText, File sourceFile) {
			this.sourceText = sourceText;
			this.sourceFile = sourceFile;
			concept.addParent(CharacteristicType.STATED_RELATIONSHIP, MEDICINAL_PRODUCT);
		}
		File sourceFile;
		String sourceText;
		Concept concept = new Concept((String)null);
	}
}

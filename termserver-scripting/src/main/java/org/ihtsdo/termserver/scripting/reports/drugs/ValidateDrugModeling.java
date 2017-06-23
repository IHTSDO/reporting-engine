package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class ValidateDrugModeling extends TermServerScript{
	
	String subHierarchyStr = "373873005"; // |Pharmaceutical / biologic product (product)|
	static final String SCTID_ACTIVE_INGREDIENT = "127489000"; // |Has active ingredient (attribute)|"
	static final String SCTID_HAS_BOSS = "732943007"; //Has basis of strength substance (attribute)
	static final String SCTID_MAN_DOSE_FORM = "411116001"; //Has manufactured dose form (attribute)
	Concept activeIngredient;
	Concept hasManufacturedDoseForm;
	Concept boss;
	GraphLoader gl = GraphLoader.getGraphLoader();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		ValidateDrugModeling report = new ValidateDrugModeling();
		try {
			report.init(args);
			report.loadProjectSnapshot(false); //Load all descriptions
			report.validateModeling();
		} catch (Exception e) {
			println("Failed to produce Druge Model Validation Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} 
	}
	
	private void validateModeling() throws TermServerScriptException {
		Set<Concept> subHierarchy = gl.getConcept(subHierarchyStr).getDescendents(NOT_SET);
		String[] drugTypes = new String[] { "(medicinal product form)", "(clinical drug)" };
		long issueCount = 0;
		for (Concept concept : subHierarchy) {
			issueCount += validateIngredientsInFSN(concept);
			issueCount += validateIngredientsAgainstBoSS(concept);
			issueCount += validateStatedVsInferredAttributes(concept, activeIngredient, drugTypes);
			issueCount += validateStatedVsInferredAttributes(concept, hasManufacturedDoseForm, drugTypes);
			issueCount += validateAttributeValueCardinality(concept, activeIngredient);
		}
		println ("Validation complete.  Detected " + issueCount + " issues.");
	}
	
	private int validateStatedVsInferredAttributes(Concept concept,
			Concept attributeType, String[] drugTypes) {
		int issueCount = 0;
		if (isDrugType(concept, drugTypes)) {
			List<Relationship> statedAttributes = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
			List<Relationship> infAttributes = concept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
			if (statedAttributes.size() != infAttributes.size()) {
				String msg = "Cardinality mismatch stated vs inferred " + attributeType;
				String data = "(s" + statedAttributes.size() + " i" + infAttributes.size() + ")";
				issueCount++;
				report (concept, msg, data);
			} else {
				for (Relationship statedAttribute : statedAttributes) {
					boolean found = false;
					for (Relationship infAttribute : infAttributes) {
						if (statedAttribute.getTarget().equals(infAttribute.getTarget())) {
							found = true;
							break;
						}
					}
					if (!found) {
						String msg = "Stated " + statedAttribute.getType() + " is not present in inferred view";
						String data = statedAttribute.toString();
						issueCount++;
						report (concept, msg, data);
					}
				}
			}
		}
		return issueCount;
	}

	private int validateIngredientsAgainstBoSS(Concept concept) throws TermServerScriptException {
		int issueCount = 0;
		List<Relationship> bossAttributes = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, boss, ActiveState.ACTIVE);

		//Check BOSS attributes against active ingredients - must be in the same relationship group
		List<Relationship> ingredientRels = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, activeIngredient, ActiveState.ACTIVE);
		for (Relationship bRel : bossAttributes) {
			boolean matchFound = false;
			for (Relationship iRel : ingredientRels) {
				if (bRel.getGroupId() == iRel.getGroupId() && isSelfOrSubTypeOf(bRel.getTarget(), iRel.getTarget())) {
					matchFound = true;
				}
			}
			if (!matchFound) {
				String issue = "Basis of Strength not equal or subtype of active ingredient";
				report (concept, issue, bRel.getTarget().toString());
				issueCount++;
			}
		}
		return issueCount;
	}

	//Return true if concept c is equal or a subtype of the superType
	private boolean isSelfOrSubTypeOf(Concept c, Concept superType) throws TermServerScriptException {
		if (c.equals(superType)) {
			return true;
		}
		Set<Concept> subTypes = superType.getDescendents(NOT_SET);
		if (subTypes.contains(c)) {
			return true;
		}
		return false;
	}

	private int validateIngredientsInFSN(Concept concept) throws TermServerScriptException {
		int issueCount = 0;
		String[] drugTypes = new String[] { "(medicinal product)" };
		
		//Only check FSN for certain drug types (to be expanded later)
		if (!isDrugType(concept, drugTypes)) {
			return issueCount;
		}
		//Get all the ingredients and put them in order
		List<Relationship> ingredientRels = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, activeIngredient, ActiveState.ACTIVE);
		Set<String> ingredients = new TreeSet<String>();  //Will naturally sort in alphabetical order
		for (Relationship r : ingredientRels) {
			//Need to recover the full concept to have all descriptions, not the partial one stored as the target.
			Description ingredientFSN = gl.getConcept(r.getTarget().getConceptId()).getFSNDescription();
			String ingredientName = SnomedUtils.deconstructFSN(ingredientFSN.getTerm())[0];
			//If the ingredient name is not case sensitive, decaptialize
			if (!ingredientFSN.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE)) {
				ingredientName = SnomedUtils.deCapitalize(ingredientName);
			}
			ingredients.add(ingredientName);
		}
		
		String proposedFSN = "Product containing ";
		proposedFSN += StringUtils.join(ingredients, " and ");
		proposedFSN += " " + SnomedUtils.deconstructFSN(concept.getFsn())[1];
		
		if (!concept.getFsn().equals(proposedFSN)) {
			String issue = "FSN did not match expected pattern";
			report (concept, issue, proposedFSN);
			issueCount++;
		}
		return issueCount;
	}
	
	private int validateAttributeValueCardinality(Concept concept, Concept activeIngredient) throws TermServerScriptException {
		int issuesEncountered = 0;
		issuesEncountered += checkforRepeatedAttributeValue(concept, CharacteristicType.INFERRED_RELATIONSHIP, activeIngredient);
		issuesEncountered += checkforRepeatedAttributeValue(concept, CharacteristicType.STATED_RELATIONSHIP, activeIngredient);
		return issuesEncountered;
	}

	private int checkforRepeatedAttributeValue(Concept c, CharacteristicType charType, Concept activeIngredient) throws TermServerScriptException {
		int issues = 0;
		Set<Concept> valuesEncountered = new HashSet<Concept>();
		for (Relationship r : c.getRelationships(charType, activeIngredient, ActiveState.ACTIVE)) {
			//Have we seen this value for the target attribute type before?
			Concept target = r.getTarget();
			if (valuesEncountered.contains(target)) {
				String msg = "Multiple " + charType + " instances of active ingredient";
				report(c, msg, target.toString());
				issues++;
			}
			valuesEncountered.add(target);
		}
		return issues;
	}

	private boolean isDrugType(Concept concept, String[] drugTypes) {
		boolean isType = false;
		for (String drugType : drugTypes) {
			if (SnomedUtils.deconstructFSN(concept.getFsn())[1].equals(drugType)) {
				isType = true;
				break;
			}
		}
		return isType;
	}

	protected void report (Concept c, String issue, String data) {
		String line =	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA_QUOTE +
						SnomedUtils.deconstructFSN(c.getFsn())[1] + QUOTE_COMMA_QUOTE +
						issue + QUOTE_COMMA_QUOTE +
						data + QUOTE;
		writeToFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException {
		super.init(args);
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String reportFilename = "drug_model_validation_" + project.toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToFile ("Concept, FSN, SemTag, Issue, Data");
		
		//Recover static concepts that we'll need to search for in attribute types
		activeIngredient = gl.getConcept(SCTID_ACTIVE_INGREDIENT);
		hasManufacturedDoseForm = gl.getConcept(SCTID_MAN_DOSE_FORM);
		boss = gl.getConcept(SCTID_HAS_BOSS);
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}
}

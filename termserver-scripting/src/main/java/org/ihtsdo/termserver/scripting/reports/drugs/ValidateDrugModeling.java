package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
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
	String[] drugTypes = new String[] { "(medicinal product)" };
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
		Concept activeIngredient = gl.getConcept(SCTID_ACTIVE_INGREDIENT);
		Concept boss = gl.getConcept(SCTID_HAS_BOSS);
		long issueCount = 0;
		for (Concept concept : subHierarchy) {
			issueCount += validateIngredientsInFSN(concept, activeIngredient);
			issueCount += validateIngredientsAgainstBoSS(concept, activeIngredient, boss);
		}
		println ("Validation complete.  Detected " + issueCount + " issues.");
	}
	
	private int validateIngredientsAgainstBoSS(Concept concept, Concept activeIngredient, Concept boss) throws TermServerScriptException {
		int issueCount = 0;
		List<Relationship> bossAttributes = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, boss, ActiveState.ACTIVE);
		if (bossAttributes.size() > 1) {
			String issue = bossAttributes.size() + " basis of strength attributes detected";
			report (concept, issue, "");
			issueCount++;
		}
		//Check BOSS attributes against active ingredients
		List<Relationship> ingredientRels = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, activeIngredient, ActiveState.ACTIVE);
		for (Relationship bRel : bossAttributes) {
			boolean matchFound = false;
			for (Relationship iRel : ingredientRels) {
				if (isSelfOrSubTypeOf(bRel.getTarget(), iRel.getTarget())) {
					matchFound = true;
				}
			}
			if (!matchFound) {
				String issue = "Basis of Strength not equal of subtype of active ingredient";
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

	private int validateIngredientsInFSN(Concept concept, Concept activeIngredient) throws TermServerScriptException {
		int issueCount = 0;
		//Only check FSN for certain drug types (to be expanded later)
		if (!verifyDrugType(concept)) {
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

	private boolean verifyDrugType(Concept concept) {
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
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}
}

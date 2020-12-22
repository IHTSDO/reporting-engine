package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.DrugTermGenerator;
import org.ihtsdo.termserver.scripting.util.DrugTermGeneratorCD;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.ihtsdo.termserver.scripting.util.TermGenerator;

public abstract class DrugBatchFix extends BatchFix implements RF2Constants{
	
	static Map<String, String> wordSubstitution = new HashMap<String, String>();
	static {
		wordSubstitution.put("acetaminophen", "paracetamol");
	}
	
	String [] unwantedWords = new String[] { "preparation", "product" };
	
	static final String find = "/1 each";
	static final String replace = "";
	static final String PLUS = "+";
	static final String PLUS_ESCAPED = "\\+";
	static final String PLUS_SPACED_ESCAPED = " \\+ ";
	static final String IN = " in ";
	static final String ONLY = "only ";
	static final String AND = " and ";
	static final String newSemanticTag = "(medicinal product)";
	
	List<String> doseForms = new ArrayList<String>();
	
	TermGenerator termGenerator;

	protected DrugBatchFix(BatchFix clone) {
		super(clone);
	}
	
	public void postInit() throws TermServerScriptException {
		//Are we living in a post concrete domains era?
		boolean useConcreteValues = false;
		Concept random = gl.getConcept("774966003 |Product containing only caffeine (medicinal product)|");
		loop:
		for (Concept c : random.getDescendents(NOT_SET)) {
			for (Relationship r : c.getRelationships()) {
				if (r.isConcrete()) {
					useConcreteValues = true;
					break loop;
				}
			}
		}
		
		if (useConcreteValues) {
			termGenerator = new DrugTermGeneratorCD(this);
		} else {
			termGenerator = new DrugTermGenerator(this);
		}
	}

	public int assignIngredientCounts(Task t, Concept c, CharacteristicType charType) throws TermServerScriptException {
		int changes = 0;
		List<Concept> ingredients = DrugUtils.getIngredients(c, charType);
		if (ingredients == null || ingredients.size() == 0) {
			throw new ValidationFailure(c, "No ingredients found for ingredient count");
		} else if (ingredients.size() == 1) {
			changes = replaceRelationship(t, c, COUNT_BASE_ACTIVE_INGREDIENT, DrugUtils.getNumberAsConcept("1"), UNGROUPED, true);
		} else {
			//Quick check that the number of ingredients matches the number of " and "
			if (ingredients.size() != c.getFsn().split(AND).length) {
				report(t,c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "FSN does not suggest " + ingredients.size() + " ingredients");
			}
			Set<Concept> bases = getBases(ingredients);
			if (bases.size() != ingredients.size()) {
				report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Ingredients / Base Count: " + ingredients.size() + " / " + bases.size());
			}
			Concept baseCountConcept = DrugUtils.getNumberAsConcept(Integer.toString(bases.size()));
			changes = replaceRelationship(t, c, COUNT_BASE_ACTIVE_INGREDIENT, baseCountConcept, UNGROUPED, true);
		}
		return changes;
	}

	protected Set<Concept> getBases(List<Concept> ingredients) throws TermServerScriptException {
		Set<Concept> bases = new HashSet<>();
		for (Concept ingredient : ingredients) {
			//We need a local copy of the ingredient to get it's full set of relationship concepts
			ingredient = gl.getConcept(ingredient.getConceptId());
			bases.add(DrugUtils.getBase(ingredient));
		}
		return bases;
	}
	
	protected String removeUnwantedWords(String str, boolean isFSN) {
		String semTag = "";
		//Keep the semantic tag separate
		if (isFSN) {
			String[] parts = SnomedUtils.deconstructFSN(str);
			str = parts[0];
			semTag = " " + parts[1];
		}
		
		for (String unwantedWord : unwantedWords) {
			String[] unwantedWordCombinations = new String[] { SPACE + unwantedWord, unwantedWord + SPACE };
			for (String thisUnwantedWord : unwantedWordCombinations) {
				if (str.contains(thisUnwantedWord)) {
					str = str.replace(thisUnwantedWord,"");
				}
			}
		}
		return str + semTag;
	}

}

package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.DrugTermGenerator;
import org.ihtsdo.termserver.scripting.util.TermGenerator;

public abstract class DrugBatchFix extends BatchFix implements ScriptConstants{

	static Map<String, String> wordSubstitution = new HashMap<>();
	static {
		wordSubstitution.put("acetaminophen", "paracetamol");
	}
	
	protected static final String [] unwantedWords = new String[] { "preparation", "product" };
	static final String AND = " and ";

	TermGenerator termGenerator;

	protected DrugBatchFix(BatchFix clone) {
		super(clone);
	}

	@Override
	public void postInit(String googleFolder, String[] tabNames, String[] columnHeadings, boolean csvOutput) throws TermServerScriptException {
		super.postInit(googleFolder, tabNames, columnHeadings, csvOutput);
		termGenerator = new DrugTermGenerator(this);
	}

	public int assignIngredientCounts(Task t, Concept c, CharacteristicType charType) throws TermServerScriptException {
		int changes = 0;
		List<Concept> ingredients = DrugUtils.getIngredients(c, charType);
		if (ingredients == null || ingredients.isEmpty()) {
			throw new ValidationFailure(c, "No ingredients found for ingredient count");
		} else if (ingredients.size() == 1) {
			ConcreteValue cv = new ConcreteValue(ConcreteValue.ConcreteValueType.INTEGER, "1");
			changes = replaceRelationship(t, c, COUNT_BASE_ACTIVE_INGREDIENT, null, cv, UNGROUPED, RelationshipTemplate.Mode.PERMISSIVE);
		} else {
			//Quick check that the number of ingredients matches the number of " and "
			if (ingredients.size() != c.getFsn().split(AND).length) {
				report(t,c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "FSN does not suggest " + ingredients.size() + " ingredients");
			}
			Set<Concept> bases = getBases(ingredients);
			if (bases.size() != ingredients.size()) {
				report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Ingredients / Base Count: " + ingredients.size() + " / " + bases.size());
			}
			ConcreteValue cv = new ConcreteValue(ConcreteValue.ConcreteValueType.INTEGER,Integer.toString(bases.size()));
			changes = replaceRelationship(t, c, COUNT_BASE_ACTIVE_INGREDIENT,null, cv, UNGROUPED, RelationshipTemplate.Mode.PERMISSIVE);
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
			String[] parts = SnomedUtilsBase.deconstructFSN(str);
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

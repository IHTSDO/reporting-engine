package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.b2international.commons.StringUtils;

import us.monoid.json.JSONObject;

/*
 * Combination of DRUGS-363 to remove "/1 each" from preferred terms
 */
public class NormalizeDrugTerms extends BatchFix implements RF2Constants{
	
	String subHierarchyStr = "373873005"; // |Pharmaceutical / biologic product (product)|
	static Map<String, String> replacementMap = new HashMap<String, String>();
	static final String findConceptsStarting = "Product containing";
	static final String find = "/1 each";
	static final String replace = "";
	static final String PLUS = "+";
	static final String PLUS_ESCAPED = "\\+";
	static final String PLUS_SPACED_ESCAPED = " \\+ ";
	static final String IN = " in ";
	static final String ONLY = "only ";
	final String AND = " and ";
	List<String> doseForms = new ArrayList<String>();
	private List<String> exceptions = new ArrayList<>();
	private String[] vitamins = new String[] {" A ", " B ", " C ", " D ", " E " };
	
	protected NormalizeDrugTerms(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		NormalizeDrugTerms fix = new NormalizeDrugTerms(null);
		try {
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
			//fix.runStandAlone = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.getDoseForms();
			fix.startTimer();
			Batch batch = fix.formIntoBatch();
			fix.batchProcess(batch);
			println ("Processing complete.  See results: " + fix.reportFile.getAbsolutePath());
		} finally {
			fix.finish();
		}
	}
	
	private void getDoseForms() throws TermServerScriptException {
		Concept doseFormRoot = gl.getConcept(421967003L);  // |Drug dose form (qualifier value)|);
		doseForms.add(" oral tablet");
		doseForms.add(" in oral dosage form");
		for (Concept doseForm : doseFormRoot.getDescendents(NOT_SET)) {
			Description pt = doseForm.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE).get(0);
			doseForms.add(" " + pt.getTerm());
		}
	}

	protected void init(String[] args) throws TermServerScriptException, IOException {
		super.init(args);
		exceptions.add("423967005");
		exceptions.add("319925005");
		exceptions.add("423967005");
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = normalizeDrugConceptTerms(task, loadedConcept);
		if (changesMade > 0) {
			try {
				String conceptSerialised = gson.toJson(loadedConcept);
				debug ((dryRun?"Skipping update":"Updating state") + " of " + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		return changesMade;
	}

	private int normalizeDrugConceptTerms(Task task, Concept concept) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : concept.getDescriptions(ActiveState.ACTIVE)) {

			String replacementTerm = d.getTerm();
			//If any term contains a PLUS sign, flag validation
			if (replacementTerm.contains(PLUS)) {
				//Try for the properly spaced version first, so we don't introduce extra spaces.
				replacementTerm = replacementTerm.replaceAll(PLUS_SPACED_ESCAPED, AND);
				replacementTerm = replacementTerm.replaceAll(PLUS_ESCAPED, AND);
			}
			
			//If this is the PT, remove any /1 each
			if (d.isPreferred() && d.getType().equals(DescriptionType.SYNONYM) && replacementTerm.contains(find)) {
				replacementTerm = replacementTerm.replace(find, replace);
			}

			//Check ingredient order
			if (replacementTerm.contains(AND)) {
				replacementTerm = normalizeMultiIngredientTerm(replacementTerm, d.getType());
			}

			//Have we made any changes?  Create a new description if so
			if (!replacementTerm.equals(d.getTerm())) {
				boolean doReplacement = true;
				if (termAlreadyExists(concept, replacementTerm)) {
					report(task, concept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Replacement term already exists: '" + replacementTerm + "' inactivating abnormal term only.");
					doReplacement = false;
				}
				
				Description replacement = d.clone(null);
				replacement.setTerm(replacementTerm);
				String msg;
				//Has our description been published?  Remove entirely if not
				if (d.isReleased()) {
					d.setActive(false);
					d.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
					msg = "Inactivated ";
				} else {
					concept.getDescriptions().remove(d);
					msg = "Deleted ";
				}
				msg +=  d.getDescriptionId() + " - '" + d.getTerm().toString();
				
				if (doReplacement) {
					msg += "' in favour of '" + replacementTerm + "'";
					concept.addDescription(replacement);
				}
				changesMade++;
				report(task, concept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, msg);
			}
		}
		return changesMade;
	}

	private boolean termAlreadyExists(Concept concept, String newTerm) {
		boolean termAlreadyExists = false;
		for (Description description : concept.getDescriptions()) {
			if (description.getTerm().equals(newTerm)) {
				termAlreadyExists = true;
			}
		}
		return termAlreadyExists;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		Set<Concept> allPotential = GraphLoader.getGraphLoader().getConcept(subHierarchyStr).getDescendents(NOT_SET);
		Set<Concept> allAffected = new TreeSet<Concept>();  //We want to process in the same order each time, in case we restart and skip some.
		println("Identifying concepts to process");
		for (Concept c : allPotential) {
			if (exceptions.contains(c.getId())) {
				report (c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Concept manually listed as an exception");
			} else if (c.getFsn().startsWith(findConceptsStarting)) {
				//We're going to skip Clinical Drugs
				String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
				switch (semTag) {
					case "(medicinal product)" : c.setConceptType(ConceptType.MEDICINAL_PRODUCT);
												 break;
					case "(medicinal product form)" : c.setConceptType(ConceptType.MEDICINAL_PRODUCT_FORM);
					 							break;
					case "(clinical drug)" : c.setConceptType(ConceptType.CLINICAL_DRUG);
												continue;  //Skip CDs for now.
					default : c.setConceptType(ConceptType.UNKNOWN);
				}
				
				//Identify either PT contains /1 each OR ingredients in wrong order 
				//OR contains a + sign
				Description pt = c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE).get(0);
				
				if (pt.getTerm().contains(find) || pt.getTerm().contains(PLUS)) {
					allAffected.add(c);
					continue;
				}
				//Now check for multi ingredients out of order in any term
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (d.getTerm().contains(AND)) {
						String normalized = normalizeMultiIngredientTerm(d.getTerm(), d.getType());
						if (!normalized.equals(d.getTerm())) {
							allAffected.add(c);
							break;
						}
					}
				}
			}
		}
		println ("Identified " + allAffected.size() + " concepts to process");
		return new ArrayList<Component>(allAffected);
	}
	
	protected String normalizeMultiIngredientTerm(String term, DescriptionType descriptionType) {
		
		String semanticTag = "";
		if (descriptionType.equals(DescriptionType.FSN)) {
			String[] parts = SnomedUtils.deconstructFSN(term);
			term = parts[0];
			semanticTag = " " + parts[1];
		}
		
		String origTerm = term;
		String prefix = "";
		if (term.startsWith(findConceptsStarting)) {
			prefix = findConceptsStarting + " ";
			term = term.substring(prefix.length());
			if (term.startsWith(ONLY)) {
				prefix += ONLY;
				term = term.substring(ONLY.length());
			}
		}
		
		String[] parts = deconstructDoseForm(term);
		term = parts[0];
		String oneEach = parts[1];
		String suffix = parts[2]; 
		
		term = sortIngredients(term);
		
		if (prefix.isEmpty()) {
			term = StringUtils.capitalizeFirstLetter(term);
		}
		
		//See if we've accidentally made vitamin letters lower case and switch back
		for (String vitamin : vitamins) {
			if (origTerm.contains(vitamin)) {
				term = term.replace(vitamin.toLowerCase(), vitamin);
			} else if (origTerm.endsWith(vitamin.substring(0, 2))) {
				//Is it still at the end?
				if (term.endsWith(vitamin.toLowerCase().substring(0, 2))) {
					term = term.replace(vitamin.toLowerCase().substring(0, 2), vitamin.substring(0, 2));
				} else {
					//It should now have a space around it
					term = term.replace(vitamin.toLowerCase(), vitamin);
				}
			}
		}
		
		return prefix + term + oneEach + suffix + semanticTag;
	}

	private String sortIngredients(String term) {
		String[] ingredients = term.split(AND);
		//ingredients should be in alphabetical order, also trim spaces
		for (int i = 0; i < ingredients.length; i++) {
			ingredients[i] = ingredients[i].toLowerCase().trim();
		}
		Arrays.sort(ingredients);

		//Reform with spaces around + sign and only first letter capitalized
		boolean isFirstIngredient = true;
		term = "";
		for (String thisIngredient : ingredients) {
			if (!isFirstIngredient) {
				term += AND;
			} 
			term += thisIngredient.toLowerCase();
			isFirstIngredient = false;
		}
		return term;
	}

	private String[] deconstructDoseForm(String term) {
		String[] parts = new String[]{term,"", ""};
		for (String doseForm : doseForms ) {
			if (term.endsWith(doseForm)) {
				parts[0] = term.substring(0, term.length() - doseForm.length());
				parts[2] = doseForm;
				if (parts[0].endsWith(find)) {
					parts[0] = parts[0].substring(0, parts[0].length() - find.length());
					parts[1] = find;
				}
				break;
			}
		}
		return parts;
	}

	@Override
	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}
}

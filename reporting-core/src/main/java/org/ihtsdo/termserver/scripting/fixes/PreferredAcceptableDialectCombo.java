package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.*;

import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.*;

/*
 * SUBST-226 PTs that are preferred in one dialect should be acceptable in the other
 * So P/N will become P/A
 * UPDATE:  Only run against substances that are used as ingredients in products
*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreferredAcceptableDialectCombo extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(PreferredAcceptableDialectCombo.class);

	String subHierarchy = "105590001 |Substance (substance)|"; 
	String[] exclusions = new String[] { "312435005 |Industrial and household substance (substance)|",
										"762766007 |Edible substance (substance)|"};
	Set<Concept> allExclusions; 
	Set<Concept> substancesUsedInProducts;
	
	protected PreferredAcceptableDialectCombo(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		PreferredAcceptableDialectCombo fix = new PreferredAcceptableDialectCombo(null);
		try {
			fix.populateEditPanel = false;
			fix.additionalReportColumns = "New Value, Old Value";
			fix.selfDetermining = true;
			fix.init(args);
			fix.loadProjectSnapshot(false); 
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		allExclusions = new HashSet<>();
		for (String exclusion : exclusions) {
			Concept subHierarchy = gl.getConcept(exclusion);
			allExclusions.addAll(subHierarchy.getDescendants(NOT_SET));
		}
		getSubstancesUsedInProducts();
		super.postInit();
	}
	
	private void getSubstancesUsedInProducts() throws TermServerScriptException {
		substancesUsedInProducts = new HashSet<>();
		for (Concept product : PHARM_BIO_PRODUCT.getDescendants(NOT_SET)) {
			for (Relationship r : product.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE)) {
				substancesUsedInProducts.add(r.getTarget());
			}
			for (Relationship r : product.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_PRECISE_INGRED, ActiveState.ACTIVE)) {
				substancesUsedInProducts.add(r.getTarget());
			}
		}
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(c, t.getBranchPath());
		int changesMade = normalizeAcceptability(t,loadedConcept);
		if (changesMade > 0) {
			try {
				updateConcept(t, loadedConcept, info);
			} catch (Exception e) {
				report(t, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		return changesMade;
	}

	private int normalizeAcceptability(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE)) {
			String before = d.toString();
			//Which dialect are we preferred in?
			if (d.hasAcceptability(Acceptability.PREFERRED, US_ENG_LANG_REFSET) && 
					d.hasAcceptability(Acceptability.NONE, GB_ENG_LANG_REFSET)) {
				d.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(AcceptabilityMode.PREFERRED_US));
				changesMade++;	
			} else if (d.hasAcceptability(Acceptability.PREFERRED, GB_ENG_LANG_REFSET) && 
					d.hasAcceptability(Acceptability.NONE, US_ENG_LANG_REFSET)) {
				d.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(AcceptabilityMode.PREFERRED_GB));
				changesMade++;	
			}
			String after = d.toString();
			report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE, after, before);
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		LOGGER.info("Identifying concepts to process");
		List<Component> processMe = new ArrayList<Component>();
		nextConcept:
		for (Concept c : gl.getConcept(subHierarchy).getDescendants(NOT_SET)) {
			if (allExclusions.contains(c) || !substancesUsedInProducts.contains(c)) {
				continue;
			}
			if (c.getConceptId().equals("126071000")) {
				//LOGGER.debug("Debug here");
			}
			for (Description d : c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE)) {
				//If we only have one acceptability indicator, then it's a P/N situation
				if (d.getLangRefsetEntries(ActiveState.ACTIVE).size() == 1) {
					processMe.add(c);
					continue nextConcept;
				}
			}
		}
		LOGGER.info("Identified " + processMe.size() + " concepts to process");
		return processMe;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		throw new NotImplementedException("This class self determines concepts to process");
	}
}

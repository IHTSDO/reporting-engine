package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.AcceptabilityMode;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Create additional acceptable synonyms to address US/GB spelling variations.
 */
@Deprecated
//This class is insufficient because it doesn't account for the substances already
//having us/gb variance.  We'll need to enhance the TermGenerator class to address this.
public class AddAdditionalSynonyms extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(AddAdditionalSynonyms.class);

	protected AddAdditionalSynonyms(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		AddAdditionalSynonyms fix = new AddAdditionalSynonyms(null);
		try {
			fix.additionalReportColumns = "term, hasPT_US_GB_Variance";
			fix.selfDetermining = true;
			fix.init(args);
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = createAdditionalSynonyms(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}

	private int createAdditionalSynonyms(Task task, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions(Acceptability.ACCEPTABLE, DescriptionType.SYNONYM, ActiveState.ACTIVE)) {
			//Is this term GB acceptable by contains the word liter?
			if ( d.getTerm().contains("Product containing") && 
					d.getTerm().contains("liter") &&
					d.isAcceptable(GB_ENG_LANG_REFSET)) {
				//Does this term already have US/GB variance?
				boolean hasExistingVariance = c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE).size() > 1;
				
				//Firstly, this should only be acceptable in US english
				d.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(AcceptabilityMode.ACCEPTABLE_US));
				report(task, c, Severity.LOW, ReportActionType.DESCRIPTION_ACCEPTABILIY_CHANGED, d, hasExistingVariance);
				changesMade++;
				
				//Now create a 2nd acceptable description containing litre
				Description additionalDesc = d.clone(null);
				additionalDesc.setTerm(additionalDesc.getTerm().replaceAll("liter", "litre"));
				additionalDesc.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(AcceptabilityMode.ACCEPTABLE_GB));
				if (!c.hasTerm(additionalDesc.getTerm(), "en")) {
					report(task, c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, additionalDesc, hasExistingVariance);
					c.addDescription(additionalDesc);
					changesMade++;
				} else {
					report(task, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Additional term already present", additionalDesc);
				}
			}
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> processMe = new ArrayList<Component>();
		print ("Identifying incorrect case signficance settings");
		this.setQuiet(true);
		for (Concept concept : PHARM_BIO_PRODUCT.getDescendants(NOT_SET)) {
			if (concept.isActive()) {
				if (createAdditionalSynonyms(null, concept.cloneWithIds()) > 0) {
					processMe.add(concept);
				}
			}
		}
		LOGGER.debug("Identified " + processMe.size() + " concepts to process");
		this.setQuiet(false);
		return processMe;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}

}

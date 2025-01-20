package org.ihtsdo.termserver.scripting.util;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AntigenTermGenerator extends TermGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(AntigenTermGenerator.class);

	List<String> nonRedundantTerms; 
	
	public AntigenTermGenerator (TermServerScript parent) {
		this.parent = parent;
	}
	

	public int ensureTermsConform(Task t, Concept c, String X, CharacteristicType charType) throws TermServerScriptException {
		int changesMade = 0;
		nonRedundantTerms = new ArrayList<>();
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			try { 
				changesMade += ensureTermConforms(t, c, X, d, charType);
			} catch (Exception e) {
				String stack = ExceptionUtils.getStackTrace(e);
				report(t, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to conform description " + d, stack);
			}
		}
		//Now that the FSN is resolved, remove any redundant terms
		changesMade += removeRedundantTerms(t,c);
		return changesMade;
	}
	
	private int ensureTermConforms(Task t, Concept c, String X, Description d, CharacteristicType charType) throws TermServerScriptException {
		int changesMade = 0;
		boolean isFSN = d.getType().equals(DescriptionType.FSN);
		boolean isGbPT = d.isPreferred(GB_ENG_LANG_REFSET);
		boolean isUsPT = d.isPreferred(US_ENG_LANG_REFSET);
		boolean isPT = (isGbPT || isUsPT);
		boolean hasUsGbVariance = !(isGbPT == isUsPT);
		
		if (hasUsGbVariance) {
			throw new ValidationFailure(t, c, "Unexpected US/GB variance");
		}
		
		String langRefset = US_ENG_LANG_REFSET;
		if (isGbPT && hasUsGbVariance) {
			langRefset = GB_ENG_LANG_REFSET;
		}
		
		//If it's not the PT or FSN, skip it.   We'll delete later if it's not the FSN counterpart
		if (!isPT && !isFSN ) {
			return NO_CHANGES_MADE;
		}
		
		String replacementTerm = calculateTerm(c, X, isFSN, isPT, langRefset, charType);
		Description replacement = d.clone(null);
		replacement.setTerm(replacementTerm);
		nonRedundantTerms.add(replacementTerm);
		
		//Does the case significance of the term suggest a need to modify the term?
		if (isFSN) {
			replacement.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
		} else if (isPT) {
			replacement.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
		}

		//Synonyms derived from the FSN are to be added
		if (isFSN) {
			String originalCounterpart = SnomedUtils.deconstructFSN(d.getTerm())[0];
			String newCounterpartTerm = SnomedUtils.deconstructFSN(replacement.getTerm())[0];
			
			//If this is the FSN and we've changed it, then retain the original as an acceptable term
			if (!newCounterpartTerm.equals(originalCounterpart)) {
				Description legacyCounterpart = Description.withDefaults(originalCounterpart, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
				legacyCounterpart.setCaseSignificance(d.getCaseSignificance());
				int changeMade = addTerm(t, c, legacyCounterpart);
				if (changeMade > 0) {
					report(t, c, Severity.MEDIUM, ReportActionType.INFO, "Added old FSN as acceptable synonym", originalCounterpart );
					changesMade += changeMade;
				}
				nonRedundantTerms.add(legacyCounterpart.getTerm());
			}
			
			Description newCounterpart = Description.withDefaults(newCounterpartTerm, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
			newCounterpart.setCaseSignificance(replacement.getCaseSignificance());
			changesMade += addTerm(t, c, newCounterpart);
			nonRedundantTerms.add(newCounterpart.getTerm());
			
			String synonymTerm = X + " Ag (antigen)";
			Description synonym = Description.withDefaults(synonymTerm, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
			synonym.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
			changesMade += addTerm(t, c, synonym);
			nonRedundantTerms.add(synonym.getTerm());
		}
		
		//Have we made any changes?  Create a new description if so
		if (!replacementTerm.equals(d.getTerm())) {
			boolean mergeAcceptability = isPT;  //PT might reactive an inactive term, needs acceptability merged.
			changesMade += replaceTerm(t, c, d, replacement, mergeAcceptability); 
		} 
		
		//Validation, check that we have some acceptability for both US and GB
		if (replacement.getAcceptability(US_ENG_LANG_REFSET) == null || replacement.getAcceptability(GB_ENG_LANG_REFSET) == null) {
			LOGGER.warn("{} has unacceptable acceptability", d);
		}
		
		return changesMade;
	}


	public String calculateTerm(Concept c, String X, boolean isFSN, boolean isPT, String langRefset, CharacteristicType charType) throws TermServerScriptException {
		//Have we been given an X to work with?
		String proposedTerm = "";
		if (isFSN) {
			proposedTerm = "Antigen of " + X + " (substance)";
		} else if (isPT) {
			proposedTerm = X + " antigen";
		}
		
		proposedTerm = StringUtils.capitalize(proposedTerm);
		return proposedTerm;
	}


	private int removeRedundantTerms(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		List<Description> allTerms = c.getDescriptions(ActiveState.ACTIVE);
		for (Description d : allTerms) {
			if (!nonRedundantTerms.contains(d.getTerm())) {
				boolean isInactivated = removeDescription(c,d);
				String msg = (isInactivated?"Inactivated redundant desc ":"Deleted redundant desc ") +  d;
				report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_INACTIVATED, msg);
				changesMade++;
			}
		}
		return changesMade;
	}

}

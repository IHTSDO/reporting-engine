package org.ihtsdo.termserver.scripting.reports;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * Reports all concepts that appear to have ingredients, with strength / concentration.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdentifyProductsWithStrengthReport extends TermServerScript{

	private static final Logger LOGGER = LoggerFactory.getLogger(IdentifyProductsWithStrengthReport.class);

	String[] strengthUnitStrs = { "mg/mL", "mg","%","ml", "mL","g","mgi", "milligram","micrograms/mL",
								"micrograms","microgram","units/mL", "units","unit/mL","unit",
								"iu/mL","iu","mcg","million units", 
								"mL", "u/mL", "MBq/mL", "MBq", "gm", "million iu", "million/iu", "unt/g","unt", "nanograms",
								"million iu","L","meq","kBq", "u/mL", "u", "mmol", "ppm", "GBq", "mol", "gr","gram", "umol",
								"molar", "megaunits", "mgI", "million international units", "microliter"};
	
	List<StrengthUnit> strengthUnits = new ArrayList<StrengthUnit>();
	
	String[] concatenatable = { "%" };
	
	Multiset<String> strengthUnitCombos = HashMultiset.create();
	
	public static void main(String[] args) throws TermServerScriptException {
		IdentifyProductsWithStrengthReport report = new IdentifyProductsWithStrengthReport();
		try {
			report.additionalReportColumns = "EffectiveTime, Definition_Status,lexicalMatch, authorIdentified";
			report.init(args);
			report.compileRegexes();
			report.loadProjectSnapshot(true);  //Load FSNs only
			List<Component> authorIdentified = report.processFile();
			report.identifyProductsWithStrength(authorIdentified);
		} catch (Exception e) {
			LOGGER.error("Failed to produce report", e);
		} finally {
			report.finish();
		}
	}


	private void identifyProductsWithStrength(List<Component> authorIdentifiedList) throws TermServerScriptException {
		//For all descendants of 373873005 |Pharmaceutical / biologic product (product)|, 
		//use a number of criteria to determine if concept is a product with strength.
		Set<Concept> products = gl.getConcept("373873005").getDescendants(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP);  //|Pharmaceutical / biologic product (product)|
		Set<Component> remainingFromList = new HashSet<Component> (authorIdentifiedList);
		LOGGER.info("Original List: " + authorIdentifiedList.size() + " deduplicated: " + remainingFromList.size());
		int bothIdentified = 0;
		int lexOnly = 0;
		int authorOnly = 0;
		int conceptsChecked = 0;
		for (Concept c : products) {
			boolean lexicalMatch = termIndicatesStrength(c.getFsn());
			boolean authorIdentified = authorIdentifiedList.contains(c);
			if (lexicalMatch || authorIdentified) {
				report(c, lexicalMatch, authorIdentified);
				remainingFromList.remove(c);
				if (lexicalMatch && authorIdentified) {
					bothIdentified++;
				} else if (lexicalMatch) {
					lexOnly++;
				} else {
					authorOnly++;
				}
			}
			if (conceptsChecked++ % 500 == 0) {
				print (".");
			}
		}
		
		LOGGER.info("\n\nMatching Counts\n===============");
		LOGGER.info("Both agree: " + bothIdentified);
		LOGGER.info("Lexical only: " + lexOnly);
		LOGGER.info("Author only: " + authorOnly);
		
		LOGGER.info("\nOn list but not active in hierarchy (" + remainingFromList.size() + ") : ");
		for (Component lostConcept : remainingFromList) {
			LOGGER.info("  " + lostConcept);
		}
		
		LOGGER.info("\n Strength/Unit combinations: " + strengthUnitCombos.elementSet().size());
		for (String strengthUnit : strengthUnitCombos.elementSet()) {
			LOGGER.info("\t" + strengthUnit + ": " + strengthUnitCombos.count(strengthUnit));
		}
	}

	private boolean termIndicatesStrength(String term) {
		boolean termIndicatesStrength = false;
		String termNoSpaces = term.replace(" ", "");
		for (StrengthUnit strengthUnit : strengthUnits) {
			Matcher matcher = strengthUnit.regex.matcher(termNoSpaces);
			if (matcher.matches()) {
				if (unitCompleteInTerm(strengthUnit.strengthUnitStr, term) && correctlyParsed(strengthUnit.strengthUnitStr, term)) {
					termIndicatesStrength = true;
					strengthUnitCombos.add(matcher.group(1) + ":" + matcher.group(2));
					//Are there any further strengthUnitCombos in this term?
					String remainingString = termNoSpaces.replace( matcher.group(1) + matcher.group(2), "");
					termIndicatesStrength(remainingString);
				}
				continue;
			}
		}
		return termIndicatesStrength;
	}

	//We found the strength unit in the term with the spaces removed, but is it still there intact when the spaces are included?
	//This is to spot cases like "Indium-113m labeling kits (product)"
	private boolean unitCompleteInTerm(String strengthUnit, String fsn) {
		return fsn.contains(strengthUnit);
	}

	//Have we accidentally matched a "g or m" that is part of another word eg Recombinant bone morphogenic protein 7 graft (product)
	private boolean correctlyParsed(String strengthUnit, String term) {
		boolean correctlyParsed = false;
		
		//Is this strengthUnit one that we've said is OK to concatenate with other letters?
		for (String checkUnit : concatenatable) {
			if (checkUnit.equals(strengthUnit)) {
				return true;
			}
		}
		
		//Loop through until we find a number, then keep going while there's more numbers or spaces, then find strengthUnit
		int idx = 0;
		while (idx < term.length() -1 && correctlyParsed == false) {
			while (!Character.isDigit(term.charAt(idx)) && idx < term.length() -1) { idx++; }
			while ((Character.isDigit(term.charAt(idx)) || term.charAt(idx) == ' ' || term.charAt(idx) == '.') && idx < term.length() -1) { idx++; }
			//Do we next have the strength Unit?
			if (term.substring(idx).startsWith(strengthUnit)) {
				idx += strengthUnit.length();
				//Also check if we've reached the end of the string, which is fine too
				//Now check that our strength term isn't part of a longer word, so we want any non-letter eg space, slash, bracket
				if (idx >= term.length() || !Character.isLetter(term.charAt(idx))) {
					correctlyParsed = true;
				}
			}
			idx++;
		}
		return correctlyParsed;
	}

	protected void report(Concept c, boolean lexicalMatch, boolean authorIdentified) throws TermServerScriptException {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA + 
						c.getEffectiveTime() + COMMA_QUOTE +
						c.getDefinitionStatus() + QUOTE_COMMA +
						(lexicalMatch?"YES":"NO") + COMMA +
						(authorIdentified?"YES":"NO");
		writeToReportFile(line);
	}
	
	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return Collections.singletonList(gl.getConcept(lineItems[0]));
	}
	

	private void compileRegexes() {
		for (String strengthUnitStr : strengthUnitStrs) {
			strengthUnits.add(new StrengthUnit(strengthUnitStr));
		}
	}
	
	class StrengthUnit {
		String strengthUnitStr;
		Pattern regex;
		StrengthUnit(String strengthUnit) {
			this.strengthUnitStr = strengthUnit;
			String strengthUnitNoSpaces = strengthUnit.replace(" ", "");
			//Match
			//.*?  As few characters as possible
			//[\d\.\d*] a numeric optionaly followed by a decimal and further characters
			//...followed by the strength unit.
			//Group the number and the unit separately
			this.regex = Pattern.compile(".*?([\\d\\.?\\d*]+)(" + strengthUnitNoSpaces +").*");
		}
	}
	
}

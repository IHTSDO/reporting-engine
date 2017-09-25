package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExportType;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExtractType;
import org.ihtsdo.termserver.scripting.domain.*;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * Reports all concepts that appear to have ingredients, with strength / concentration.
 */
public class IdentifyProductsWithStrengthReport extends TermServerScript{
	
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	GraphLoader gl = GraphLoader.getGraphLoader();
	
	String[] strengthUnitStrs = { "mg/mL", "mg","%","ml", "mL","g","mgi", "milligram","micrograms/mL", 
								"micrograms","microgram","units/mL", "units","unit/mL","unit",
								"iu/mL","iu","mcg","million units", 
								"mL", "u/mL", "MBq/mL", "MBq", "gm", "million iu", "million/iu", "unt/g","unt", "nanograms",
								"million iu","L","meq","kBq", "u/mL", "u", "mmol", "ppm", "GBq", "mol", "gr","gram", "umol",
								"molar", "megaunits", "mgI", "million international units", "microliter"};
	
	List<StrengthUnit> strengthUnits = new ArrayList<StrengthUnit>();
	
	String[] concatenatable = { "%" };
	
	Multiset<String> strengthUnitCombos = HashMultiset.create();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		IdentifyProductsWithStrengthReport report = new IdentifyProductsWithStrengthReport();
		try {
			report.init(args);
			report.compileRegexes();
			report.loadProjectSnapshot();  //Load FSNs only
			List<Concept> authorIdentified = report.processFile();
			report.identifyProductsWithStrength(authorIdentified);
		} catch (Exception e) {
			println("Failed to validate laterality due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}


	private void identifyProductsWithStrength(List<Concept> authorIdentifiedList) throws TermServerScriptException {
		//For all descendants of 373873005 |Pharmaceutical / biologic product (product)|, 
		//use a number of criteria to determine if concept is a product with strength.
		Set<Concept> products = gl.getConcept("373873005").getDescendents(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE);  //|Pharmaceutical / biologic product (product)|
		Set<Concept> remainingFromList = new HashSet<Concept> (authorIdentifiedList);
		println ("Original List: " + authorIdentifiedList.size() + " deduplicated: " + remainingFromList.size());
		int bothIdentified = 0;
		int lexOnly = 0;
		int authorOnly = 0;
		int conceptsChecked = 0;
		for (Concept c : products) {
			boolean lexicalMatch = termIndicatesStrength(c.getFsn());
			boolean authorIdentified = authorIdentifiedList.contains(c);
			if (lexicalMatch || authorIdentified) {
				report (c, lexicalMatch, authorIdentified);
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
		
		println ("\n\nMatching Counts\n===============");
		println ("Both agree: " + bothIdentified);
		println ("Lexical only: " + lexOnly);
		println ("Author only: " + authorOnly);
		
		println("\nOn list but not active in hierarchy (" + remainingFromList.size() + ") : ");
		for (Concept lostConcept : remainingFromList) {
			println ("  " + lostConcept);
		}
		
		println("\n Strength/Unit combinations: " + strengthUnitCombos.elementSet().size());
		for (String strengthUnit : strengthUnitCombos.elementSet()) {
			println ("\t" + strengthUnit + ": " + strengthUnitCombos.count(strengthUnit));
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

	protected void report (Concept c, boolean lexicalMatch, boolean authorIdentified) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA + 
						c.getEffectiveTime() + COMMA_QUOTE +
						c.getDefinitionStatus() + QUOTE_COMMA +
						(lexicalMatch?"YES":"NO") + COMMA +
						(authorIdentified?"YES":"NO");
		writeToFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException, SnowOwlClientException {
		super.init(args);
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		//String reportFilename = "changed_relationships_" + project.getKey().toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		String reportFilename = getScriptName() + "_" + project.getKey().toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToFile ("Concept, FSN, EffectiveTime, Definition_Status,lexicalMatch, authorIdentified");
	}

	private void loadProjectSnapshot() throws SnowOwlClientException, TermServerScriptException, InterruptedException {
		int SNAPSHOT = 0;
		File[] archives = new File[] { new File (project + "_snapshot_" + env + ".zip")};

		//Do we already have a copy of the project locally?  If not, recover it.
		if (!archives[SNAPSHOT].exists()) {
			println ("Recovering snapshot state of " + project + " from TS (" + env + ")");
			tsClient.export("MAIN/" + project, null, ExportType.MIXED, ExtractType.SNAPSHOT, archives[SNAPSHOT]);
			initialiseSnowOwlClient();  //re-initialise client to avoid HttpMediaTypeNotAcceptableException.  Cause unknown.
		}
		
		println ("Loading snapshot into memory...");
		for (File archive : archives) {
			try {
				ZipInputStream zis = new ZipInputStream(new FileInputStream(archive));
				ZipEntry ze = zis.getNextEntry();
				try {
					while (ze != null) {
						if (!ze.isDirectory()) {
							Path p = Paths.get(ze.getName());
							String fileName = p.getFileName().toString();
							if (fileName.contains("sct2_Description_Snapshot")) {
								println("Loading Description File.");
								gl.loadDescriptionFile(zis, true);  //Load FSNs only
							}
							
							if (fileName.contains("sct2_Concept_Snapshot")) {
								println("Loading Concept File.");
								gl.loadConceptFile(zis);
							}
							
							if (fileName.contains("sct2_Relationship_Snapshot")) {
								println("Loading Relationship Snapshot File.");
								gl.loadRelationships(CharacteristicType.INFERRED_RELATIONSHIP,zis, true);
							}
							
							if (fileName.contains("sct2_StatedRelationship_Snapshot")) {
								println("Loading Stated Relationship Snapshot File.");
								gl.loadRelationships(CharacteristicType.STATED_RELATIONSHIP,zis, true);
							}

						}
						ze = zis.getNextEntry();
					}
				} finally {
					try{
						zis.closeEntry();
						zis.close();
					} catch (Exception e){} //Well, we tried.
				}
			} catch (IOException e) {
				throw new TermServerScriptException("Failed to extract project state from archive " + archive.getName(), e);
			}
		}
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return gl.getConcept(lineItems[0]);
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

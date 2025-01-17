package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.google.common.io.Files;

/**
 * SUBST-130, SUBST-299
 * Class to work with the cs_words.txt file
 * 
 * We're going to follow the following:
 * 1. We don't need any Substance or Organism terms as those hierarchies will be taken as the source of truth
 * 2. Where a word is case sensitive, any phrase that starts with that word can be removed, since the first word will cover them all
 * 3. Where a word exists as variants, we can take the first word and add a wildcard to indicate all words that start this way are covered
 * 4. Where a word or phrase has a capital letter after the first letter, we can remove it since processing rules
 *    would already identify such as term as being case sensitive.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CaseSensitivityTextFileEditor extends TermServerReport{

	private static final Logger LOGGER = LoggerFactory.getLogger(CaseSensitivityTextFileEditor.class);

	Set<String> organismTerms = new HashSet<>();
	Set<String> substanceTerms = new HashSet<>();
	Map<String, Description> allWordsUsedActively = new HashMap<>();
	SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
	String lastWildcardWritten = "";
	public static final String WILDCARD = "*";
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		CaseSensitivityTextFileEditor report = new CaseSensitivityTextFileEditor();
		try {
			ReportSheetManager.targetFolderId = "1bwgl8BkUSdNDfXHoL__ENMPQy_EdEP7d"; //Substances
			report.additionalReportColumns = "Phrase, Action, Data, Additional Info";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			LOGGER.info ("Modifying CS Words file...");
			report.processCSWordsFile();
		} finally {
			report.finish();
		}
	}
	
	@Override
	public void postInit() throws TermServerScriptException {
		LOGGER.info("Collecting organism terms...");
		for (Concept c : ORGANISM.getDescendants(NOT_SET)) {
			for (Description d : c.getDescriptions(Acceptability.PREFERRED, null, ActiveState.ACTIVE)) {
				organismTerms.add(d.getTerm());
			}
		}
		
		LOGGER.info("Collecting substance terms...");
		for (Concept c : SUBSTANCE.getDescendants(NOT_SET)) {
			for (Description d : c.getDescriptions(Acceptability.PREFERRED, null, ActiveState.ACTIVE)) {
				substanceTerms.add(d.getTerm());
			}
		}
		
		LOGGER.info("Collecting all words currently in use....");
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActiveSafely()) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					String[] words = d.getTerm().toLowerCase().split(SPACE);
					for (String word : words) {
						if (!allWordsUsedActively.containsKey(word)) {
							allWordsUsedActively.put(word, d);
						}
					}
				}
			}
		}
		super.postInit();
	}

	private void processCSWordsFile() throws IOException, TermServerScriptException {
		LOGGER.info ("Processing {}", getInputFile());
		String timeStamp = df.format(new Date());
		String outputFileName = getInputFile().getAbsolutePath().replace(".txt", "_" + timeStamp + ".txt");
		File outputFile = new File(outputFileName);
		if (!getInputFile().canRead()) {
			throw new TermServerScriptException ("Cannot read: " + getInputFile());
		}
		List<String> lines = Files.readLines(getInputFile(), StandardCharsets.UTF_8);
		int linesWritten = 0;
		String lastLineWritten = "";
		for (int i=1; i<lines.size() - 1; i++) {
			//If the previous line was written, then we can pass it in.  
			//Otherwise it's not available 
			boolean lineWritten = processLine(lines.get(i), lastLineWritten, lines.get(i+1), outputFile);
			if (lineWritten) {
				lastLineWritten = lines.get(i);
				linesWritten++;
			}
		}
		LOGGER.info ("Lines read: {}", lines.size());
		LOGGER.info ("Lines written: {}", linesWritten);
		LOGGER.info ("Output file: {}", outputFileName);
	}

	private boolean processLine(String line, String lastLineWritten, String nextLine, File outputFile) throws TermServerScriptException, IOException {
		//Split the line up on tabs
		String[] lineItems = line.split(TAB);
		String[] previousLineItems = lastLineWritten.split(TAB);
		String[] nextLineItems = nextLine.split(TAB);
		
		String phrase = lineItems[0];
		String source = lineItems.length > 1 ? lineItems[1] : null; 
		;
		String phraseToWrite = phrase;
		String previousPhrase = previousLineItems[0];
		String nextPhrase = nextLineItems[0];
		String chopped = phrase.substring(1);
		
		//Does the word contain a capital letter (ie not the same as it's all lower case variant)
		if (!chopped.equals(chopped.toLowerCase())) {
			report (null, phrase , "Removed", "Has captial after initial letter, algorithm can spot this");
			return false;
		}
		
		if (organismTerms.contains(phrase)) {
			report (null, phrase , "Removed", "Is Organism - not required as source of truth");
			return false;
		}
		
		if (substanceTerms.contains(phrase)) {
			report (null, phrase , "Removed", "Is Substance - not required as source of truth");
			return false;
		}
		
		//Is this a phrase?
		String[] previousWords = previousPhrase.split(" ");
		String[] words = phrase.split(" ");
		String[] nextWords = nextPhrase.split(" ");
				
		//Can this word be removed by the previous wildcard?
		if (!lastWildcardWritten.isEmpty() && words[0].startsWith(lastWildcardWritten.substring(0, lastWildcardWritten.length() - 1))) {
			report (null, phrase , "Removed", "'" + phrase + "' is covered by " + lastWildcardWritten);
			return false;
		}
		
		//Now is this phrase more than one word and the first word is contained in the previous line?
		if (words[0].equals(previousWords[0])) {
			report (null, phrase , "Removed", "Is contained in previous line - '" + previousPhrase + "'");
			return false;
		}
		
		Description[] usedIn = new Description[1];
		if (isCurrentlyUsed(words, usedIn)) {
			//If the next first word is contained in this word, then we can save a wildcard variant.  But only if it's 4 letters long
			//And not if it's an exact match
			if (!words[0].equals(nextWords[0]) && words[0].length() > 4 && nextWords[0].startsWith(words[0])) {
				phraseToWrite = words[0] + "*";
				lastWildcardWritten = phraseToWrite;
				report (null, phraseToWrite , "Wildcard", words[0] + " in '" + nextPhrase + "' can covered by " + phraseToWrite, "Used in: " + usedIn[0]);
			} else {
				//Is there a case mismatch with how we're actually using it?
				String mismatchFlag = usedIn[0].getTerm().contains(phrase.replaceAll("\\*", ""))?"":"** CHECK CASE **";
				report (null, phrase, "Kept", "Used in: " + usedIn[0], mismatchFlag);
			}
		} else {
			//Does it have a source - eg country, that we like?
			if (source == null) {
				report (null, phrase, "Not in Use");
				return false;
			} else {
				report (null, phrase, "Kept", "Source: " + source);
				FileUtils.writeStringToFile( outputFile, phraseToWrite + (lineItems.length > 1 ? TAB + lineItems[1] : "") + "\n" , StandardCharsets.UTF_8, true);
			}
		}
		FileUtils.writeStringToFile( outputFile, phraseToWrite + (lineItems.length > 1 ? TAB + lineItems[1] : "") + "\n" , StandardCharsets.UTF_8, true);
		return true;
	}

	private boolean isCurrentlyUsed(String[] words, Description[] usedIn) throws TermServerScriptException {
		//Are any of the component words used somewhere in active SNOMED?
		for (String word : words) {
			word = word.toLowerCase();
			//Are we having to do a wildcard match?
			if (word.endsWith(WILDCARD)) {
				String startMatch = word.substring(0, word.length() - 1);
				for (String wordInUse : allWordsUsedActively.keySet()) {
					if (wordInUse.startsWith(startMatch)) {
						usedIn[0] = allWordsUsedActively.get(wordInUse);
						return true;
					}
				}
			} else if (allWordsUsedActively.containsKey(word)) {
				usedIn[0] = allWordsUsedActively.get(word);
				return true;
			} 
		}
		return false;
	}

}

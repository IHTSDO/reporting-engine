package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.domain.*;

import com.google.common.base.Charsets;
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
public class CaseSensitivityTextFileEditor extends TermServerReport{
	
	Set<String> organismTerms = new HashSet<>();
	Set<String> substanceTerms = new HashSet<>();
	SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
	String lastWildcardWritten = "";
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		CaseSensitivityTextFileEditor report = new CaseSensitivityTextFileEditor();
		try {
			report.additionalReportColumns = ", Phrase, Action, Reason";
			report.init(args);
			report.getReportManager().setWriteToFile(true);
			report.getReportManager().setWriteToSheet(false);
			report.getArchiveManager().allowStaleData = true;
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			info ("Modifying CS Words file...");
			report.processCSWordsFile();
		} finally {
			report.finish();
		}
	}

	protected void init(String[] args) throws TermServerScriptException, SnowOwlClientException {
		super.init(args);
	}
	
	private void postInit() throws TermServerScriptException {
		info("Collecting organism terms...");
		for (Concept c : ORGANISM.getDescendents(NOT_SET)) {
			for (Description d : c.getDescriptions(Acceptability.PREFERRED, null, ActiveState.ACTIVE)) {
				organismTerms.add(d.getTerm());
			}
		}
		
		info("Collecting substance terms...");
		for (Concept c : SUBSTANCE.getDescendents(NOT_SET)) {
			for (Description d : c.getDescriptions(Acceptability.PREFERRED, null, ActiveState.ACTIVE)) {
				substanceTerms.add(d.getTerm());
			}
		}
	}

	private void processCSWordsFile() throws IOException, TermServerScriptException {
		info ("Processing " + inputFile);
		String timeStamp = df.format(new Date());
		String outputFileName = inputFile.getAbsolutePath().replace(".txt", "_" + timeStamp + ".txt");
		File outputFile = new File(outputFileName);
		if (!inputFile.canRead()) {
			throw new TermServerScriptException ("Cannot read: " + inputFile);
		}
		List<String> lines = Files.readLines(inputFile, Charsets.UTF_8);
		int linesWritten = 0;
		for (int i=1; i<lines.size() - 1; i++) {
			if (processLine(lines.get(i), lines.get(i-1), lines.get(i+1), outputFile)) {
				linesWritten++;
			}
		}
		info ("Lines read: " + lines.size());
		info ("Lines written: " + linesWritten);
		info ("Output file: " + outputFileName);
	}

	private boolean processLine(String line, String preivousLine, String nextLine, File outputFile) throws TermServerScriptException, IOException {
		//Split the line up on tabs
		String[] lineItems = line.split(TAB);
		String[] previousLineItems = preivousLine.split(TAB);
		String[] nextLineItems = nextLine.split(TAB);
		
		String phrase = lineItems[0];
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
		
		//If the next first word is contained in this word, then we can save a wildcard variant.  But only if it's 4 letters long
		//And not if it's an exact match
		if (!words[0].equals(nextWords[0]) && words[0].length() > 4 && nextWords[0].startsWith(words[0])) {
			phraseToWrite = words[0] + "*";
			lastWildcardWritten = phraseToWrite;
			report (null, phrase , "Wildcard", words[0] + " in '" + nextPhrase + "' can covered by " + phraseToWrite);
		} 
		
		report (null, phraseToWrite , "Kept", "");
		FileUtils.writeStringToFile( outputFile, phraseToWrite + (lineItems.length > 1 ? TAB + lineItems[1] : "") + "\n" , StandardCharsets.UTF_8, true);
		return true;
	}

}

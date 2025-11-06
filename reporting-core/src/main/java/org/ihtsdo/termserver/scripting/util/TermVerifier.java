package org.ihtsdo.termserver.scripting.util;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * Class reads in a spreadsheet of terms from a file and uses it to validate
 * active descriptions of concepts
 * @author Peter
 *
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TermVerifier implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(TermVerifier.class);

	Map<Concept, String[]> conceptTermsMap;
	
	public static int idx_sctid = 0;
	public static int idx_fsn = 2;
	public static int idx_us = 3;
	public static int idx_syn = 4;
	public static int idx_gb = 5;
	
	TermServerScript script;
	File inputFile;
	
	public TermVerifier (File inputFile, TermServerScript script) {
		this.inputFile = inputFile;
		this.script = script;
	}
	
	public void init() throws TermServerScriptException {
		conceptTermsMap = new HashMap<>();
		String[] lineItems;
		
		if (inputFile == null) {
			throw new TermServerScriptException ("No file specified as input to verify terms");
		}
		LOGGER.info("Loading term file {}", inputFile.getAbsolutePath());
		try {
			List<String> lines = Files.readLines(inputFile, Charsets.UTF_8);
			lines = StringUtils.removeBlankLines(lines);
			for (int lineNum = 0; lineNum < lines.size(); lineNum++) {
				if (lineNum == 0) {
					continue; //skip header row  
				}
				lineItems = lines.get(lineNum).replace("\"", "").split(TermServerScript.inputFileDelimiter);
				Concept c = GraphLoader.getGraphLoader().getConcept(lineItems[idx_sctid]);
				conceptTermsMap.put(c, lineItems);
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to load " + inputFile, e);
		}
	}
	
	public void validateTerms(Task t, Concept c) throws TermServerScriptException {
		if (conceptTermsMap.containsKey(c)) {
			String[] terms = conceptTermsMap.get(c);
			validateTerm (t, c, c.getFSNDescription(), terms[idx_fsn], true);
			validateTerm (t, c, c.getPreferredSynonym(US_ENG_LANG_REFSET), terms[idx_us], false);
			validateTerm (t, c, c.getPreferredSynonym(GB_ENG_LANG_REFSET), terms[idx_gb], false);
			
			//And try to find the synonym
			String synonym = fixIssues(terms[idx_syn], false);
			if (c.findTerm(synonym) == null) {
				String msg = "Unable to find suggested synonym: " + synonym;
				script.report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
			}
		} else {
			String msg = "No suggested term supplied for comparison";
			script.report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, msg);
		}
	}

	private void validateTerm(Task t, Concept c, Description d, String suggestedTerm, boolean isFSN) throws TermServerScriptException {
		suggestedTerm = fixIssues(suggestedTerm, isFSN);
		if (d == null) {
			String msg = c + " does not contain a description to validate against suggestion: '" + suggestedTerm + "'";
			script.report(t, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, msg);
		} else if (!d.getTerm().equals(suggestedTerm)) {
			String msg = "Description " + d + " does not match suggested value '" + suggestedTerm + "'";
			script.report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
		}
	}

	private String fixIssues(String term, boolean isFSN) {
		//Fix known issues 
		term = term.replace(',', '.');
		
		if (!isFSN) {
			term.replaceAll("milligram", "mg");
		}
		
		term = term.replaceAll(" only ", " precisely ");
		
		//Do we have milligram without a space?
		if (org.apache.commons.lang.StringUtils.countMatches(term, "milligram") != 
				org.apache.commons.lang.StringUtils.countMatches(term, " milligram ")) {
			term = term.replaceAll("milligram", "milligram ");
			term = term.replace("  ", " ");
			//Also fix the one case where we want no space after milligram
			term = term.replaceAll("milligram /1 each", "milligram/1 each");
		}
		return term;
	}

	public void replace(Concept original, Concept alternative) {
		String[] values = conceptTermsMap.get(original);
		conceptTermsMap.remove(original);
		conceptTermsMap.put(alternative, values);
	}

}

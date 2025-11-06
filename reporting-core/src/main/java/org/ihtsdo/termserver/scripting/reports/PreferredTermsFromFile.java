package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * Reports all concepts that contains an international term without the national equivalent, or vis versa.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreferredTermsFromFile extends TermServerScript{

	private static final Logger LOGGER = LoggerFactory.getLogger(PreferredTermsFromFile.class);

	List<Concept> conceptFilter;
	
	public static void main(String[] args) throws TermServerScriptException {
		PreferredTermsFromFile report = new PreferredTermsFromFile();
		try {
			report.additionalReportColumns = "Desc_SCTID, Term, USPref, GBPref";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.reportPreferredTerms();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report", e);
		} finally {
			report.finish();
		}
	}
	
	private void reportPreferredTerms() throws TermServerScriptException {
		for (Concept concept : conceptFilter) {
			for (Description d : concept.getDescriptions(ActiveState.ACTIVE)) {
				if (d.isPreferred() && !d.getType().equals(DescriptionType.FSN)) {
					String[] acceptabilities = SnomedUtils.translateLangRefset(d);
					report(concept, d, acceptabilities[0], acceptabilities[1] );
				}
			}
		}
	}

	protected void report(Concept c, Description pt, String usPref, String gbPref) throws TermServerScriptException {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA_QUOTE + 
						pt.getDescriptionId() + QUOTE_COMMA_QUOTE +
						pt.getTerm() + QUOTE_COMMA_QUOTE +
						usPref + QUOTE_COMMA_QUOTE +
						gbPref + QUOTE;
		writeToReportFile(line);
	}
	
	protected void init(String[] args) throws TermServerScriptException {
		super.init(args);
		boolean fileLoaded = false;
		for (int i=0; i < args.length; i++) {
			if (args[i].equalsIgnoreCase("-z")) {
				loadConceptsSelected(args[i+1]);
				fileLoaded = true;
			}
		}
		
		if (!fileLoaded) {
			LOGGER.info("Failed to concept filter file to load.  Specify path with 'z' command line parameter");
			System.exit(1);
		}
	}

	private void loadConceptsSelected(String fileName) throws TermServerScriptException {
		try {
			File nationalTerms = new File(fileName);
			List<String> lines = Files.readLines(nationalTerms, Charsets.UTF_8);
			LOGGER.info("Loading selected Concepts from " + fileName);
			conceptFilter = new ArrayList<Concept>();
			for (String line : lines) {
				conceptFilter.add(gl.getConcept(line));
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to import concept selection file " + fileName, e);
		}
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}

}

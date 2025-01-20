package org.ihtsdo.termserver.scripting.reports;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;

/**
 * DRUGS-453 A report to list acceptable synonyms from a subset of concepts 
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListAcceptableSynonyms extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(ListAcceptableSynonyms.class);

	List<Component> conceptsOfInterest = new ArrayList<Component>();
	
	public static void main(String[] args) throws TermServerScriptException {
		ListAcceptableSynonyms report = new ListAcceptableSynonyms();
		try {
			report.additionalReportColumns="DescId, Term";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.conceptsOfInterest = report.processFile();
			report.listAcceptableSynonyms();
		} catch (Exception e) {
			LOGGER.info("Failed to ListAcceptableSynonyms due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}


	//Find all concepts that are a target of the attribute type we're interested in
	private void listAcceptableSynonyms() throws TermServerScriptException {
		
		for (Component comp : conceptsOfInterest) {
			Concept c = (Concept) comp;
			if (c.getFsn() == null) {
				report(c, "Concept not yet known in project");
			}
			if (c.isActive()) {
				List<Description> descriptions = c.getDescriptions(Acceptability.ACCEPTABLE, DescriptionType.SYNONYM, ActiveState.ACTIVE);
				for (Description d : descriptions) {
					report(c, d.getDescriptionId(), d.getTerm());
				}
			}
		}
	}

	
}



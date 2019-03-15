package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * DRUGS-453 A report to list acceptable synonyms from a subset of concepts 
 */
public class ListAcceptableSynonyms extends TermServerReport {
	
	List<Component> conceptsOfInterest = new ArrayList<Component>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException {
		ListAcceptableSynonyms report = new ListAcceptableSynonyms();
		try {
			report.additionalReportColumns="DescId, Term";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.conceptsOfInterest = report.processFile();
			report.listAcceptableSynonyms();
		} catch (Exception e) {
			info("Failed to ListAcceptableSynonyms due to " + e.getMessage());
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
				report (c, "Concept not yet known in project");
			}
			if (c.isActive()) {
				List<Description> descriptions = c.getDescriptions(Acceptability.ACCEPTABLE, DescriptionType.SYNONYM, ActiveState.ACTIVE);
				for (Description d : descriptions) {
					report (c, d.getDescriptionId(), d.getTerm());
				}
			}
		}
	}

	
}



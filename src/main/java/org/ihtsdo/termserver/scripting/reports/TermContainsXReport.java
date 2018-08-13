package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * FD19459
 * Reports all terms that contain the specified text
 * Optionally only report the first description matched for each concept
 * 
 * CTR-19 Match organism taxon
 */
public class TermContainsXReport extends TermServerReport {
	
	//String[] textsToMatch = new String[] {"remission", "diabet" };
	String[] textsToMatch = new String[] { "Clade","Class","Division",
								"Domain","Family","Genus","Infraclass",
								"Infraclass","Infrakingdom","Infraorder",
								"Infraorder","Kingdom","Order","Phylum",
								"Species","Subclass","Subdivision",
								"Subfamily","Subgenus","Subkingdom",
								"Suborder","Subphylum","Subspecies",
								"Superclass","Superdivision","Superfamily",
								"Superkingdom","Superorder"};
	boolean reportConceptOnceOnly = true;
	Concept subHierarchy = ORGANISM;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		TermContainsXReport report = new TermContainsXReport();
		try {
			//report.headers="Concept, FSN, TermMatched, TopLevelHierarchy, SubHierarchy, SpecificHierarchy";
			report.additionalReportColumns = "FSN, TermMatched, Case, SubHierarchy, SubSubHierarchy";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.reportDescriptionContainsX();
		} catch (Exception e) {
			info("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void reportDescriptionContainsX() throws TermServerScriptException {
		nextConcept:
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			if (c.isActive()) {
				//for (Description d : c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE)) {
				//for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					boolean reported = false;
					for (String matchText : textsToMatch) {
						//Wrap fsn with a space to ensure we can do whole word matches, even with 
						//word at the start of the line
						//String fsn = " " + c.getFsn().toLowerCase();
						//String match = " " + matchText.toLowerCase() + " ";
						
						if (c.getFsn().toLowerCase().startsWith(matchText.toLowerCase())) {
						//if (fsn.contains(match)) {
						//if (d.getTerm().toLowerCase().contains(matchText.toLowerCase())) {
							//String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
							String[] hiearchies = getHierarchies(c);
							String cs = SnomedUtils.translateCaseSignificanceFromEnum(c.getFSNDescription().getCaseSignificance());
							report(c, matchText, cs/*hiearchies[0]*/, hiearchies[1], hiearchies[2]);
							reported = true;
							incrementSummaryInformation("Matched " + matchText);
							//incrementSummaryInformation( "Tag: " + semTag);
						}
					}
					if (reported && reportConceptOnceOnly) {
						continue nextConcept;
					}
				//}
			}
		}
		
	}

	//Return hierarchy depths 1, 2, 3
	private String[] getHierarchies(Concept c) throws TermServerScriptException {
		String[] hierarchies = new String[3];
		Set<Concept> ancestors = c.getAncestors(NOT_SET);
		for (Concept ancestor : ancestors) {
			int depth = ancestor.getDepth();
			if (depth > 0 && depth < 4) {
				hierarchies[depth -1] = ancestor.getPreferredSynonym(US_ENG_LANG_REFSET).getTerm();
			}
		}
		return hierarchies;
	}

}

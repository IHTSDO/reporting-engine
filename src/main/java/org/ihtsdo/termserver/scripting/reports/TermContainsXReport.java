package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * FD19459
 * Reports all terms that contain the specified text
 */
public class TermContainsXReport extends TermServerReport {
	
	List<String> criticalErrors = new ArrayList<String>();
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	String[] textsToMatch = new String[] {"remission", "diabet" };
	boolean reportConceptOnceOnly = true;
	
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		TermContainsXReport report = new TermContainsXReport();
		try {
			//report.headers="Concept, FSN, Sem_Tag, Desc_SCTID, Term";
			report.headers="Concept, FSN, Sem_Tag, TermMatched, TopLevelHierarchy, SubHierarchy, SpecificHierarchy";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.reportDescriptionContainsX();
		} catch (Exception e) {
			println("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
			for (String err : report.criticalErrors) {
				println (err);
			}
		}
	}

	private void reportDescriptionContainsX() throws TermServerScriptException {
		Collection<Concept> concepts = gl.getAllConcepts();
		Multiset<String> tags = HashMultiset.create();
		
		nextConcept:
		for (Concept c : concepts) {
			if (c.isActive()) {
				//for (Description d : c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE)) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					boolean reported = false;
					for (String matchText : textsToMatch) {
						if (d.getTerm().toLowerCase().contains(matchText)) {
							String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
							String[] hiearchies = getHierarchies(c);
							report(c, semTag, matchText, hiearchies[0], hiearchies[1], hiearchies[2]);
							reported = true;
							tags.add(semTag);
							incrementSummaryInformation("Matched " + matchText);
						}
					}
					if (reported) {
						continue nextConcept;
					}
				}
			}
		}
		
		addSummaryInformation("Concepts checked", concepts.size());
		addSummaryInformation("Matching Descriptions", tags.size());
		for (String tag : tags.elementSet()) {
			addSummaryInformation( "Tag: " + tag , tags.count(tag));
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

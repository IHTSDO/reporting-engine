package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * Attempts to find matching acids ("x acid") and bases ("--ate")
 */
public class SubstanceAcidWithBase extends TermServerScript{
	
	String subHierarchyStr = "105590001";  // Substance (substance)
	Set<Concept> reported = new HashSet<Concept>();
	
	static final String acid = "acid";
	static final String ate = "ate";
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		SubstanceAcidWithBase report = new SubstanceAcidWithBase();
		try {
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.reportAcidsWithBases();
		} catch (Exception e) {
			println("Failed to produce SubstanceAcidWithBase Report due to " + e.getMessage());
			e.printStackTrace();
		} 
	}

	private void reportAcidsWithBases() throws TermServerScriptException {
		Concept subHierarchy = GraphLoader.getGraphLoader().getConcept(subHierarchyStr);
		Collection<Concept> concepts = subHierarchy.getDescendents(NOT_SET);
		
		for (Concept c : concepts) {
			//Skip any we've already reported as being the matched partners
			if (reported.contains(c)) {
				continue;
			}
			boolean isBase = false;
			boolean isAcid = false;
			if (c.isActive()) {
				String term = SnomedUtils.deconstructFSN(c.getFsn())[0].toLowerCase();
				String[] parts = term.split(SPACE);
				for (String part : parts) {
					if (part.equals(acid)) {
						isAcid = true;
					}
					if (part.endsWith(ate)) {
						isBase = true;
					}
				}
				
				if (isAcid && isBase) {
					report (c, c, "Confusion");
				} else {
					if (isAcid) {
						findPartner(c, false);
					} else if (isBase) {
						findPartner(c, true);
					}
				}
			}
		}
		addSummaryInformation("Concepts checked", concepts.size());
	}

	//Given a base or acid, searches up and down parents and descendants
	//to find the alternative form and indicates direction.
	private void findPartner(Concept c, boolean findAcid) throws TermServerScriptException {
		Set<Concept> parents = c.getAncestors(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE, false);
		String direction = findAcid? "Downstream" : "Upstream";
		boolean foundPartnerInParents = false;
		foundPartnerInParents = findPartner( c, parents, direction, findAcid);
		
		boolean foundPartnerInChildren = false;
		Set<Concept> children = c.getDescendents(NOT_SET);
		direction = findAcid? "Upstream" : "Downstream" ;
		foundPartnerInChildren = findPartner( c, children, direction, findAcid);
	
		if (!foundPartnerInParents && !foundPartnerInChildren) {
			report (findAcid?null:c, findAcid?c:null, "Unmatched");
		}
	}

	private boolean findPartner(Concept matchMe, Set<Concept> parents, String direction, boolean findAcid) {
		boolean found = false;
		for (Concept c : parents) {
			if (c.isActive()) {
				String term = SnomedUtils.deconstructFSN(c.getFsn())[0].toLowerCase();
				String[] parts = term.split(SPACE);
				for (String part : parts) {
					if (part.equals(acid) && findAcid) {
						report (c,matchMe, direction);
						reported.add(c);
						found = true;
					}
					if (part.endsWith(ate) && !findAcid) {
						report (matchMe, c, direction);
						reported.add(c);
						found = true;
					}
				}
			}
		}
		return found;
	}

	protected void report (Concept acid, Concept base, String notes) {
		String line =	(acid==null?"":acid.getConceptId()) + COMMA_QUOTE + 
						(acid==null?"":acid.getFsn()) + QUOTE_COMMA + 
						(base==null?"":base.getConceptId()) + COMMA_QUOTE + 
						(base==null?"":base.getFsn()) + QUOTE_COMMA_QUOTE +
						notes + QUOTE;
		writeToFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException {
		super.init(args);
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String reportFilename = getScriptName() + "_" + project.toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToFile ("Acid_SCTID, Acid FSN, Base_SCTID, Base FSN, Notes");
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}
}

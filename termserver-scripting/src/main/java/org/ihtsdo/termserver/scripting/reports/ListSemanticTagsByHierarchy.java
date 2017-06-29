package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * Lists all semantic tags used in each of the top level hierarchies.
 */
public class ListSemanticTagsByHierarchy extends TermServerScript{
	
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	GraphLoader gl = GraphLoader.getGraphLoader();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		ListSemanticTagsByHierarchy report = new ListSemanticTagsByHierarchy();
		try {
			report.init(args);
			report.loadProjectSnapshot(true);  //Load FSNs only
			report.listSemanticTags();
		} catch (Exception e) {
			println("Failed to validate laterality due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void listSemanticTags() throws TermServerScriptException {
		//Work through all top level hierarchies and list semantic tags along with their counts
		Concept rootConcept = gl.getConcept(SCTID_ROOT_CONCEPT.toString());
		for (Concept topLevel : rootConcept.getDescendents(IMMEDIATE_CHILD)) {
			Set<Concept> descendents = topLevel.getDescendents(NOT_SET);
			println (topLevel.toString() + " - total: " + descendents.size());
			Multiset<String> tags = HashMultiset.create();
			for (Concept thisDescendent : descendents) {
				tags.add(SnomedUtils.deconstructFSN(thisDescendent.getFsn())[1]);
			}
			for (String tag : tags.elementSet()) {
				println ("\t" + tag + ": " + tags.count(tag));
			}
		}
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return gl.getConcept(lineItems[0]);
	}
}

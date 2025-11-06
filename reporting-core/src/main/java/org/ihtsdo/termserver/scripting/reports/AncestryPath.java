package org.ihtsdo.termserver.scripting.reports;

import java.io.PrintStream;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * This class attempts to answer the question: If you KNOW a concept is a member of 
 * some subhierarchy, how do you trace a path from one to the other?
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AncestryPath extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(AncestryPath.class);

	Concept subHierarchy;
	Concept conceptOfInterest;
	
	public static void main(String[] args) throws TermServerScriptException {
		AncestryPath report = new AncestryPath();
		try {
			report.additionalReportColumns = "FSN";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.runReport();
		} catch (Exception e) {
			LOGGER.info("Failed to produce StatedNotInferred Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void runReport() throws TermServerScriptException {
		subHierarchy = gl.getConcept("416462003 |Wound (disorder)|");
		conceptOfInterest = gl.getConcept("283755007 |Dog bite of dorsum of hand (disorder)|");
		if (!gl.getDescendantsCache().getDescendantsOrSelf(subHierarchy).contains(conceptOfInterest)) {
			LOGGER.warn(conceptOfInterest + " is not subsumed by " + subHierarchy);
		} else {
			pathWalk(conceptOfInterest);
		}
	}

	private boolean pathWalk(Concept currentLocation) throws TermServerScriptException {
		//Have we got to the top of the subHierarchy?
		if (currentLocation.equals(subHierarchy)) {
			return true;
		} else {
			//Look at all my parents
			for (Concept parent : currentLocation.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
				//Does this parent lead to the top of the subhierarchy?
				if (pathWalk(parent)) {
					LOGGER.info (parent.toString());
					report(parent);
					return true;
				}
			}
		}
		return false;
	}
}

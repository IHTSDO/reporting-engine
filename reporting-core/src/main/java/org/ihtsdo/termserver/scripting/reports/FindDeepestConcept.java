package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Answer a quick question: What is the deepest concept ie via longest path from Root?
 */
public class FindDeepestConcept extends TermServerReport {

	Logger logger = LoggerFactory.getLogger(FindDeepestConcept.class);
	public static void main(String[] args) throws TermServerScriptException, InterruptedException {
		FindDeepestConcept report = new FindDeepestConcept();
		report.init(args);
		report.loadProjectSnapshot(false);  //Load all descriptions
		report.findDeepestConcept();
	}

	private void findDeepestConcept() {
		Concept deepestConcept = ROOT_CONCEPT;
		for (Concept c : gl.getAllConcepts()) {
			if (c.getMaxDepth() > deepestConcept.getMaxDepth()) {
				deepestConcept = c;
			}
		}
		logger.info("Deepest Concept is " + deepestConcept + " with longest path: " + deepestConcept.getMaxDepth() + " and shortest: " + deepestConcept.getDepth());
	}

}

package org.ihtsdo.termserver.scripting.reports;

import java.io.PrintStream;
import java.util.Collection;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Updated for SUBST-153
 * Reports all concepts that have been defined (stated) using one or more 
 * Fully Defined Parents
 */
@Deprecated
// Improved version is org.ihtsdo.termserver.scripting.reports.qi.FullyDefinedParentsInSubHierarchy
// Which lists all the parents along with the calculated Proximal Primitive Parents
public class FdParentsReport extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(FdParentsReport.class);

	String subHierarchy = "105590001"; // |Substance (substance)|
	
	public static void main(String[] args) throws TermServerScriptException {
		FdParentsReport report = new FdParentsReport();
		try {
			report.additionalReportColumns = "SemanticTag, DefinitionStatus, FdParent";
			report.init(args);
			report.loadProjectSnapshot(true);  //Load FSNs only
			report.reportFdParents();
		} catch (Exception e) {
			LOGGER.info("Failed to produce FdParentsReport due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void reportFdParents() throws TermServerScriptException {
		Collection<Concept> concepts = null;
		if (subHierarchy != null) {
			concepts = gl.getConcept(subHierarchy).getDescendants(NOT_SET);
		} else {
			concepts = gl.getAllConcepts();
		}
		
		for (Concept c : concepts) {
			if (c.isActive()) {
				String semanticTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
				for (Concept p : c.getParents(CharacteristicType.STATED_RELATIONSHIP)) {
					if (p.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
						incrementSummaryInformation(semanticTag);
						report(c, semanticTag, c.getDefinitionStatus().toString(), p.toString());
					}
				}
			}
		}
	}
	
}

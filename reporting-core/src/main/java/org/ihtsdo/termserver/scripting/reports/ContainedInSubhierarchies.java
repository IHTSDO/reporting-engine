package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * QI Given one list (eg starter set), find out how much of that is 
 * contained within a set of subHierarchies (eg Yong's QI)
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContainedInSubhierarchies extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(ContainedInSubhierarchies.class);

	List<Concept> conceptsOfInterest = new ArrayList<>();
	List<Concept> subHierarchies = new ArrayList<>();
	DecimalFormat df = new DecimalFormat();
	
	int notClinicalFinding = 0;
	int contained = 0;
	int notContained = 0;
	
	public static void main(String[] args) throws TermServerScriptException {
		ContainedInSubhierarchies report = new ContainedInSubhierarchies();
		try {
			report.additionalReportColumns = "FSN, Contained by";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			report.runContainedInSubhierarchyReport();
		} catch (Exception e) {
			LOGGER.info("Failed to produce ContainedInSubhierarchyReport Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		df.setMaximumFractionDigits(2);
		//Load the starter set
		try {
			for (String line : Files.readLines(getInputFile(), Charsets.UTF_8)) {
				conceptsOfInterest.add(gl.getConcept(line));
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to load " + getInputFile(), e);
		}
		
		//Load Yong's list of subhierarchies
		try {
			for (String line : Files.readLines(getInputFile(1), Charsets.UTF_8)) {
				subHierarchies.add(gl.getConcept(line));
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to load " + getInputFile(1), e);
		}
		super.postInit();
	}

	private void runContainedInSubhierarchyReport() throws TermServerScriptException {
		for (Concept c : conceptsOfInterest) {
			//Is this even part of clinical findings?
			if (!gl.getDescendantsCache().getDescendants(CLINICAL_FINDING).contains(c)) {
				notClinicalFinding++;
				continue;
			}
			
			//Is this concept contained by one of identified subHierarchies?
			Concept isContainedIn = null;
			for (Concept subHierarchy : subHierarchies) {
				if (gl.getDescendantsCache().getDescendantsOrSelf(subHierarchy).contains(c)) {
					isContainedIn = subHierarchy;
					break;
				}
			}
			
			if (isContainedIn == null) {
				String semtag = SnomedUtils.deconstructFSN(c.getFsn())[1];
				incrementSummaryInformation("Not contained: " + semtag);
				notContained++;
				report(c, "");
			} else {
				contained++;
				report(c, isContainedIn);
			}
		}
		
		//Now work out our stats
		float notClinicalFindingPerc = (notClinicalFinding * 100.0f) / conceptsOfInterest.size();
		LOGGER.info (df.format(notClinicalFindingPerc) + "% of starter set is not clinical finding");
		LOGGER.info("Of the concepts that ARE clinical findings:");
		int clinicalFindingCount = conceptsOfInterest.size() - notClinicalFinding;
		float containedPerc =  (contained * 100.0f) / clinicalFindingCount;
		float notContainedPerc =  (notContained * 100.0f) / clinicalFindingCount;
		LOGGER.info (df.format(containedPerc) + "% of starter set clinical findings are covered by QI subhierarchies");
		LOGGER.info (df.format(notContainedPerc) + "% of starter set clinical findings are not covered by QI subhierarchies");
		
		
	}
}

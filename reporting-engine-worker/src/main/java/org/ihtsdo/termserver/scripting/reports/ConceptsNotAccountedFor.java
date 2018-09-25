package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * Given a number of sub-hierarchies, find the highest concepts (with a count of descendants)
 * that are not contained by those sub hierarchies
 */
public class ConceptsNotAccountedFor extends TermServerReport implements ReportClass {
	
	Set<Concept> accountedForHierarchies = new HashSet<>();
	Set<Concept> notAccountedForHierarchies = new HashSet<>();
	Set<Concept> tooHigh = new HashSet<>();   //Concepts too high up the hierarchy to be considered for grouping.
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		TermServerReport.run(ConceptsNotAccountedFor.class, args);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		//ReportSheetManager.targetFolderId = "1YoJa68WLAMPKG6h4_gZ5-QT974EU9ui6"; //QI / Stats
		additionalReportColumns="FSN, Inferred Count, Already accounted";
		run.setParameter(SUB_HIERARCHY, CLINICAL_FINDING);
		super.init(run);
		getArchiveManager().allowStaleData = true;
	}

	public void postInit (JobRun run) throws TermServerScriptException {
		List<String> lines = null;
		try {
			lines = Files.readLines(inputFile, Charsets.UTF_8);
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to read " + inputFile, e);
		}
		
		for (String line : lines) {
			String[] items = line.split(COMMA);
			accountedForHierarchies.add(gl.getConcept(items[0]));
		}
		super.postInit(run);
		
		tooHigh.add(subHierarchy);
		tooHigh.add(DISEASE);
		tooHigh.add(gl.getConcept("417163006"));
		tooHigh.add(gl.getConcept("118234003"));
		tooHigh.add(gl.getConcept("250171008"));
	}
	@Override
	public Job getJob() {
		String[] parameterNames = new String[] { "SubHierarchy" };
		return new Job( new JobCategory(JobCategory.QI),
						"Concepts not accounted for",
						"Given a number of sub-hierarchies, find the highest concepts not included",
						parameterNames);
	}

	public void runJob() throws TermServerScriptException {
		
		//Work through all concepts in the hierarchy and work out if we've covered it or not
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			//Is this concept too high to look at?
			if (tooHigh.contains(c)) {
				continue;
			}
			//Is this concept accounted for?
			Set<Concept> highestNotAccountFor = findHighestNotAccountedFor(c);
			if (highestNotAccountFor != null) {
				notAccountedForHierarchies.addAll(highestNotAccountFor);
			}
		}
		
		//Now output the results
		Set<Concept> alreadyReported = new HashSet<>();
		for (Concept c : notAccountedForHierarchies) {
			Set<Concept> descendants = new HashSet<>(gl.getDescendantsCache().getDescendentsOrSelf(c));
			int originalSize = descendants.size();
			descendants.removeAll(alreadyReported);
			if (descendants.size() > 0) {
				report (c, descendants.size(), originalSize - descendants.size());
			}
			alreadyReported.addAll(descendants);
		}
	}

	private Set<Concept> findHighestNotAccountedFor(Concept c) throws TermServerScriptException {
		//Is this concept already accounted for?
		for (Concept hierarchy : accountedForHierarchies) {
			if (gl.getDescendantsCache().getDescendentsOrSelf(hierarchy).contains(c)) {
				return null;
			}
		}
		
		for (Concept hierarchy : notAccountedForHierarchies) {
			if (gl.getDescendantsCache().getDescendentsOrSelf(hierarchy).contains(c)) {
				return null;
			}
		}
		
		//Can we go higher?
		Set<Concept> highestAncestors = new HashSet<>();
		for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (!tooHigh.contains(parent)) {
				Set<Concept> highestAncestorSet = findHighestNotAccountedFor(parent);
				if (highestAncestorSet != null) {
					highestAncestors.addAll(highestAncestorSet);
				}
			}
		}
		//Remove redundancy
		SnomedUtils.removeRedundancies(highestAncestors);
		
		//Did we find anything higher?
		if (highestAncestors.size() > 0) {
			return highestAncestors;
		} else {
			return new HashSet<>(Collections.singletonList(c));
		}
	}

}

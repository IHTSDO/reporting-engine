package org.ihtsdo.termserver.scripting.pipeline.loinc;

import org.ihtsdo.termserver.scripting.pipeline.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincTerm;

import java.util.*;
import java.util.stream.Collectors;

public class ExternalConceptUsage implements Comparable<Object> {
	private static int MAX_EXAMPLES = 3;
	boolean analysed = false;
	int priority = 0;
	Set<ExternalConcept> usage = new HashSet<>();
	List<ExternalConcept> topRankedExternalConcepts = new LinkedList<>();
	
	public void add(ExternalConcept externalConcept) {
		usage.add(externalConcept);
	}
	
	public void analyze() {
		for (ExternalConcept externalConcept : usage) {
			int thisPriority = externalConcept.getPriority();
			for (int x = 0; x < MAX_EXAMPLES; x++) {
				if (x >= topRankedExternalConcepts.size() ||
						thisPriority > topRankedExternalConcepts.get(x).getPriority()) {
					topRankedExternalConcepts.add(x, externalConcept);
					break;
				}
			}
			priority += thisPriority;
		}
		analysed = true;
	}

	public List<ExternalConcept> getTopRankedExternalConcepts() {
		if (!analysed) {
			analyze();
		}
		int returnSize = topRankedExternalConcepts.size() > MAX_EXAMPLES ? MAX_EXAMPLES : topRankedExternalConcepts.size();
		return topRankedExternalConcepts.subList(0, returnSize);
	}
	
	public int getPriority() {
		if (!analysed) {
			analyze();
		}
		return priority;
	}

	@Override
	public int compareTo(Object o) {
		ExternalConceptUsage other = (ExternalConceptUsage)o;
		int comparison = ((Integer)other.getPriority()).compareTo((Integer)getPriority());
		
		return comparison == 0 ? ((Integer)other.getCount()).compareTo((Integer)getCount()) : comparison;
	}

	public String getTopRankedLoincTermsStr() {
		return getTopRankedExternalConcepts().stream()
				.map(l -> l.getExternalIdentifier() + " " + l.getLongDisplayName())
				.collect(Collectors.joining(", \n"));
	}

	public int getCount() {
		return usage.size();
	}
}

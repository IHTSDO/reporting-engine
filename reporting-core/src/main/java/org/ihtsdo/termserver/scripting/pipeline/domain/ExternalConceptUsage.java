package org.ihtsdo.termserver.scripting.pipeline.domain;

import java.util.*;
import java.util.stream.Collectors;

public class ExternalConceptUsage implements Comparable<Object> {
	private static final int MAX_EXAMPLES = 3;
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
		int comparison = ((Integer)other.getPriority()).compareTo(getPriority());
		
		return comparison == 0 ? ((Integer)other.getCount()).compareTo(getCount()) : comparison;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof ExternalConceptUsage ecu) {
			return ecu.getPriority() == getPriority();
		}
		return false;
	}

	@Override
	public int hashCode() {
		return getPriority();
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

package org.ihtsdo.termserver.scripting.pipeline.loinc;

import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincTerm;

import java.util.*;
import java.util.stream.Collectors;

public class LoincUsage implements Comparable<Object> {
	private static int MAX_EXAMPLES = 3;
	boolean analysed = false;
	int priority = 0;
	Set<LoincTerm> usage = new HashSet<>();
	List<LoincTerm> topRankedLoincTerms = new LinkedList<>();
	
	public void add(LoincTerm loincTerm) {
		usage.add(loincTerm);
	}
	
	public void analyze() {
		for (LoincTerm loincTerm : usage) {
			int thisPriority = LoincUtils.getLoincTermPriority(loincTerm);
			for (int x = 0; x < MAX_EXAMPLES; x++) {
				if (x >= topRankedLoincTerms.size() || 
						thisPriority > LoincUtils.getLoincTermPriority(topRankedLoincTerms.get(x))) {
					topRankedLoincTerms.add(x, loincTerm);
					break;
				}
			}
			priority += thisPriority;
		}
		analysed = true;
	}

	public List<LoincTerm> getTopRankedLoincTerms() {
		if (!analysed) {
			analyze();
		}
		int returnSize = topRankedLoincTerms.size() > MAX_EXAMPLES ? MAX_EXAMPLES : topRankedLoincTerms.size();
		return topRankedLoincTerms.subList(0, returnSize);
	}
	
	public int getPriority() {
		if (!analysed) {
			analyze();
		}
		return priority;
	}

	@Override
	public int compareTo(Object o) {
		LoincUsage other = (LoincUsage)o;
		int comparison = ((Integer)other.getPriority()).compareTo((Integer)getPriority());
		
		return comparison == 0 ? ((Integer)other.getCount()).compareTo((Integer)getCount()) : comparison;
	}

	public String getTopRankedLoincTermsStr() {
		return getTopRankedLoincTerms().stream()
				.map(l -> l.getLoincNum() + " " + l.getLongCommonName())
				.collect(Collectors.joining(", \n"));
	}

	public int getCount() {
		return usage.size();
	}
}

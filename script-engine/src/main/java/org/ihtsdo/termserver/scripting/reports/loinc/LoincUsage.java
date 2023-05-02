package org.ihtsdo.termserver.scripting.reports.loinc;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

public class LoincUsage implements Comparable {
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
			int thisPriority = getLoincTermPriority(loincTerm);
			for (int x = 0; x < MAX_EXAMPLES; x++) {
				if (thisPriority > getLoincTermPriority(topRankedLoincTerms.get(x))) {
					topRankedLoincTerms.add(x, loincTerm);
					break;
				}
			}
			priority += thisPriority;
		}
		analysed = true;
	}
	
	private int getLoincTermPriority (LoincTerm loincTerm) {
		int thisPriority = 0;
		String rankStr = loincTerm.getCommonOrderRank();
		if (!StringUtils.isEmpty(rankStr) && !rankStr.equals("0")) {
			thisPriority = 2000 / Integer.parseInt(rankStr);
		}
		return thisPriority;
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
		return ((Integer)getPriority()).compareTo((Integer)other.getPriority());
	}

	public String getTopRankedLoincTermsStr() {
		return getTopRankedLoincTerms().stream()
				.map(l -> l.getLoincNum() + " " + l.getLongCommonName())
				.collect(Collectors.joining(", \n"));
	}
}

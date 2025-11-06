package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincTerm;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class LoincUtils implements RF2Constants {

	public static final String LOINC_NUM_PREFIX = "LOINC Unique ID:";
	public static final String CORRELATION_PREFIX = "Correlation ID:";

	private LoincUtils() {
		throw new IllegalStateException("Utility class");
	}

	public static String getLoincNumFromDescription(Concept c) {
		try {
			return getLoincNumDescription(c).getTerm().substring(LOINC_NUM_PREFIX.length());
		} catch (Exception e) {
			return "No LOINCNum"; 
		}
	}

	public static Description getLoincNumDescription(Concept c) throws TermServerScriptException {
		return getDescription(c, LOINC_NUM_PREFIX); 
	}
	
	public static String getCorrelation(Concept c) {
		try {
			return getDescription(c, CORRELATION_PREFIX).getTerm().substring(CORRELATION_PREFIX.length());
		} catch (Exception e) {
			return "No Correlation"; 
		}
	}
	
	private static Description getDescription(Concept c, String prefix) throws TermServerScriptException {
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getTerm().startsWith(prefix)) {
				return d;
			}
		}
		throw new TermServerScriptException(c + " does not specify a " + prefix);
	}
	
	
	public static List<Concept> getActiveLoincConcepts(GraphLoader gl) {
		return SnomedUtils.sort(gl.getAllConcepts()
				.stream()
				.filter(Concept::isActive)
				.filter(c -> c.getModuleId().equals(SCTID_LOINC_PROJECT_MODULE))
				.toList()
				);
	}
	
	public static int getLoincTermPriority (LoincTerm loincTerm) {
		int thisPriority = 0;
		String rankStr = loincTerm.getCommonTestRank();
		if (!StringUtils.isEmpty(rankStr) && !rankStr.equals("0")) {
			thisPriority = 2000 / Integer.parseInt(rankStr);
		}
		return thisPriority;
	}
	
}

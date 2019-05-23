package org.ihtsdo.termserver.scripting.reports;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.AncestorsCache;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.scheduler.domain.JobRun;

public abstract class TermServerReport extends TermServerScript {
	
	public static final String IP = "IP";
	
	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		String field = lineItems[0];
		//Do we have the FSN in here?
		if (field.contains(PIPE)) {
			String[] parts = field.split(ESCAPED_PIPE);
			field = parts[0].trim();
		}
		return Collections.singletonList(gl.getConcept(field));
	}
	
	protected void report (int reportIdx, Component c, Object...details) throws TermServerScriptException {
		String line = "";
		boolean isFirst = true;
		if (c != null) {
			if (c instanceof Concept) {
				Concept concept = (Concept) c;
				line = concept.getConceptId() + COMMA_QUOTE + 
						 concept.getFsn() + QUOTE;
			} else if (c instanceof Relationship) {
				Relationship r = (Relationship) c;
				line = r.getSourceId() + COMMA_QUOTE + 
						r.toString() + QUOTE;
			}
			isFirst = false;
		}
		
		for (Object detail : details) {
			if (detail == null) {
				line += (isFirst?"":COMMA);
			} else if (detail instanceof String[]) {
				for (Object subDetail : (String[])detail) {
					String item = subDetail.toString();
					if (StringUtils.isNumeric(item)) {
						line += (isFirst?"":COMMA) + item;
					} else {
						line += (isFirst?QUOTE:COMMA_QUOTE) + item + QUOTE;
					}
				}
			} else if (detail instanceof int[]) {
				for (int subDetail : (int[])detail) {
						line += COMMA + subDetail;
				}
			} else if (detail instanceof Collection) {
				for (Object subDetail : (Collection<?>)detail) {
					line += (isFirst?QUOTE:COMMA_QUOTE) + subDetail.toString() + QUOTE;
				}
			} else {
				String item = detail.toString();
				if (StringUtils.isNumeric(item)) {
					line += (isFirst?"":COMMA) + (detail == null ? "" : detail.toString());
				} else {
					line += (isFirst?QUOTE:COMMA_QUOTE) + (detail == null ? "" : detail.toString()) + QUOTE;
				}
			}
			isFirst = false;
		}
		writeToReportFile(reportIdx, line);
	}
	
	protected void reportSafely (int reportIdx, Component c, Object... details) {
		try {
			report (reportIdx, c, details);
		} catch (TermServerScriptException e) {
			throw new IllegalStateException("Failed to write to report", e);
	}
	}
	
	protected void report (Component c, Object... details) throws TermServerScriptException {
		report (0, c, details);
	}

	@Override
	public void incrementSummaryInformation(String key) {
		if (!quiet) {
			super.incrementSummaryInformation(key);
		}
	}
	
	public static void run(Class<? extends ReportClass> reportClass, String[] args) throws TermServerScriptException {
		run(reportClass, args, null);
	}
	
	public static void run(Class<? extends ReportClass> reportClass, String[] args, Map<String, String> parameters) throws TermServerScriptException {
		JobRun jobRun = createJobRunFromArgs(reportClass.getSimpleName(), args);
		if (parameters != null) {
			for (Map.Entry<String, String> entry : parameters.entrySet()) {
				jobRun.setParameter(entry.getKey(), entry.getValue());
			}
		}
		ReportClass report = null;
		try {
			report = reportClass.newInstance();
			((TermServerReport)report).checkSettingsWithUser(jobRun);
		} catch ( InstantiationException | IllegalAccessException e) {
			throw new TermServerScriptException("Unable to instantiate " + reportClass.getSimpleName(), e);
		}
		report.instantiate(jobRun);
	}
	
	protected Set<Concept> identifyIntermediatePrimitives(Collection<Concept> concepts) throws TermServerScriptException {
		return identifyIntermediatePrimitives(concepts, CharacteristicType.INFERRED_RELATIONSHIP);
	}
	
	protected Set<Concept> identifyIntermediatePrimitives(Collection<Concept> concepts, CharacteristicType charType) throws TermServerScriptException {
		Set<Concept> allIps = new HashSet<>();
		AncestorsCache cache = charType.equals(CharacteristicType.INFERRED_RELATIONSHIP) ? gl.getAncestorsCache() : gl.getStatedAncestorsCache();
		for (Concept c : concepts) {
			//We're only interested in fully defined (QI project includes leaf concepts)
			if (c.isActive() &&
				c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				//Get a list of all my primitive ancestors
				List<Concept> proxPrimParents = cache.getAncestors(c).stream()
						.filter(a -> a.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE))
						.collect(Collectors.toList());
				//Do those ancestors themselves have sufficiently defined ancestors ie making them intermediate primitives
				for (Concept thisPPP : proxPrimParents) {
					if (containsFdConcept(cache.getAncestors(thisPPP))) {
						allIps.add(thisPPP);
					}
				}
			}
		}
		return allIps;
	}

	private boolean containsFdConcept(Collection<Concept> concepts) {
		for (Concept c : concepts) {
			if (c.isActive() && c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				return true;
			}
		}
		return false;
	}
}

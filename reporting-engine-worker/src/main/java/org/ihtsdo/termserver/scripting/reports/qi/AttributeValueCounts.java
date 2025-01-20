package org.ihtsdo.termserver.scripting.reports.qi;

import com.google.common.util.concurrent.AtomicLongMap;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.AncestorsCache;
import org.ihtsdo.termserver.scripting.DescendantsCache;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.apache.commons.lang.StringUtils;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AttributeValueCounts extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(AttributeValueCounts.class);

	public static final String ATTRIBUTE_TYPE = "Attribute Type";
	public static final String IGNORE_ECL = "Ignore Concepts ECL";
	public static final String MIN_SIZE_INTEREST = "Minimum Size";
	Concept targetAttributeType;
	String ignoreConceptsECL;
	AncestorsCache ancestorCache;
	DescendantsCache descendantCache;
	AtomicLongMap<Concept> valueCounts = AtomicLongMap.create();
	AtomicLongMap<Concept> recentActivity = AtomicLongMap.create();
	AtomicLongMap<Concept> valueCountsFiltered = AtomicLongMap.create();
	Map<Concept, AtomicLongMap<Concept>> combinationCounts = new HashMap<>();
	Set<Concept> alreadyReported = new HashSet<>();
	int minimumSizeOfInterest = 50;
	Set<Concept>ignoreConcepts;
	String recentEffectiveTime;
	DecimalFormat df = new DecimalFormat("0.0");

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "<< 373873005 |Pharmaceutical / biologic product (product)|");
		params.put(ATTRIBUTE_TYPE, "127489000 |Has active ingredient|");
		TermServerScript.run(AttributeValueCounts.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("11i7XQyb46P2xXNBwlCOd3ssMNhLOx1m1"); // QI / Misc Analysis
		additionalReportColumns = "FSN, SemTag, Depth, Total Concept Count, Filtered Concept Count, Not-Including Descendants, Filtered Not-Including Descendants, Parents, GrandParents, Seen Together With, Recent Activity";
		getArchiveManager().setPopulateHierarchyDepth(true);
		super.init(run);
		
		String attribStr = run.getMandatoryParamValue(ATTRIBUTE_TYPE);
		if (attribStr != null && !attribStr.isEmpty()) {
			targetAttributeType = gl.getConcept(attribStr);
		}
		
		subsetECL = run.getMandatoryParamValue(ECL);
		ignoreConceptsECL = run.getParamValue(IGNORE_ECL);
		
		if (run.getParamValue(MIN_SIZE_INTEREST) != null) {
			minimumSizeOfInterest = Integer.parseInt(run.getParamValue(MIN_SIZE_INTEREST));
		}
	}
	
	@Override
	public void postInit() throws TermServerScriptException {
		super.postInit();
		recentEffectiveTime = project.getMetadata().getPreviousRelease();
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL).withMandatory()
				.add(IGNORE_ECL).withType(JobParameter.Type.ECL)
				.add(ATTRIBUTE_TYPE).withType(JobParameter.Type.CONCEPT).withMandatory()
				.add(MIN_SIZE_INTEREST).withType(JobParameter.Type.STRING).withDefaultValue("50")
				//TODO Add the characteristic type dropdown
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.QI))
				.withName("Attribute Value Counts")
				.withDescription("This report shows the number of times different attribute values are used with a " + 
					"specific attribute type in concepts that match the specified ECL. You can also filter out certain " +
					"concepts if needed. Additionally, the report indicates the level of activity related to these " +
					"concepts and highlights the other attribute values that are most frequently used together with them.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		Concept[] types = new Concept[] {targetAttributeType};
		ancestorCache = gl.getAncestorsCache();
		descendantCache = gl.getDescendantsCache();
		LOGGER.info("Analyzing " + subsetECL);
		ignoreConcepts = StringUtils.isEmpty(ignoreConceptsECL)? new HashSet<>() : new HashSet<>(findConcepts(ignoreConceptsECL));
		for (Concept c : findConcepts(subsetECL)) {
			//Find all the target values for the specified attribute type
			Set<Concept> targets = SnomedUtils.getTargets(c, types, CharacteristicType.INFERRED_RELATIONSHIP);
			for (Concept target : targets) {
				valueCounts.getAndIncrement(target);
				//We also separately count those we haven't been told to ignore eg QI project previously processed.
				if (!ignoreConcepts.contains(c)) {
					valueCountsFiltered.getAndIncrement(target);
				}
				
				//Has this concept been 'touched' recently? Indicates work in this area
				if (wasTouchedRecently(c)) {
					recentActivity.getAndIncrement(target);
				}
				
				//Increment the counts of all the other morphology types used here
				AtomicLongMap<Concept> comboCount = combinationCounts.get(target);
				if (comboCount == null) {
					comboCount = AtomicLongMap.create();
					combinationCounts.put(target, comboCount);
				}
				for (Concept combo : targets) {
					if (!combo.equals(target)) {
						comboCount.getAndIncrement(combo);
					}
				}
			}
		}
		
		LOGGER.info("Outputting counts");
		Set<Concept> targets = new HashSet<>(valueCounts.asMap().keySet());
		if (!targets.isEmpty()) {
			//Now work through the list, from top to bottom
			//Concept top = calculateHighestConceptOrParent(targets);
			Concept top = SnomedUtils.findCommonAncestor(targets, ancestorCache);
			targets.add(top);
			//Go go go recursive programming!
			reportCounts(top, targets, true);
		}
	}

	private boolean wasTouchedRecently(Concept c) {
		for (Relationship r : c.getRelationships()) {
			if (StringUtils.isEmpty(r.getEffectiveTime()) || r.getEffectiveTime().equals(recentEffectiveTime)) {
				return true;
			}
		}
		return false;
	}

	private void reportCounts(Concept c, Set<Concept> targets, boolean isTop) throws TermServerScriptException {
		//Have we reported this concept already?
		if (alreadyReported.contains(c)) {
			return;
		} else {
			alreadyReported.add(c);
		}

		LOGGER.debug("Reporting {} with {} targets", c, targets.size());
		
		//What's the descendant count added up?
		if (isTop || valueCounts.containsKey(c)) {
			Set<Concept> parents = c.getParents(CharacteristicType.INFERRED_RELATIONSHIP);
			String parentsStr = parents.stream().map(p->p.toString())
					.collect(Collectors.joining(",\n"));
			String parentsParentsStr = parents.stream()
					.flatMap(p->p.getParents(CharacteristicType.INFERRED_RELATIONSHIP).stream())
					.map(pp->pp.toString())
					.collect(Collectors.joining(",\n"));
			int count = getDescendantAndSelfCount(c, targets, false);
			int countFiltered = getDescendantAndSelfCount(c, targets, true);
			int countNotIncludingDescendants = (int)valueCounts.get(c);
			int countNotIncludingDescendantsFiltered = (int)valueCountsFiltered.get(c);
			if (countFiltered > minimumSizeOfInterest) {
				double recentActivityPercentage = 100 * ((double) recentActivity.get(c) / count);
				report(c, getDepthIndicator(c), count, countFiltered, countNotIncludingDescendants, countNotIncludingDescendantsFiltered, parentsStr, parentsParentsStr, getUsedInCombination(c), df.format(recentActivityPercentage));
				countIssue(c);
			}
		}

		//Now report all of my children, and their children, if they exist.
		for (Concept child : c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
			reportCounts(child, targets, false);
		}
	}

	private String getUsedInCombination(Concept c) {
		AtomicLongMap<Concept> combinationCount = combinationCounts.get(c);
		if (combinationCount == null) {
			return null;
		}
		List<Map.Entry<Concept, Long>> sorted = new ArrayList<>(combinationCount.asMap().entrySet());
		Collections.sort(sorted, Collections.reverseOrder(Map.Entry.comparingByValue()));
		return sorted.stream()
			.limit(3)
			.map(cb -> cb.toString())
			.collect(Collectors.joining(", \n"));

	}

	private Object getDepthIndicator(Concept c) {
		String depthIndicator="";
		for (int x=NOT_SET; x<c.getDepth(); x++) {
			depthIndicator += "- ";
		}
		return depthIndicator;
	}

	private Integer getDescendantAndSelfCount(Concept c, Set<Concept> targets, boolean doFiltering) throws TermServerScriptException {
		Set<Concept> descendantsInUse = new HashSet<>(descendantCache.getDescendantsOrSelf(c));
		descendantsInUse.retainAll(targets);
		AtomicLongMap<Concept> sourceData = doFiltering ? valueCountsFiltered : valueCounts;
		int count = 0;
		for (Concept target : descendantsInUse) {
			count += sourceData.get(target);
		}
		return count;
	}

}

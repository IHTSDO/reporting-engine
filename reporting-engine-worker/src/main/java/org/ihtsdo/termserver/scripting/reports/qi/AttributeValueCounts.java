package org.ihtsdo.termserver.scripting.reports.qi;

import com.google.common.util.concurrent.AtomicLongMap;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.AncestorsCache;
import org.ihtsdo.termserver.scripting.DescendantsCache;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * INFRA-
 */

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
	DescendantsCache descendentCache;
	AtomicLongMap<Concept> valueCounts = AtomicLongMap.create();
	AtomicLongMap<Concept> recentActivity = AtomicLongMap.create();
	AtomicLongMap<Concept> valueCountsFiltered = AtomicLongMap.create();
	Map<Concept, AtomicLongMap<Concept>> combinationCounts = new HashMap<>();
	Set<Concept> alreadyReported = new HashSet<>();
	int minimumSizeOfInterest = 50;
	Set<Concept>ignoreConcepts;
	String recentEffectiveTime;
	DecimalFormat df = new DecimalFormat("0.0");

	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "<< 404684003 |Clinical finding (finding)|");
		params.put(ATTRIBUTE_TYPE, ASSOC_MORPH.toString());
		TermServerReport.run(AttributeValueCounts.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		additionalReportColumns = "FSN, SemTag, Depth, Total Concept Count, Filtered Concept Count, Not-Including Descendants, Filtered Not-Including Descendants, Parents, GrandParents, Seen Together With, Recent Activity";
		getArchiveManager().setPopulateHierarchyDepth(true);
		super.init(run);
		
		String attribStr = run.getParamValue(ATTRIBUTE_TYPE);
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
				.add(ATTRIBUTE_TYPE).withType(JobParameter.Type.CONCEPT)
				.add(MIN_SIZE_INTEREST).withType(JobParameter.Type.STRING).withDefaultValue("50")
				//TODO Add the characteristic type dropdown
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.QI))
				.withName("Attribute Value Counts")
				.withDescription("This report counts of attribute values used for concepts matching the specified ECL, with optionally some specified concepts filtered out.  Additionally, an indicator is given of how much activity there has been against the conepts using this attribtue value, and what other attribute value(s) it is most often seen in combination with.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		Concept[] types = new Concept[] {targetAttributeType};
		ancestorCache = gl.getAncestorsCache();
		descendentCache = gl.getDescendantsCache();
		LOGGER.info ("Analyzing " + subsetECL);
		ignoreConcepts = new HashSet<>(findConcepts(ignoreConceptsECL));
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
		
		//TODO We're also interested in the primitive concepts above this attribute value.
		LOGGER.info ("Outputting counts");
		Set<Concept> targets = new HashSet<>(valueCounts.asMap().keySet());
		if (!targets.isEmpty()) {
			//Now work through the list, from top to bottom
			Concept top = calculateHighestConceptOrParent(targets);
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
		Set<Concept> descendantsInUse = new HashSet<>(descendentCache.getDescendentsOrSelf(c));
		descendantsInUse.retainAll(targets);
		AtomicLongMap<Concept> sourceData = doFiltering ? valueCountsFiltered : valueCounts;
		int count = 0;
		for (Concept target : descendantsInUse) {
			count += sourceData.get(target);
		}
		return count;
	}

	private Concept calculateHighestConceptOrParent(Set<Concept> concepts) throws TermServerScriptException {
		//Find the smallest depth indicator
		Integer minimumDepth = null;
		for (Concept c : concepts) {
			if (minimumDepth == null || c.getDepth() < minimumDepth) {
				minimumDepth = c.getDepth();
			}
		}
		//What all concepts are at that depth?
		final int minDepth = minimumDepth;
		Set<Concept> siblings = concepts.stream()
				.filter(c -> c.getDepth() == minDepth)
				.collect(Collectors.toSet());
		
		if (siblings.size() == 1) {
			return siblings.iterator().next();
		} else {
			//Find the deepest common ancestor of these siblings
			Set<Concept> commonAncestors = null;
			for (Concept sibling : siblings) {
				if (commonAncestors == null) {
					commonAncestors = ancestorCache.getAncestors(sibling);
				} else {
					commonAncestors.retainAll(ancestorCache.getAncestors(sibling));
				}
			}
			return deepestConcept(commonAncestors);
		}
	}

	private Concept deepestConcept(Set<Concept> concepts) throws TermServerScriptException {
		//Find the greatest depth indicator
		Integer maximumDepth = null;
		for (Concept c : concepts) {
			if (maximumDepth == null || c.getDepth() < maximumDepth) {
				maximumDepth = c.getDepth();
			}
		}
		
		//What all concepts are at that depth?
		final int maxDepth = maximumDepth;
		Set<Concept> siblings = concepts.stream()
				.filter(c -> c.getDepth() == maxDepth)
				.collect(Collectors.toSet());
		
		if (siblings.size() != 1) {
			throw new TermServerScriptException("Unable to find deepest concept from " + concepts);
		}
		return siblings.iterator().next();
	}

}

package org.ihtsdo.termserver.scripting.reports.release;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.service.TraceabilityService;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.ihtsdo.termserver.scripting.util.StringUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ConceptChanged extends TermServerReport implements ReportClass {
	
	private Set<Concept> newConcepts = new HashSet<>();
	private Set<Concept> inactivatedConcepts = new HashSet<>();
	private Set<Concept> defStatusChanged = new HashSet<>();
	private Set<Concept> hasNewStatedRelationships = new HashSet<>();
	private Set<Concept> hasNewInferredRelationships = new HashSet<>();
	private Set<Concept> hasLostStatedRelationships = new HashSet<>();
	private Set<Concept> hasLostInferredRelationships = new HashSet<>();
	private Set<Concept> hasNewDescriptions = new HashSet<>();
	private Set<Concept> hasChangedDescriptions = new HashSet<>();
	private Set<Concept> hasLostDescriptions = new HashSet<>();
	private Set<Concept> hasNewTextDefn = new HashSet<>();
	private Set<Concept> hasChangedTextDefn = new HashSet<>();
	private Set<Concept> hasLostTextDefn = new HashSet<>();
	private Set<Concept> hasChangedAssociations = new HashSet<>();
	private Set<Concept> hasChangedInactivationIndicators = new HashSet<>();
	private Set<Concept> isTargetOfNewStatedRelationship = new HashSet<>();
	private Set<Concept> wasTargetOfLostStatedRelationship = new HashSet<>();
	private Set<Concept> isTargetOfNewInferredRelationship = new HashSet<>();
	private Set<Concept> wasTargetOfLostInferredRelationship = new HashSet<>();
	
	//RP-398
	private Set<Concept> hasChangedAcceptabilityDesc = new HashSet<>();
	//RP-452
	private Set<Concept> hasChangedAcceptabilityTextDefn = new HashSet<>();

	//RP-387
	private Set<Concept> hasNewLanguageRefSets = new HashSet<>();
	private Set<Concept> hasLostLanguageRefSets = new HashSet<>();
	private Set<Concept> hasChangedLanguageRefSets = new HashSet<>();
	
	TraceabilityService traceability;
	
	Map<String, SummaryCount> summaryCounts = new LinkedHashMap<>();  //preserve insertion order for tight report loop
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		//params.put(ECL, "<< 420040002|Fluoroscopic angiography (procedure)|");
		TermServerReport.run(ConceptChanged.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
		getArchiveManager().setPopulateReleasedFlag(true);
		subsetECL = run.getParamValue(ECL);
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Component, New, Changed, Inactivated",
				"Id, FSN, SemTag, Active, DefStatusChanged, Languages, Author, Task, Creation Date",
				"Id, FSN, SemTag, Active, hasNewStatedRelationships, hasNewInferredRelationships, hasLostStatedRelationships, hasLostInferredRelationships, Author, Task, Creation Date",
				"Id, FSN, SemTag, Active, hasNewDescriptions, hasChangedDescriptions, hasLostDescriptions, hasChangedAcceptability, Author, Task, Creation Date",
				"Id, FSN, SemTag, Active, hasChangedAssociations, hasChangedInactivationIndicators, Author, Task, Creation Date",
				"Id, FSN, SemTag, Active, isTargetOfNewStatedRelationship, isTargetOfNewInferredRelationship, wasTargetOfLostStatedRelationship, wasTargetOfLostInferredRelationship, Author, Task, Creation Date",
				"Id, FSN, SemTag, Language, Description, isNew, isChanged, wasInactivated, changedAcceptability,Description Type",
				"Id, FSN, SemTag, Description, LangRefset, isNew, isChanged, wasInactivated",
				"Id, FSN, SemTag, Active, hasNewTextDefn, hasChangedTextDefn, hasLostTextDefn, hasChangedAcceptability, Author, Task, Creation Date",
				
		};
		String[] tabNames = new String[] {
				"Summary Counts",
				"Concept Changes",
				"Relationship Changes",
				"Description Changes",
				"Association Changes",
				"Incoming Relationship Changes",
				"Description Change Details",
				"Language Refset Details",
				"TextDefn Changes"
		};
		super.postInit(tabNames, columnHeadings, false);
		traceability = new TraceabilityService(jobRun, this, "pdat");  //Matching Updating and updated
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL).withDefaultValue("<< " + ROOT_CONCEPT)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Concepts Changed")
				.withDescription("This report lists all concepts changed in the current release cycle, optionally restricted to a subset defined by an ECL expression.  The issue count here is the total number of concepts featuring one change or another.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		examineConcepts();
		reportConceptsChanged();
		determineUniqueCountAndTraceability();
		traceability.flush();
		report (PRIMARY_REPORT, "");
		if (!StringUtils.isEmpty(subsetECL)) {
			report (PRIMARY_REPORT, "Run against", subsetECL);
		}
		info ("Job complete");
	}
	
	public void examineConcepts() throws TermServerScriptException { 
		int conceptsExamined = 0;
		Collection<Concept> conceptsOfInterest;
		
		if (!StringUtils.isEmpty(subsetECL)) {
			info("Running Concepts Changed report against subset: " + subsetECL);
			conceptsOfInterest = findConcepts(subsetECL);
		} else {
			conceptsOfInterest = gl.getAllConcepts();
		}
		
		double lastPercentageReported = 0;
		long notReleased = 0;
		long notChanged = 0;
		long notInScope = 0;
		
		for (Concept c : conceptsOfInterest) {
			SummaryCount summaryCount = getSummaryCount(ComponentType.CONCEPT.name());
			if (c.isReleased() == null) {
				throw new IllegalStateException ("Malformed snapshot. Released status not populated at " + c);
			} else if (!c.isReleased()) {
				//We will not service.populateTraceabilityAndReport any changes on brand new concepts
				//Or (in managed service) concepts from other modules
				notReleased++;
				summaryCount.isNew++;
				continue;
			} else if (inScope(c) && StringUtils.isEmpty(c.getEffectiveTime())) {
				//Only want to log def status change if the concept has not been made inactive
				if (c.isActive()) {
					defStatusChanged.add(c);
					summaryCount.isChanged++;
				} else {
					inactivatedConcepts.add(c);
					summaryCount.isInactivated++;
				}
			} else if (inScope(c)) {
				notChanged++;
			} else {
				notInScope++;
			}
			
			for (Description d : c.getDescriptions()) {
				//Report FSN/Synonyms and TextDefinition separately.
				boolean isTextDefn = d.getType().equals(DescriptionType.TEXT_DEFINITION);
				ComponentType componentType = isTextDefn ? ComponentType.TEXT_DEFINITION : ComponentType.DESCRIPTION;
				Set<Concept> hasNew = isTextDefn ? hasNewTextDefn : hasNewDescriptions;
				Set<Concept> hasLost = isTextDefn ? hasLostTextDefn : hasLostDescriptions;
				Set<Concept> hasChanged = isTextDefn ? hasChangedTextDefn : hasChangedDescriptions;
				Set<Concept> hasChangedAcceptability = isTextDefn ? hasChangedAcceptabilityTextDefn : hasChangedAcceptabilityDesc;
				
				summaryCount = getSummaryCount(componentType.name() + " - " + d.getLang());
				SummaryCount summaryCountLRF = getSummaryCount(ComponentType.LANGREFSET.name() + " - " + d.getLang());
				boolean isNew = false;
				boolean isChanged = false;
				boolean wasInactivated = false;
				boolean changedAcceptability = false;
				if (inScope(d)) {
					if (!d.isReleased()) {
						hasNew.add(c);
						isNew = true;
						summaryCount.isNew++;
					} else if (StringUtils.isEmpty(d.getEffectiveTime())) {
						if (!d.isActive()) {
							hasLost.add(c);
							wasInactivated = true;
							summaryCount.isInactivated++;
						} else {
							hasChanged.add(c);
							isChanged = true;
							summaryCount.isChanged++;
						}
					}
					
					//Has it changed acceptability?
					if (!isNew) {
						for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
							if (StringUtils.isEmpty(l.getEffectiveTime())) {
								hasChangedAcceptability.add(c);
								changedAcceptability = true;
							}
						}
					}
					
					//Description inactivation indicators
					summaryCount = getSummaryCount(ComponentType.ATTRIBUTE_VALUE.name() + " - Descriptions");
					for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries()) {
						if (i.getEffectiveTime() == null || i.getEffectiveTime().isEmpty()) {
							if (i.isReleased()) {
								if (i.isActive()) {
									summaryCount.isChanged++;
								}
							} else {
								if (i.isActive()) {
									summaryCount.isNew++;
								} else {
									summaryCount.isInactivated++;
								}
							}
						}
					}
					
					//Description hist assocs
					summaryCount = getSummaryCount(ComponentType.HISTORICAL_ASSOCIATION.name() + " - Descriptions");
					for (AssociationEntry h : d.getAssociationEntries()) {
						if (h.getEffectiveTime() == null || h.getEffectiveTime().isEmpty()) {
							if (h.isReleased()) {
								if (h.isActive()) {
									summaryCount.isChanged++;
								}
							} else {
								if (h.isActive()) {
									summaryCount.isNew++;
								} else {
									summaryCount.isInactivated++;
								}
							}
						}
					}

					boolean langRefSetIsNew = false;
					boolean langRefSetIsLost = false;
					boolean langRefSetIsChanged = false;
					for (LangRefsetEntry langRefsetEntry : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
						if (inScope(langRefsetEntry)) {
							if (!langRefsetEntry.isReleased()) {
								langRefSetIsNew = true;
								hasNewLanguageRefSets.add(c);
								summaryCountLRF.isNew++;
							} else if (StringUtils.isEmpty(langRefsetEntry.getEffectiveTime())) {
								if (!langRefsetEntry.isActive()) {
									hasLostLanguageRefSets.add(c);
									langRefSetIsLost = true;
									summaryCountLRF.isInactivated++;
								} else {
									hasChangedLanguageRefSets.add(c);
									langRefSetIsChanged = true;
									summaryCountLRF.isChanged++;
								}
							}

							if (langRefSetIsNew || langRefSetIsLost || langRefSetIsChanged) {
								report(OCTONARY_REPORT, c, d, langRefsetEntry, langRefSetIsNew, langRefSetIsChanged, langRefSetIsLost);
							}
						}
					}
				}
				
				if (isNew || isChanged || wasInactivated || changedAcceptability) {
					report (SEPTENARY_REPORT, c, d.getLang(), d, isNew, isChanged, wasInactivated, changedAcceptability, d.getType());
				}
			}
			
			SummaryCount summaryCountInferred = getSummaryCount(ComponentType.INFERRED_RELATIONSHIP.name());
			for (Relationship r : c.getRelationships()) {
				if (inScope(r)) {
					if (StringUtils.isEmpty(r.getEffectiveTime())) {
						boolean isStated = r.getCharacteristicType().equals(CharacteristicType.STATED_RELATIONSHIP);
						if (r.isActive()) {
							if (isStated) {
								hasNewStatedRelationships.add(c);
								if (r.isNotConcrete()) {
									isTargetOfNewStatedRelationship.add(r.getTarget());
								}
							} else {
								hasNewInferredRelationships.add(c);
								if (r.isNotConcrete()) {
									isTargetOfNewInferredRelationship.add(r.getTarget());
									summaryCountInferred.isNew++;
								}
							}
						} else {
							if (isStated) {
								hasLostStatedRelationships.add(c);
								if (r.isNotConcrete()) {
									wasTargetOfLostStatedRelationship.add(r.getTarget());
								}
							} else {
								hasLostInferredRelationships.add(c);
								if (r.isNotConcrete()) {
									wasTargetOfLostInferredRelationship.add(r.getTarget());
								}
								summaryCountInferred.isInactivated++;
							}
						}
					}
				}
			}
			
			if (inScope(c)) {
				summaryCount = getSummaryCount(ComponentType.ATTRIBUTE_VALUE.name() + " - Concepts");
				for (InactivationIndicatorEntry i : c.getInactivationIndicatorEntries()) {
					if (i.getEffectiveTime() == null || i.getEffectiveTime().isEmpty()) {
						hasChangedInactivationIndicators.add(c);
						if (i.isReleased()) {
							if (i.isActive()) {
								summaryCount.isChanged++;
							}
						} else {
							if (i.isActive()) {
								summaryCount.isNew++;
							} else {
								summaryCount.isInactivated++;
							}
						}
					}
				}
				
				summaryCount = getSummaryCount(ComponentType.HISTORICAL_ASSOCIATION.name() + " - Concepts");
				for (AssociationEntry a : c.getAssociationEntries()) {
					if (a.getEffectiveTime() == null || a.getEffectiveTime().isEmpty()) {
						hasChangedAssociations.add(c);
						if (a.isReleased()) {
							if (a.isActive()) {
								summaryCount.isChanged++;
							}
						} else {
							if (a.isActive()) {
								summaryCount.isNew++;
							} else {
								summaryCount.isInactivated++;
							}
						}
					}
				}
			}
			
			conceptsExamined++;
			double perc = ((double)conceptsExamined / (double)conceptsOfInterest.size()) * 100;
			if (perc >= lastPercentageReported + 10) {
				info ("Examined " + String.format("%.2f", perc) + "%");
				lastPercentageReported = perc;
			}
		}
		
		info ("Not Released: " + notReleased);
		info ("Not Changed: " + notChanged);
		info ("Not In Scope " +  notInScope);
		info ("Total examined: " + conceptsOfInterest.size());
	}
	
	private void reportConceptsChanged() throws TermServerScriptException {
		
		HashSet<Concept> superSet = new HashSet<>();
		superSet.addAll(newConcepts);
		superSet.addAll(defStatusChanged);
		superSet.addAll(inactivatedConcepts);
		debug ("Creating concept report for " + superSet.size() + " concepts");
		for (Concept c : sort(superSet)) {
			traceability.populateTraceabilityAndReport (SECONDARY_REPORT, c,
				c.isActive()?"Y":"N",
				defStatusChanged.contains(c)?"Y":"N",
				getLanguages(c));
		}
		traceability.flush();
		superSet.clear();
		
		superSet.addAll(hasNewStatedRelationships);
		superSet.addAll(hasNewInferredRelationships);
		superSet.addAll(hasLostStatedRelationships);
		superSet.addAll(hasLostInferredRelationships);
		debug ("Creating relationship report for " + superSet.size() + " concepts");
		for (Concept c : sort(superSet)) {
			traceability.populateTraceabilityAndReport (TERTIARY_REPORT, c,
				c.isActive()?"Y":"N",
				hasNewStatedRelationships.contains(c)?"Y":"N",
				hasNewInferredRelationships.contains(c)?"Y":"N",
				hasLostStatedRelationships.contains(c)?"Y":"N",
				hasLostInferredRelationships.contains(c)?"Y":"N");
		}
		getSummaryCount("Concept Model Changed").isChanged = superSet.size();
		superSet.clear();
		
		superSet.addAll(hasNewDescriptions);
		superSet.addAll(hasChangedDescriptions);
		superSet.addAll(hasLostDescriptions);
		superSet.addAll(hasChangedAcceptabilityDesc);
		debug ("Creating description report for " + superSet.size() + " concepts");
		for (Concept c : sort(superSet)) {
			traceability.populateTraceabilityAndReport (QUATERNARY_REPORT, c,
				c.isActive()?"Y":"N",
				hasNewDescriptions.contains(c)?"Y":"N",
				hasChangedDescriptions.contains(c)?"Y":"N",
				hasLostDescriptions.contains(c)?"Y":"N",
				hasChangedAcceptabilityDesc.contains(c)?"Y":"N");
		}
		getSummaryCount("Concept Descriptions Changed").isNew = hasNewDescriptions.size();
		getSummaryCount("Concept Descriptions Changed").isChanged = hasChangedDescriptions.size();
		getSummaryCount("Concept Descriptions Changed").isInactivated = hasLostDescriptions.size();
		superSet.clear();
		
		superSet.addAll(hasChangedAssociations);
		superSet.addAll(hasChangedInactivationIndicators);
		debug ("Creating association report for " + superSet.size() + " concepts");
		for (Concept c : sort(superSet)) {
			traceability.populateTraceabilityAndReport (QUINARY_REPORT, c,
				c.isActive()?"Y":"N",
				hasChangedAssociations.contains(c)?"Y":"N",
				hasChangedInactivationIndicators.contains(c)?"Y":"N");
		}
		superSet.clear();
		
		superSet.addAll(isTargetOfNewStatedRelationship);
		superSet.addAll(wasTargetOfLostStatedRelationship);
		superSet.addAll(isTargetOfNewInferredRelationship);
		superSet.addAll(wasTargetOfLostInferredRelationship);
		debug ("Creating incoming relationship report for " + superSet.size() + " concepts");
		for (Concept c : sort(superSet)) {
			traceability.populateTraceabilityAndReport (SENARY_REPORT, c,
				c.isActive()?"Y":"N",
				isTargetOfNewStatedRelationship.contains(c)?"Y":"N",
				wasTargetOfLostStatedRelationship.contains(c)?"Y":"N",
				isTargetOfNewInferredRelationship.contains(c)?"Y":"N",
				wasTargetOfLostInferredRelationship.contains(c)?"Y":"N");
		}
		superSet.clear();
		
		superSet.addAll(hasNewTextDefn);
		superSet.addAll(hasChangedTextDefn);
		superSet.addAll(hasLostTextDefn);
		superSet.addAll(hasChangedAcceptabilityTextDefn);
		debug ("Creating text defn report for " + superSet.size() + " concepts");
		for (Concept c : sort(superSet)) {
			traceability.populateTraceabilityAndReport (NONARY_REPORT, c,
				c.isActive()?"Y":"N",
				hasNewTextDefn.contains(c)?"Y":"N",
				hasChangedTextDefn.contains(c)?"Y":"N",
				hasLostTextDefn.contains(c)?"Y":"N",
				hasChangedAcceptabilityTextDefn.contains(c)?"Y":"N");
		}
		getSummaryCount("Concept TextDefn Changed").isNew = hasNewTextDefn.size();
		getSummaryCount("Concept TextDefn Changed").isChanged = hasChangedTextDefn.size();
		getSummaryCount("Concept TextDefn Changed").isInactivated = hasLostTextDefn.size();
		superSet.clear();
		
		traceability.flush();
		
		//Populate the summary numbers for each type of component
		List<String> summaryCountKeys = new ArrayList<>(summaryCounts.keySet());
		Collections.sort(summaryCountKeys);
		for (String componentType : summaryCountKeys) {
			SummaryCount sc = summaryCounts.get(componentType);
			String componentName = StringUtils.capitalizeFirstLetter(componentType.toString().toLowerCase());
			report (PRIMARY_REPORT, componentName, sc.isNew, sc.isChanged, sc.isInactivated);
		}
	}
	
	private String getLanguages(Concept c) {
		Set<String> langs = new TreeSet<>();
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			langs.add(d.getLang());
		}
		return String.join(", ", langs);
	}

	private void determineUniqueCountAndTraceability() {
		debug("Determining unique count");
		HashSet<Concept> superSet = new HashSet<>();
		superSet.addAll(newConcepts);
		superSet.addAll(inactivatedConcepts);
		superSet.addAll(defStatusChanged);
		superSet.addAll(hasNewStatedRelationships);
		superSet.addAll(hasNewInferredRelationships);
		superSet.addAll(hasLostStatedRelationships);
		superSet.addAll(hasLostInferredRelationships);
		superSet.addAll(hasNewDescriptions);
		superSet.addAll(hasChangedDescriptions);
		superSet.addAll(hasLostDescriptions);
		superSet.addAll(hasChangedAssociations);
		superSet.addAll(hasChangedInactivationIndicators);
		superSet.addAll(isTargetOfNewStatedRelationship);
		superSet.addAll(wasTargetOfLostStatedRelationship);
		superSet.addAll(isTargetOfNewInferredRelationship);
		superSet.addAll(wasTargetOfLostInferredRelationship);
		superSet.addAll(hasChangedAcceptabilityDesc);
		superSet.addAll(hasNewLanguageRefSets);
		superSet.addAll(hasLostLanguageRefSets);
		superSet.addAll(hasChangedLanguageRefSets);

		for (Concept c : superSet) {
			countIssue(c);
		}
	}
	
	private List<Concept> sort(Set<Concept> superSet) {
		//We're going to sort on top level hierarchy, then alphabetically
		return superSet.stream()
		.sorted((c1, c2) -> compareSemTagFSN(c1,c2))
		.collect(Collectors.toList());
	}

	private int compareSemTagFSN(Concept c1, Concept c2) {
		String[] fsnSemTag1 = SnomedUtils.deconstructFSN(c1.getFsn());
		String[] fsnSemTag2 = SnomedUtils.deconstructFSN(c2.getFsn());
		
		if (fsnSemTag1[1] == null) {
			System.out.println("FSN Encountered without semtag: " + c1);
			return 1;
		} else if (fsnSemTag2[1] == null) {
			System.out.println("FSN Encountered without semtag: " + c2);
			return -1;
		}
		
		if (fsnSemTag1[1].equals(fsnSemTag2[1])) {
			return fsnSemTag1[0].compareTo(fsnSemTag2[0]);
		}
		return fsnSemTag1[1].compareTo(fsnSemTag2[1]);
	}
	
	class SummaryCount {
		int isNew;
		int isChanged;
		int isInactivated;
	}
	
	SummaryCount getSummaryCount(String type) {
		if (!summaryCounts.containsKey(type)) {
			SummaryCount summaryCount = new SummaryCount();
			summaryCounts.put(type, summaryCount);
		}
		return summaryCounts.get(type);
	}
	
}

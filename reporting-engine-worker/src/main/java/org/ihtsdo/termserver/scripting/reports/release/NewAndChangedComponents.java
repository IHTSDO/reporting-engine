package org.ihtsdo.termserver.scripting.reports.release;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.service.TraceabilityService;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class NewAndChangedComponents extends TermServerReport implements ReportClass {
	
	private Set<Concept> newConcepts = new HashSet<>();
	private Set<Concept> inactivatedConcepts = new HashSet<>();
	private Set<Concept> defStatusChanged = new HashSet<>();
	private Set<Concept> hasNewInferredRelationships = new HashSet<>();
	private Set<Concept> hasLostInferredRelationships = new HashSet<>();
	private Set<Concept> hasNewAxioms = new HashSet<>();
	private Set<Concept> hasChangedAxioms = new HashSet<>();
	private Set<Concept> hasLostAxioms = new HashSet<>();
	private Set<Concept> hasNewDescriptions = new HashSet<>();
	private Set<Concept> hasChangedDescriptions = new HashSet<>();
	private Set<Concept> hasLostDescriptions = new HashSet<>();
	private Set<Concept> hasNewTextDefn = new HashSet<>();
	private Set<Concept> hasChangedTextDefn = new HashSet<>();
	private Set<Concept> hasLostTextDefn = new HashSet<>();
	private Set<Concept> hasChangedAssociations = new HashSet<>();
	private Set<Concept> hasChangedInactivationIndicators = new HashSet<>();
	private Set<Concept> isTargetOfNewInferredRelationship = new HashSet<>();
	private Set<Concept> wasTargetOfLostInferredRelationship = new HashSet<>();
	private Set<Concept> hasChangedAcceptabilityDesc = new HashSet<>();
	private Set<Concept> hasChangedAcceptabilityTextDefn = new HashSet<>();
	private Set<Concept> hasNewLanguageRefSets = new HashSet<>();
	private Set<Concept> hasLostLanguageRefSets = new HashSet<>();
	private Set<Concept> hasChangedLanguageRefSets = new HashSet<>();
	
	TraceabilityService updateTraceability;
	TraceabilityService createTraceability;
	
	Map<String, SummaryCount> summaryCounts = new LinkedHashMap<>();  //preserve insertion order for tight report loop
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		TermServerReport.run(NewAndChangedComponents.class, args, params);
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
				"Id, FSN, SemTag, Active, isNew, DefStatusChanged, Languages, Author, Task, Date",
				"Id, FSN, SemTag, Active, newWithNewConcept, hasNewInferredRelationships, hasLostInferredRelationships, Author, Task, Date",
				"Id, FSN, SemTag, Active, newWithNewConcept, hasNewAxioms, hasChangedAxioms, hasLostAxioms, Author, Task, Date",
				"Id, FSN, SemTag, Active, newWithNewConcept, hasNewDescriptions, hasChangedDescriptions, hasLostDescriptions, hasChangedAcceptability, Author, Task, Date",
				"Id, FSN, SemTag, Active, hasChangedAssociations, hasChangedInactivationIndicators, Author, Task, Date",
				"Id, FSN, SemTag, Active, isTargetOfNewInferredRelationship, wasTargetOfLostInferredRelationship",
				"Id, FSN, SemTag, Language, Description, isNew, isChanged, wasInactivated, changedAcceptability,Description Type",
				"Id, FSN, SemTag, Description, LangRefset, isNew, isChanged, wasInactivated",
				"Id, FSN, SemTag, Active, newWithNewConcept, hasNewTextDefn, hasChangedTextDefn, hasLostTextDefn, hasChangedAcceptability, Author, Task, Date",
		};
		String[] tabNames = new String[] {
				"Summary Counts",
				"Concepts",
				"Inferred Rels",
				"Axioms",
				"Descriptions",
				"Associations",
				"Incoming Rels",
				"Description Details",
				"Language Refset Details",
				"TextDefns"
		};
		super.postInit(tabNames, columnHeadings, false);
		updateTraceability = new TraceabilityService(jobRun, this, "pdat");  //Matching Updating and updated
		createTraceability = new TraceabilityService(jobRun, this, "reating concept");
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL).withDefaultValue("<< " + ROOT_CONCEPT)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("New And Changed Components")
				.withDescription("This report lists all components new and changed in the current release cycle, optionally restricted to a subset defined by an ECL expression." +
				"The issue count here is the total number of concepts featuring one change or another.  Note that specifying ECL means that inactive concepts will not be included, on account of them having no hierarchial position.")
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
		updateTraceability.flush();
		createTraceability.flush();
		report (PRIMARY_REPORT, "");
		if (!StringUtils.isEmpty(subsetECL)) {
			report (PRIMARY_REPORT, "Run against", subsetECL);
		}
		updateTraceability.tidyUp();
		createTraceability.tidyUp();
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
			/*if (c.getId().equals("3641486008")) {
				debug("here");
			}*/
			SummaryCount summaryCount = getSummaryCount(ComponentType.CONCEPT.name());
			if (c.isReleased() == null) {
				throw new IllegalStateException ("Malformed snapshot. Released status not populated at " + c);
			} else if (!c.isReleased()) {
				notReleased++;
				newConcepts.add(c);
				summaryCount.isNew++;
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
						for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.BOTH)) {
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
								} else {
									summaryCount.isInactivated++;
								}
							} else if (i.isActive()) {
								summaryCount.isNew++;
							}
						}
					}
					
					//Description hist assocs
					summaryCount = getSummaryCount(ComponentType.HISTORICAL_ASSOCIATION.name() + " - Descriptions");
					for (AssociationEntry h : d.getAssociationEntries()) {
						if (StringUtils.isEmpty(h.getEffectiveTime())) {
							if (h.isReleased()) {
								if (h.isActive()) {
									summaryCount.isChanged++;
								} else {
									summaryCount.isInactivated++;
								}
							} else if (h.isActive()) {
								summaryCount.isNew++;
							}
						}
					}

					boolean langRefSetIsNew = false;
					boolean langRefSetIsLost = false;
					boolean langRefSetIsChanged = false;
					for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.BOTH)) {
						if (inScope(l)) {
							if (StringUtils.isEmpty(l.getEffectiveTime())) {
								if (l.isReleased()) {
									if (l.isActive()) {
										hasChangedLanguageRefSets.add(c);
										langRefSetIsChanged = true;
										summaryCountLRF.isChanged++;
									} else {
										hasLostLanguageRefSets.add(c);
										langRefSetIsLost = true;
										summaryCountLRF.isInactivated++;
									}
								} else {
									langRefSetIsNew = true;
									hasNewLanguageRefSets.add(c);
									summaryCountLRF.isNew++;
								}
							}

							if (langRefSetIsNew || langRefSetIsLost || langRefSetIsChanged) {
								report(NONARY_REPORT, c, d, l, langRefSetIsNew, langRefSetIsChanged, langRefSetIsLost);
							}
						}
					}
				}
				
				if (isNew || isChanged || wasInactivated || changedAcceptability) {
					report (OCTONARY_REPORT, c, d.getLang(), d, isNew, isChanged, wasInactivated, changedAcceptability, d.getType());
				}
			}
			
			summaryCount = getSummaryCount(ComponentType.INFERRED_RELATIONSHIP.name());
			for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.BOTH)) {
				if (inScope(r)) {
					if (StringUtils.isEmpty(r.getEffectiveTime())) {
						if (r.isActive()) {
							hasNewInferredRelationships.add(c);
							if (r.isNotConcrete()) {
								isTargetOfNewInferredRelationship.add(r.getTarget());
								summaryCount.isNew++;
							}
						} else {
							if (r.isNotConcrete()) {
								wasTargetOfLostInferredRelationship.add(r.getTarget());
							}
							hasLostInferredRelationships.add(c);
							summaryCount.isInactivated++;
						}
					}
				}
			}
			
			summaryCount = getSummaryCount(ComponentType.AXIOM.name());
			for (AxiomEntry a : c.getAxiomEntries()) {
				if (inScope(a)) {
					if (StringUtils.isEmpty(a.getEffectiveTime())) {
						if (a.isReleased()) {
							if (a.isActive()) {
								summaryCount.isChanged++;
								hasChangedAxioms.add(c);
							} else {
								summaryCount.isInactivated++;
								hasLostAxioms.add(c);
							}
						} else if (a.isActive()) {
							summaryCount.isNew++;
							hasNewAxioms.add(c);
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
							} else {
								summaryCount.isInactivated++;
							}
						} else if (i.isActive()) {
							summaryCount.isNew++;
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
							} else {
								summaryCount.isInactivated++;
							}
						} else if (a.isActive()) {
							summaryCount.isNew++;
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
		for (Concept c : SnomedUtils.sort(superSet)) {
			populateTraceabilityAndReport (SECONDARY_REPORT, c,
				c.isActive()?"Y":"N",
				newConcepts.contains(c)?"Y":"N",
				defStatusChanged.contains(c)?"Y":"N",
				getLanguages(c));
		}
		updateTraceability.flush();
		createTraceability.flush();
		superSet.clear();
		
		superSet.addAll(hasNewInferredRelationships);
		superSet.addAll(hasLostInferredRelationships);
		debug ("Creating relationship report for " + superSet.size() + " concepts");
		for (Concept c : SnomedUtils.sort(superSet)) {
			String newWithNewConcept = hasNewInferredRelationships.contains(c) && newConcepts.contains(c) ? "Y":"N";
			populateTraceabilityAndReport (TERTIARY_REPORT, c,
				c.isActive()?"Y":"N",
				newWithNewConcept,
				hasNewInferredRelationships.contains(c)?"Y":"N",
				hasLostInferredRelationships.contains(c)?"Y":"N");
		}
		superSet.clear();
		
		superSet.addAll(hasNewAxioms);
		superSet.addAll(hasChangedAxioms);
		superSet.addAll(hasLostAxioms);
		debug ("Creating axiom report for " + superSet.size() + " concepts");
		for (Concept c : SnomedUtils.sort(superSet)) {
			String newWithNewConcept = hasNewAxioms.contains(c) && newConcepts.contains(c) ? "Y":"N";
			populateTraceabilityAndReport (QUATERNARY_REPORT, c,
				c.isActive()?"Y":"N",
				newWithNewConcept,
				hasNewAxioms.contains(c)?"Y":"N",
				hasChangedAxioms.contains(c)?"Y":"N",
				hasLostAxioms.contains(c)?"Y":"N");
		}
		superSet.clear();
		
		superSet.addAll(hasNewDescriptions);
		superSet.addAll(hasChangedDescriptions);
		superSet.addAll(hasLostDescriptions);
		superSet.addAll(hasChangedAcceptabilityDesc);
		debug ("Creating description report for " + superSet.size() + " concepts");
		for (Concept c : SnomedUtils.sort(superSet)) {
			String newWithNewConcept = hasNewDescriptions.contains(c) && newConcepts.contains(c) ? "Y":"N";
			populateTraceabilityAndReport (QUINARY_REPORT, c,
				c.isActive()?"Y":"N",
				newWithNewConcept,
				hasNewDescriptions.contains(c)?"Y":"N",
				hasChangedDescriptions.contains(c)?"Y":"N",
				hasLostDescriptions.contains(c)?"Y":"N",
				hasChangedAcceptabilityDesc.contains(c)?"Y":"N");
		}
		getSummaryCount("Concepts with Descriptions").isNew = hasNewDescriptions.size();
		getSummaryCount("Concepts with Descriptions").isChanged = hasChangedDescriptions.size();
		getSummaryCount("Concepts with Descriptions").isInactivated = hasLostDescriptions.size();
		superSet.clear();
		
		superSet.addAll(hasChangedAssociations);
		superSet.addAll(hasChangedInactivationIndicators);
		debug ("Creating association report for " + superSet.size() + " concepts");
		for (Concept c : SnomedUtils.sort(superSet)) {
			populateTraceabilityAndReport (SENARY_REPORT, c,
				c.isActive()?"Y":"N",
				hasChangedAssociations.contains(c)?"Y":"N",
				hasChangedInactivationIndicators.contains(c)?"Y":"N");
		}
		superSet.clear();
		
		superSet.addAll(isTargetOfNewInferredRelationship);
		superSet.addAll(wasTargetOfLostInferredRelationship);
		debug ("Creating incoming relationship report for " + superSet.size() + " concepts");
		for (Concept c : SnomedUtils.sort(superSet)) {
			report (SEPTENARY_REPORT, c,
				c.isActive()?"Y":"N",
				isTargetOfNewInferredRelationship.contains(c)?"Y":"N",
				wasTargetOfLostInferredRelationship.contains(c)?"Y":"N");
		}
		superSet.clear();
		
		superSet.addAll(hasNewTextDefn);
		superSet.addAll(hasChangedTextDefn);
		superSet.addAll(hasLostTextDefn);
		superSet.addAll(hasChangedAcceptabilityTextDefn);
		debug ("Creating text defn report for " + superSet.size() + " concepts");
		for (Concept c : SnomedUtils.sort(superSet)) {
			populateTraceabilityAndReport (DENARY_REPORT, c,
				c.isActive()?"Y":"N",
				hasNewTextDefn.contains(c)?"Y":"N",
				hasChangedTextDefn.contains(c)?"Y":"N",
				hasLostTextDefn.contains(c)?"Y":"N",
				hasChangedAcceptabilityTextDefn.contains(c)?"Y":"N");
		}
		getSummaryCount("Concepts with TextDefn").isNew = hasNewTextDefn.size();
		getSummaryCount("Concepts with TextDefn").isChanged = hasChangedTextDefn.size();
		getSummaryCount("Concepts with TextDefn").isInactivated = hasLostTextDefn.size();
		superSet.clear();
		
		updateTraceability.flush();
		createTraceability.flush();
		
		//Populate the summary numbers for each type of component
		List<String> summaryCountKeys = new ArrayList<>(summaryCounts.keySet());
		Collections.sort(summaryCountKeys);
		for (String componentType : summaryCountKeys) {
			SummaryCount sc = summaryCounts.get(componentType);
			String componentName = StringUtils.capitalizeFirstLetter(componentType.toString().toLowerCase());
			if (!componentName.endsWith("s") && !componentName.contains(" - ")) {
				componentName += "s";
			}
			report (PRIMARY_REPORT, componentName, sc.isNew, sc.isChanged, sc.isInactivated);
		}
	}
	
	private void populateTraceabilityAndReport(int tabIdx, Concept c, Object... details) throws TermServerScriptException {
		//Are we recovering this information as an update or a creation?
		if (newConcepts.contains(c)) {
			createTraceability.populateTraceabilityAndReport(tabIdx, c, details);
		} else {
			updateTraceability.populateTraceabilityAndReport(tabIdx, c, details);
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
		superSet.addAll(hasNewInferredRelationships);
		superSet.addAll(hasLostInferredRelationships);
		superSet.addAll(hasNewAxioms);
		superSet.addAll(hasChangedAxioms);
		superSet.addAll(hasLostAxioms);
		superSet.addAll(hasNewDescriptions);
		superSet.addAll(hasChangedDescriptions);
		superSet.addAll(hasLostDescriptions);
		superSet.addAll(hasChangedAssociations);
		superSet.addAll(hasChangedInactivationIndicators);
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

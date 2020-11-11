package org.ihtsdo.termserver.scripting.reports.release;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.service.TraceabilityService;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.ihtsdo.termserver.scripting.util.StringUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

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
	private Set<Concept> hasChangedAssociations = new HashSet<>();
	private Set<Concept> hasChangedInactivationIndicators = new HashSet<>();
	private Set<Concept> isTargetOfNewStatedRelationship = new HashSet<>();
	private Set<Concept> wasTargetOfLostStatedRelationship = new HashSet<>();
	private Set<Concept> isTargetOfNewInferredRelationship = new HashSet<>();
	private Set<Concept> wasTargetOfLostInferredRelationship = new HashSet<>();
	
	//RP-398
	private Set<Concept> hasChangedAcceptability = new HashSet<>();

	//RP-387
	private Set<Concept> hasNewLanguageRefSets = new HashSet<>();
	private Set<Concept> hasLostLanguageRefSets = new HashSet<>();
	private Set<Concept> hasChangedLanguageRefSets = new HashSet<>();
	
	TraceabilityService traceability;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
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
				"Id, FSN, SemTag, Active, DefStatusChanged, Languages, Author, Task, Creation Date",
				"Id, FSN, SemTag, Active, hasNewStatedRelationships, hasNewInferredRelationships, hasLostStatedRelationships, hasLostInferredRelationships, Author, Task, Creation Date",
				"Id, FSN, SemTag, Active, hasNewDescriptions, hasChangedDescriptions, hasLostDescriptions, hasChangedAcceptability, Author, Task, Creation Date",
				"Id, FSN, SemTag, Active, hasChangedAssociations, hasChangedInactivationIndicators, Author, Task, Creation Date",
				"Id, FSN, SemTag, Active, isTargetOfNewStatedRelationship, isTargetOfNewInferredRelationship, wasTargetOfLostStatedRelationship, wasTargetOfLostInferredRelationship, Author, Task, Creation Date",
				"Id, FSN, SemTag, Language, Description, isNew, isChanged, wasInactivated, changedAcceptability",
				"Id, FSN, SemTag, LangRefsetId, LangRefset, isNew, isChanged, wasInactivated, Details, Details"
		};
		String[] tabNames = new String[] {
				"Concept Changes",
				"Relationship Changes",
				"Description Changes",
				"Association Changes",
				"Incoming Relationship Changes",
				"Description Change Details",
				"Language Refset Changes"
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
			if (c.getConceptId().equals("53726008")) {
				debug("here");
			}
			
			if (c.isReleased() == null) {
				throw new IllegalStateException ("Malformed snapshot. Released status not populated at " + c);
			} else if (!c.isReleased()) {
				//We will not service.populateTraceabilityAndReport any changes on brand new concepts
				//Or (in managed service) concepts from other modules
				notReleased++;
				continue;
			} else if (inScope(c) && StringUtils.isEmpty(c.getEffectiveTime())) {
				//Only want to log def status change if the concept has not been made inactive
				if (c.isActive()) {
					defStatusChanged.add(c);
				} else {
					inactivatedConcepts.add(c);
				}
			} else if (inScope(c)) {
				notChanged++;
			} else {
				notInScope++;
			}
			
			for (Description d : c.getDescriptions()) {
				boolean isNew = false;
				boolean isChanged = false;
				boolean wasInactivated = false;
				boolean changedAcceptability = false;
				if (inScope(d)) {
					if (!d.isReleased()) {
						hasNewDescriptions.add(c);
						isNew = true;
					} else if (StringUtils.isEmpty(d.getEffectiveTime())) {
						if (!d.isActive()) {
							hasLostDescriptions.add(c);
							wasInactivated = true;
						} else {
							hasChangedDescriptions.add(c);
							isChanged = true;
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

					boolean langRefSetIsNew = false;
					boolean langRefSetIsLost = false;
					boolean langRefSetIsChanged = false;
					for (LangRefsetEntry langRefsetEntry : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
						if (inScope(langRefsetEntry)) {
							if (!langRefsetEntry.isReleased()) {
								langRefSetIsNew = true;
								hasNewLanguageRefSets.add(c);
							} else if (StringUtils.isEmpty(langRefsetEntry.getEffectiveTime())) {
								if (!langRefsetEntry.isActive()) {
									hasLostLanguageRefSets.add(c);
									langRefSetIsLost = true;
								} else {
									hasChangedLanguageRefSets.add(c);
									langRefSetIsChanged = true;
								}
							}

							if (langRefSetIsNew || langRefSetIsLost || langRefSetIsChanged) {
								report(SEPTENARY_REPORT, c, langRefsetEntry.getRefsetId(), langRefsetEntry, langRefSetIsNew, langRefSetIsChanged, langRefSetIsLost);
							}
						}
					}
				}
				
				if (isNew || isChanged || wasInactivated || changedAcceptability) {
					report (SENARY_REPORT, c, d.getLang(), d, isNew, isChanged, wasInactivated, changedAcceptability);
				}
			}
			
			for (Relationship r : c.getRelationships()) {
				if (inScope(r)) {
					if (StringUtils.isEmpty(r.getEffectiveTime())) {
						boolean isStated = r.getCharacteristicType().equals(CharacteristicType.STATED_RELATIONSHIP);
						if (r.isActive()) {
							if (isStated) {
								hasNewStatedRelationships.add(c);
								isTargetOfNewStatedRelationship.add(r.getTarget());
							} else {
								hasNewInferredRelationships.add(c);
								isTargetOfNewInferredRelationship.add(r.getTarget());
							}
						} else {
							if (isStated) {
								hasLostStatedRelationships.add(c);
								wasTargetOfLostStatedRelationship.add(c);
							} else {
								hasLostInferredRelationships.add(c);
								wasTargetOfLostInferredRelationship.add(c);
							}
						}
					}
				}
			}
			
			if (inScope(c)) {
				for (InactivationIndicatorEntry i : c.getInactivationIndicatorEntries()) {
					if (i.getEffectiveTime() == null || i.getEffectiveTime().isEmpty()) {
						hasChangedInactivationIndicators.add(c);
					}
				}
				
				for (AssociationEntry a : c.getAssociationEntries()) {
					if (a.getEffectiveTime() == null || a.getEffectiveTime().isEmpty()) {
						hasChangedAssociations.add(c);
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
			traceability.populateTraceabilityAndReport (PRIMARY_REPORT, c,
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
			traceability.populateTraceabilityAndReport (SECONDARY_REPORT, c,
				c.isActive()?"Y":"N",
				hasNewStatedRelationships.contains(c)?"Y":"N",
				hasNewInferredRelationships.contains(c)?"Y":"N",
				hasLostStatedRelationships.contains(c)?"Y":"N",
				hasLostInferredRelationships.contains(c)?"Y":"N");
		}
		superSet.clear();
		
		superSet.addAll(hasNewDescriptions);
		superSet.addAll(hasChangedDescriptions);
		superSet.addAll(hasLostDescriptions);
		superSet.addAll(hasChangedAcceptability);
		debug ("Creating description report for " + superSet.size() + " concepts");
		for (Concept c : sort(superSet)) {
			traceability.populateTraceabilityAndReport (TERTIARY_REPORT, c,
				c.isActive()?"Y":"N",
				hasNewDescriptions.contains(c)?"Y":"N",
				hasChangedDescriptions.contains(c)?"Y":"N",
				hasLostDescriptions.contains(c)?"Y":"N",
				hasChangedAcceptability.contains(c)?"Y":"N");
		}
		superSet.clear();

		superSet.addAll(hasNewLanguageRefSets);
		superSet.addAll(hasChangedLanguageRefSets);
		superSet.addAll(hasLostLanguageRefSets);
		debug("Creating language refsets report for " + superSet.size() + " concepts");
		for (Concept c : sort(superSet)) {
			traceability.populateTraceabilityAndReport(SEPTENARY_REPORT, c,
					c.isActive() ? "Y" : "N",
					hasNewLanguageRefSets.contains(c) ? "Y" : "N",
					hasChangedLanguageRefSets.contains(c) ? "Y" : "N",
					hasLostLanguageRefSets.contains(c) ? "Y" : "N");
		}
		superSet.clear();
		
		superSet.addAll(hasChangedAssociations);
		superSet.addAll(hasChangedInactivationIndicators);
		debug ("Creating association report for " + superSet.size() + " concepts");
		for (Concept c : sort(superSet)) {
			traceability.populateTraceabilityAndReport (QUATERNARY_REPORT, c,
				c.isActive()?"Y":"N",
				hasChangedAssociations.contains(c)?"Y":"N",
				hasChangedInactivationIndicators.contains(c)?"Y":"N");
		}
		superSet.clear();
		
		superSet.addAll(isTargetOfNewStatedRelationship);
		superSet.addAll(wasTargetOfLostStatedRelationship);
		superSet.addAll(isTargetOfNewInferredRelationship);
		superSet.addAll(wasTargetOfLostInferredRelationship);
		debug ("Creating incoming relatonshiip report for " + superSet.size() + " concepts");
		for (Concept c : sort(superSet)) {
			traceability.populateTraceabilityAndReport (QUINARY_REPORT, c,
				c.isActive()?"Y":"N",
				isTargetOfNewStatedRelationship.contains(c)?"Y":"N",
				wasTargetOfLostStatedRelationship.contains(c)?"Y":"N",
				isTargetOfNewInferredRelationship.contains(c)?"Y":"N",
				wasTargetOfLostInferredRelationship.contains(c)?"Y":"N");
		}
		
		traceability.flush();
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
		superSet.addAll(hasChangedAcceptability);
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
	
}

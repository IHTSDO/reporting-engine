package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.ArchiveManager;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

public class ConceptChanged extends TermServerReport implements ReportClass {
	
	private Set<Concept> newConcepts = new HashSet<>();
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
	
	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException {
		Map<String, String> params = new HashMap<>();
		//params.put(ECL, "<< " + ROOT_CONCEPT.toString());
		TermServerReport.run(ConceptChanged.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		ReportSheetManager.setMaxColumns(18);
		getArchiveManager().populateReleasedFlag = true;
		additionalReportColumns = "FSN, Semtag, Active, New Concept, defStatusChanged, hasNewStatedRelationships, hasNewInferredRelationships, hasLostStatedRelationships, hasLostInferredRelationships, hasNewDescriptions, hasChangedDescriptions, hasLostDescriptions, hasChangedAssociations, hasChangedInactivationIndicators, isTargetOfNewStatedRelationship, isTargetOfNewInferredRelationship, wasTargetOfLostStatedRelationship, wasTargetOfLostInferredRelationship";
		subHierarchyECL = run.getParamValue(ECL);
		super.init(run);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL).withDefaultValue("<< " + ROOT_CONCEPT)
				.build();
		return new Job( new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION),
						"Concepts Changed",
						"This report lists all concepts changed in the current release cycle",
						params, ProductionStatus.HIDEME);
	}
	
	public void runJob() throws TermServerScriptException {
		examineConcepts();
		reportConceptsChanged();
		info ("Job complete");
	}
	
	public void examineConcepts() throws TermServerScriptException { 
		int conceptsExamined = 0;
		Collection<Concept> conceptsOfInterest;
		
		if (subHierarchyECL != null && !subHierarchyECL.isEmpty()) {
			conceptsOfInterest = findConcepts(subHierarchyECL);
		} else {
			conceptsOfInterest = gl.getAllConcepts();
		}
		
		double lastPercentageReported = 0;
		for (Concept c : conceptsOfInterest) {
			if (!c.isReleased()) {
				newConcepts.add(c);
			} else if (c.getEffectiveTime() == null || c.getEffectiveTime().isEmpty()) {
				defStatusChanged.add(c);
			}
			
			for (Description d : c.getDescriptions()) {
				if (!d.isReleased()) {
					hasNewDescriptions.add(c);
				} else if (d.getEffectiveTime() == null || d.getEffectiveTime().isEmpty()) {
					if (!d.isActive()) {
						hasLostDescriptions.add(c);
					} else {
						hasChangedDescriptions.add(c);
					}
				}
			}
			
			for (Relationship r : c.getRelationships()) {
				if (r.getEffectiveTime() == null || r.getEffectiveTime().isEmpty()) {
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
			
			for (InactivationIndicatorEntry i : c.getInactivationIndicatorEntries()) {
				if (i.getEffectiveTime() == null || i.getEffectiveTime().isEmpty()) {
					hasChangedInactivationIndicators.add(c);
				}
			}
			
			for (AssociationEntry a : c.getAssociations()) {
				if (a.getEffectiveTime() == null || a.getEffectiveTime().isEmpty()) {
					hasChangedAssociations.add(c);
				}
			}
			
			conceptsExamined++;
			double perc = ((double)conceptsExamined / (double)conceptsOfInterest.size()) * 100;
			if (perc >= lastPercentageReported + 10) {
				info ("Examined " + String.format("%.2f", perc) + "%");
				lastPercentageReported = perc;
			}
		}
	}
	
	private void reportConceptsChanged() throws TermServerScriptException {
		debug ("Creating super set of all concept changes identified");
		HashSet<Concept> superSet = new HashSet<>();
		superSet.addAll(newConcepts);
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
		
		//We're going to sort on top level hierarchy, then alphabetically
		debug ("Sorting super set");
		List<Concept> sortedList = superSet.stream()
		.sorted((c1, c2) -> compareSemTagFSN(c1,c2))
		.collect(Collectors.toList());
		
		debug ("Creating report for " + sortedList.size() + " concepts");
		for (Concept c : sortedList) {
			report (c,
				c.isActive()?"Y":"N",
				newConcepts.contains(c)?"Y":"N",
				defStatusChanged.contains(c)?"Y":"N",
				hasNewStatedRelationships.contains(c)?"Y":"N",
				hasNewInferredRelationships.contains(c)?"Y":"N",
				hasLostStatedRelationships.contains(c)?"Y":"N",
				hasLostInferredRelationships.contains(c)?"Y":"N",
				hasNewDescriptions.contains(c)?"Y":"N",
				hasChangedDescriptions.contains(c)?"Y":"N",
				hasLostDescriptions.contains(c)?"Y":"N",
				hasChangedAssociations.contains(c)?"Y":"N",
				hasChangedInactivationIndicators.contains(c)?"Y":"N",
				isTargetOfNewStatedRelationship.contains(c)?"Y":"N",
				wasTargetOfLostStatedRelationship.contains(c)?"Y":"N",
				isTargetOfNewInferredRelationship.contains(c)?"Y":"N",
				wasTargetOfLostInferredRelationship.contains(c)?"Y":"N");
		}
	}

	private int compareSemTagFSN(Concept c1, Concept c2) {
		String[] fsnSemTag1 = SnomedUtils.deconstructFSN(c1.getFsn());
		String[] fsnSemTag2 = SnomedUtils.deconstructFSN(c2.getFsn());
		
		if (fsnSemTag1[1].equals(fsnSemTag2[1])) {
			return fsnSemTag1[0].compareTo(fsnSemTag2[0]);
		}
		return fsnSemTag1[1].compareTo(fsnSemTag2[1]);
	}
	
}

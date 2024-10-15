package org.ihtsdo.termserver.scripting.reports.release;

import org.apache.commons.lang3.time.DateUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.service.TraceabilityService;
import org.ihtsdo.termserver.scripting.service.SingleTraceabilityService;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewAndChangedComponents extends HistoricDataUser implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(NewAndChangedComponents.class);

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
	
	private List<String> wordMatches;
	private String changesFromET;
	private static final String WORD_MATCHES = "Word Matches";
	private static final String CHANGES_SINCE = "Changes From";
	private static final String INCLUDE_DETAIL = "Include Detail";
	
	private SimpleDateFormat dateFormat =  new SimpleDateFormat("yyyyMMdd");
	private boolean forceTraceabilityPopulation = false;
	private boolean includeDescriptionDetail = true;
	
	TraceabilityService traceabilityService;
	
	public static int MAX_ROWS_FOR_TRACEABILITY = 10000;
	private boolean loadHistoricallyGeneratedData = false;
	
	Map<String, SummaryCount> summaryCounts = new LinkedHashMap<>();  //preserve insertion order for tight report loop
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		//params.put(ECL, "<<118245000 |Measurement finding (finding)|");
		//params.put(THIS_RELEASE, "SnomedCT_ManagedServiceSE_PRODUCTION_SE1000052_20220531T120000Z.zip");
		//params.put(THIS_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20220131T120000Z.zip");
		//params.put(PREV_RELEASE, "SnomedCT_ManagedServiceSE_PRODUCTION_SE1000052_20200531T120000Z.zip");
		//params.put(PREV_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20200131T120000Z.zip");
		//params.put(MODULES, "45991000052106");
		//params.put(WORD_MATCHES, "COVID,COVID-19,Severe acute respiratory syndrome coronavirus 2,SARS-CoV-2,2019-nCoV,2019 novel coronavirus");
		//params.put(CHANGES_SINCE, "20210801");
		params.put(INCLUDE_DETAIL, "true");
		params.put(UNPROMOTED_CHANGES_ONLY, "false");
		TermServerReport.run(NewAndChangedComponents.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
		getArchiveManager().setPopulateReleasedFlag(true);
		subsetECL = run.getParamValue(ECL);
		
		if (!StringUtils.isEmpty(run.getParamValue(INCLUDE_DETAIL))) {
			includeDescriptionDetail = run.getParamBoolean(INCLUDE_DETAIL);
		}
		
		if (!StringUtils.isEmpty(run.getParamValue(WORD_MATCHES))) {
			wordMatches = Arrays.asList(run.getParamValue(WORD_MATCHES).split("\\s*,\\s*"));
			wordMatches = wordMatches.stream()
					.map(String::toLowerCase)
					.collect(Collectors.toList());
		}
		
		if (!StringUtils.isEmpty(run.getParamValue(CHANGES_SINCE))) {
			int dateCheck = Integer.parseInt(run.getParamValue(CHANGES_SINCE));
			if (dateCheck < 19840101 || dateCheck > 30000101) {
				throw new IllegalArgumentException("Invalid effective time: " + dateCheck);
			}
			changesFromET = run.getParamValue(CHANGES_SINCE);
		}
		
		if (!StringUtils.isEmpty(run.getParamValue(MODULES))) {
			moduleFilter = Stream.of(run.getParamValue(MODULES).split(",", -1))
					.map(String::trim)
					.collect(Collectors.toList());
		}
		
		super.init(run);
	}
	
	
	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException {
		//If we're working with zip packages, we'll use the HistoricDataGenerator
		//Otherwise we'll use the default behaviour
		prevRelease = getJobRun().getParamValue(PREV_RELEASE);
		if (prevRelease == null) {
			super.doDefaultProjectSnapshotLoad(fsnOnly);
		} else {
			loadHistoricallyGeneratedData = true;
			prevDependency = getJobRun().getParamValue(PREV_DEPENDENCY);
			
			if (StringUtils.isEmpty(prevDependency)) {
				prevDependency = getProject().getMetadata().getPreviousDependencyPackage();
				if (StringUtils.isEmpty(prevDependency)) {
					throw new TermServerScriptException("Previous dependency package not populated in branch metadata for " + getProject().getBranchPath());
				}
			}
			
			setDependencyArchive(prevDependency);
			
			thisDependency = getJobRun().getParamValue(THIS_DEPENDENCY);
			if (StringUtils.isEmpty(thisDependency)) {
				thisDependency = getProject().getMetadata().getDependencyPackage();
			}
			
			if (!StringUtils.isEmpty(getJobRun().getParamValue(THIS_DEPENDENCY)) 
					&& StringUtils.isEmpty(getJobRun().getParamValue(MODULES))) {
				throw new TermServerScriptException("Module filter must be specified when working with published archives");
			}
			
			if (StringUtils.isEmpty(getJobRun().getParamValue(MODULES))) {
				String defaultModule = project.getMetadata().getDefaultModuleId();
				if (StringUtils.isEmpty(defaultModule)) {
					throw new TermServerScriptException("Unable to recover default moduleId from project: " + project.getKey());
				}
				moduleFilter = Collections.singletonList(defaultModule);
			}
			
			super.loadProjectSnapshot(fsnOnly);
		}
	}

	@Override
	protected void loadCurrentPosition(boolean compareTwoSnapshots, boolean fsnOnly) throws TermServerScriptException {
		LOGGER.info("Setting dependency archive: " + thisDependency);
		setDependencyArchive(thisDependency);
		super.loadCurrentPosition(compareTwoSnapshots, fsnOnly);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Component, New, Changed, Inactivated",
				"Id, FSN, SemTag, EffectiveTime, Active, isNew, DefStatusChanged, Languages, Author, Task, Date",
				"Id, FSN, SemTag, EffectiveTime, Active, newWithNewConcept, hasNewInferredRelationships, hasLostInferredRelationships",
				"Id, FSN, SemTag, EffectiveTime, Active, newWithNewConcept, hasNewAxioms, hasChangedAxioms, hasLostAxioms, Author, Task, Date",
				"Id, FSN, SemTag, EffectiveTime, Active, newWithNewConcept, hasNewDescriptions, hasChangedDescriptions, hasLostDescriptions, hasChangedAcceptability, Author, Task, Date",
				"Id, FSN, SemTag, EffectiveTime, Active, newWithNewConcept, hasNewTextDefn, hasChangedTextDefn, hasLostTextDefn, hasChangedAcceptability, Author, Task, Date",
				"Id, FSN, SemTag, EffectiveTime, Active, hasChangedAssociations, hasChangedInactivationIndicators, Author, Task, Date",
				"Id, FSN, SemTag, EffectiveTime, Active, isTargetOfNewInferredRelationship, wasTargetOfLostInferredRelationship",
				"Id, FSN, SemTag, EffectiveTime, Language, Description, isNew, isChanged, wasInactivated, changedAcceptability, Description Type",
				"Id, FSN, SemTag, EffectiveTime, Description, LangRefset, isNew, isChanged, wasInactivated",
		};
		String[] tabNames = new String[] {
				"Summary Counts",
				"Concepts",
				"Inferred Rels",
				"Axioms",
				"Descriptions",
				"TextDefns",
				"Associations",
				"Incoming Rels",
				"Description Details",
				"Language Refset Details"
		};
		
		if (!includeDescriptionDetail) {
			tabNames[8] = "N/A 1";
			tabNames[9] = "N/A 2";
		}
		if (loadHistoricallyGeneratedData) {
			changesFromET = previousEffectiveTime;
		}
		
		if (loadHistoricallyGeneratedData && ! forceTraceabilityPopulation) {
			traceabilityService = new PassThroughTraceability(this);
			for (int i=0; i<columnHeadings.length; i++) {
				//We're not going to populate traceability for published releases
				columnHeadings[i] = columnHeadings[i].replace(", Author, Task, Date", "");
			}
		} else {
			traceabilityService = new SingleTraceabilityService(jobRun, this);
		}
		
		if (thisDependency != null || 
				(project.getBranchPath() != null && project.getBranchPath().contains("SNOMEDCT-"))) {
			//If we have a dependency then we're loading an extension so tell traceability
			//that specific CodeSystem branch
			String onBranch = null;
			if (project.getKey().endsWith(".zip")) {
				if (project.getKey().startsWith("SnomedCT_ManagedService")) {
					onBranch = "MAIN/SNOMEDCT-" + project.getKey().substring(23, 25);
				} else {
					throw new TermServerScriptException("Cannot determine CodeSystem from " + project.getKey());
				}
			} else {
				onBranch = project.getBranchPath();
			}
			traceabilityService.setBranchPath(onBranch);
		} else {
			traceabilityService.setBranchPath(project.getBranchPath());
		}
		
		super.postInit(tabNames, columnHeadings, false);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.add(WORD_MATCHES).withType(JobParameter.Type.STRING)
				.add(THIS_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE)
				.add(THIS_DEPENDENCY).withType(JobParameter.Type.STRING)
				.add(PREV_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE)
				.add(PREV_DEPENDENCY).withType(JobParameter.Type.STRING)
				.add(MODULES).withType(JobParameter.Type.STRING)
				.add(INCLUDE_DETAIL).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.add(UNPROMOTED_CHANGES_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("New And Changed Components")
				.withDescription("This report lists all components new and changed in the current release cycle or, by specifying published zip files following a pattern of SnomedCT_InternationalRF2_PRODUCTION_YYYYMMDDT120000Z.zip, between two previous releases.  Optionally, restrict the report to a subset defined by an ECL expression." +
				"The issue count here is the total number of concepts featuring one change or another.  Note that specifying ECL means that inactive concepts will not be included, on account of them having no hierarchial position.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.withExpectedDuration(60)  //BE with large RF2 imports easily hitting this
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		if (loadHistoricallyGeneratedData) {
			LOGGER.info ("Loading Previous Data");
			loadData(prevRelease);
		}
		examineConcepts();
		reportConceptsChanged();
		determineUniqueCountAndTraceability();
		traceabilityService.flush();
		report (PRIMARY_REPORT, "");
		if (!StringUtils.isEmpty(subsetECL)) {
			report (PRIMARY_REPORT, "Run against", subsetECL);
		}
		if (!StringUtils.isEmpty(changesFromET)) {
			report (PRIMARY_REPORT, "Changes since", changesFromET);
		}
		if (!StringUtils.isEmpty(jobRun.getParamValue(WORD_MATCHES))) {
			report (PRIMARY_REPORT, "Word matches", QUOTE + jobRun.getParamValue(WORD_MATCHES) + QUOTE);
		}
		traceabilityService.tidyUp();
		LOGGER.info ("Job complete");
	}
	
	public void examineConcepts() throws TermServerScriptException { 
		int conceptsExamined = 0;
		double lastPercentageReported = 0;
		long notReleased = 0;
		long notChanged = 0;
		long notInScope = 0;
		
		LOGGER.info("Determining concepts of interest");
		Collection<Concept> conceptsOfInterest = determineConceptsOfInterest();
		
		LOGGER.info("Examining " +  conceptsOfInterest.size() + " concepts of interest");
		for (Concept c : conceptsOfInterest) {
			/*if (c.getId().equals("58851000052104")) {
				LOGGER.debug("here");
			}*/
			SummaryCount summaryCount = getSummaryCount(ComponentType.CONCEPT.name());
			if (!loadHistoricallyGeneratedData && c.isReleased() == null) {
				throw new IllegalStateException ("Malformed snapshot. Released status not populated at " + c);
			} else if (inScope(c)) {
				if (!isReleased(c, c, ComponentType.CONCEPT)) {
					notReleased++;
					newConcepts.add(c);
					summaryCount.isNew++;
				} else if (SnomedUtils.hasChangesSince(c, changesFromET, false)) {
					//Only want to log def status change if the concept has not been made inactive
					if (c.isActive()) {
						defStatusChanged.add(c);
						summaryCount.isChanged++;
					} else {
						inactivatedConcepts.add(c);
						summaryCount.isInactivated++;
					}
				} else {
					notChanged++;
				}
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
					if (!isReleased(c, d, ComponentType.DESCRIPTION)) {
						hasNew.add(c);
						isNew = true;
						summaryCount.isNew++;
					} else if (SnomedUtils.hasChangesSince(d, changesFromET, false)) {
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
							if (SnomedUtils.hasChangesSince(l, changesFromET, false)) {
								hasChangedAcceptability.add(c);
								changedAcceptability = true;
							}
						}
					}
					
					//Description inactivation indicators
					summaryCount = getSummaryCount(ComponentType.ATTRIBUTE_VALUE.name() + " - Descriptions");
					for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries()) {
						if (SnomedUtils.hasChangesSince(i, changesFromET, false)) {
							if (isReleased(c, i, ComponentType.ATTRIBUTE_VALUE)) {
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
						if (SnomedUtils.hasChangesSince(h, changesFromET, false)) {
							if (isReleased(c, h, ComponentType.HISTORICAL_ASSOCIATION)) {
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
							if (SnomedUtils.hasChangesSince(l, changesFromET, false)) {
								if (isReleased(c, l, ComponentType.LANGREFSET)) {
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

							if (includeDescriptionDetail && (langRefSetIsNew || langRefSetIsLost || langRefSetIsChanged)) {
								report(DENARY_REPORT, c, c.getEffectiveTime(), d, l, langRefSetIsNew, langRefSetIsChanged, langRefSetIsLost);
							}
						}
					}
				}
				
				if (includeDescriptionDetail && (isNew || isChanged || wasInactivated || changedAcceptability)) {
					report(NONARY_REPORT, c, c.getEffectiveTime(), d.getLang(), d, isNew, isChanged, wasInactivated, changedAcceptability, d.getType());
				}
			}
			
			summaryCount = getSummaryCount(ComponentType.INFERRED_RELATIONSHIP.name());
			for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.BOTH)) {
				if (inScope(r)) {
					if (SnomedUtils.hasChangesSince(r, changesFromET, false)) {
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
					if (SnomedUtils.hasChangesSince(a, changesFromET, false)) {
						if (isReleased(c, a, ComponentType.AXIOM)) {
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
					if (SnomedUtils.hasChangesSince(i, changesFromET, false)) {
						hasChangedInactivationIndicators.add(c);
						if (isReleased(c, i, ComponentType.ATTRIBUTE_VALUE)) {
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
					if (SnomedUtils.hasChangesSince(a, changesFromET, false)) {
						hasChangedAssociations.add(c);
						if (isReleased(c, a, ComponentType.HISTORICAL_ASSOCIATION)) {
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
			double perc = (conceptsExamined / (double)conceptsOfInterest.size()) * 100;
			if (perc >= lastPercentageReported + 10) {
				LOGGER.info ("Examined " + String.format("%.2f", perc) + "%");
				lastPercentageReported = perc;
			}
		}
		
		LOGGER.info ("Not Released: " + notReleased);
		LOGGER.info ("Not Changed: " + notChanged);
		LOGGER.info ("Not In Scope " +  notInScope);
		LOGGER.info ("Total examined: " + conceptsOfInterest.size());
	}

	private Boolean isReleased(Concept c, Component comp, ComponentType type) throws TermServerScriptException {
		//If we're working off a previous release + delta, we'll know this directly
		//otherwise we'll need to lookup the historic data
		if (loadHistoricallyGeneratedData) {
			Collection<String> idsActive = getHistoricData(c, comp, type, true);
			//If either active or inactive ids exist, then this component has been previously published
			if (idsActive.contains(comp.getId())) {
				return true;
			}
			Collection<String> idsInactive = getHistoricData(c, comp, type, false);
			//If either active or inactive ids exist, then this component has been previously published
			if (idsInactive.contains(comp.getId())) {
				return true;
			}
			return false;
		} else {
			return comp.isReleased();
		}
	}

	private Collection<String> getHistoricData(Concept c, Component comp, ComponentType type, boolean active) throws TermServerScriptException {
		Datum datum = prevData.get(c.getConceptId());
		if (datum == null) {
			return Collections.<String>emptySet();
		}
		switch (type) {
			case CONCEPT : return Collections.singleton(c.getConceptId());
			case DESCRIPTION : 
			case TEXT_DEFINITION : return active? datum.descIds : datum.descIdsInact;
			case INFERRED_RELATIONSHIP : return active ? datum.relIds : datum.relIdsInact;
			case AXIOM : return active ? datum.axiomIds : datum.axiomIdsInact;
			case HISTORICAL_ASSOCIATION : return active ? datum.histAssocIds : datum.descHistAssocIdsInact;
			case ATTRIBUTE_VALUE : return active ? datum.inactivationIds : datum.inactivationIdsInact;
			case LANGREFSET : return active ? datum.langRefsetIds : datum.langRefsetIdsInact;
			default : throw new TermServerScriptException("Unexpected component type : " + type);
		}
	}

	private Collection<Concept> determineConceptsOfInterest() throws TermServerScriptException {
		List<Concept> conceptsOfInterest;
		if (!StringUtils.isEmpty(subsetECL)) {
			LOGGER.info("Running Concepts Changed report against subset: " + subsetECL);
			conceptsOfInterest = new ArrayList<>(findConcepts(subsetECL));
		} else {
			conceptsOfInterest = new ArrayList<>(gl.getAllConcepts());
		}
		
		return conceptsOfInterest.stream()
				.filter(c -> hasLexicalMatch(c))
				.filter(c -> SnomedUtils.hasChangesSinceIncludingSubComponents(c, changesFromET, false))
				.sorted((c1, c2) -> SnomedUtils.compareSemTagFSN(c1,c2))
				.collect(Collectors.toList());
	}

	private boolean hasLexicalMatch(Concept c) {
		if (wordMatches == null || wordMatches.size() == 0) {
			return true;
		}
		
		for (Description d : c.getDescriptions()) {
			if (!d.isActive() || d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
				continue;
			}
			for (String word : wordMatches) {
				if (d.getTerm().toLowerCase().contains(word)) {
					return true;
				}
			}
		}
		return false;
	}

	private void reportConceptsChanged() throws TermServerScriptException {
		
		HashSet<Concept> superSet = new HashSet<>();
		superSet.addAll(newConcepts);
		superSet.addAll(defStatusChanged);
		superSet.addAll(inactivatedConcepts);
		LOGGER.debug ("Creating concept report for " + superSet.size() + " concepts");
		for (Concept c : SnomedUtils.sort(superSet)) {
			populateTraceabilityAndReport(SECONDARY_REPORT, c,
				c.getEffectiveTime(),
				c.isActive()?"Y":"N",
				newConcepts.contains(c)?"Y":"N",
				defStatusChanged.contains(c)?"Y":"N",
				getLanguages(c));
		}
		superSet.clear();
		
		superSet.addAll(hasNewInferredRelationships);
		superSet.addAll(hasLostInferredRelationships);
		LOGGER.debug ("Creating relationship report for " + superSet.size() + " concepts");
		for (Concept c : SnomedUtils.sort(superSet)) {
			String newWithNewConcept = hasNewInferredRelationships.contains(c) && newConcepts.contains(c) ? "Y":"N";
			report(TERTIARY_REPORT, c,
				c.getEffectiveTime(),
				c.isActive()?"Y":"N",
				newWithNewConcept,
				hasNewInferredRelationships.contains(c)?"Y":"N",
				hasLostInferredRelationships.contains(c)?"Y":"N");
		}
		superSet.clear();
		
		superSet.addAll(hasNewAxioms);
		superSet.addAll(hasChangedAxioms);
		superSet.addAll(hasLostAxioms);
		LOGGER.debug ("Creating axiom report for " + superSet.size() + " concepts");
		for (Concept c : SnomedUtils.sort(superSet)) {
			String newWithNewConcept = hasNewAxioms.contains(c) && newConcepts.contains(c) ? "Y":"N";
			populateTraceabilityAndReport(QUATERNARY_REPORT, c,
				c.getEffectiveTime(),
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
		LOGGER.debug ("Creating description report for " + superSet.size() + " concepts");
		boolean includeTraceability = superSet.size() < MAX_ROWS_FOR_TRACEABILITY ? true : false;
		
		if (forceTraceabilityPopulation) {
			includeTraceability = true;
		}
		if (!includeTraceability) {
			report (QUINARY_REPORT, "", "", "", "", "", "", "", "Cannot report traceability for > 10K rows due to performance constraints");
		}
		for (Concept c : SnomedUtils.sort(superSet)) {
			String newWithNewConcept = (hasNewDescriptions.contains(c) && newConcepts.contains(c)) ? "Y":"N";
			if (includeTraceability) {
				populateTraceabilityAndReport(QUINARY_REPORT, c,
					c.getEffectiveTime(),
					c.isActive()?"Y":"N",
					newWithNewConcept,
					hasNewDescriptions.contains(c)?"Y":"N",
					hasChangedDescriptions.contains(c)?"Y":"N",
					hasLostDescriptions.contains(c)?"Y":"N",
					hasChangedAcceptabilityDesc.contains(c)?"Y":"N");
			} else {
				report(QUINARY_REPORT, c,
					c.getEffectiveTime(),
					c.isActive()?"Y":"N",
					newWithNewConcept,
					hasNewDescriptions.contains(c)?"Y":"N",
					hasChangedDescriptions.contains(c)?"Y":"N",
					hasLostDescriptions.contains(c)?"Y":"N",
					hasChangedAcceptabilityDesc.contains(c)?"Y":"N");
			}
		}
		getSummaryCount("Concepts with Descriptions").isNew = hasNewDescriptions.size();
		getSummaryCount("Concepts with Descriptions").isChanged = hasChangedDescriptions.size();
		getSummaryCount("Concepts with Descriptions").isInactivated = hasLostDescriptions.size();
		superSet.clear();

		superSet.addAll(hasNewTextDefn);
		superSet.addAll(hasChangedTextDefn);
		superSet.addAll(hasLostTextDefn);
		superSet.addAll(hasChangedAcceptabilityTextDefn);

		LOGGER.debug ("Creating text defn report for " + superSet.size() + " concepts");
		for (Concept c : SnomedUtils.sort(superSet)) {
			String newWithNewConcept = (hasNewTextDefn.contains(c) && newConcepts.contains(c)) ? "Y":"N";
			populateTraceabilityAndReport(SENARY_REPORT, c,
					c.getEffectiveTime(),
					c.isActive()?"Y":"N",
					newWithNewConcept,
					hasNewTextDefn.contains(c)?"Y":"N",
					hasChangedTextDefn.contains(c)?"Y":"N",
					hasLostTextDefn.contains(c)?"Y":"N",
					hasChangedAcceptabilityTextDefn.contains(c)?"Y":"N");
		}
		getSummaryCount("Concepts with TextDefn").isNew = hasNewTextDefn.size();
		getSummaryCount("Concepts with TextDefn").isChanged = hasChangedTextDefn.size();
		getSummaryCount("Concepts with TextDefn").isInactivated = hasLostTextDefn.size();
		superSet.clear();
		
		superSet.addAll(hasChangedAssociations);
		superSet.addAll(hasChangedInactivationIndicators);
		LOGGER.debug ("Creating association report for " + superSet.size() + " concepts");
		for (Concept c : SnomedUtils.sort(superSet)) {
			populateTraceabilityAndReport(SEPTENARY_REPORT, c,
				c.getEffectiveTime(),
				c.isActive()?"Y":"N",
				hasChangedAssociations.contains(c)?"Y":"N",
				hasChangedInactivationIndicators.contains(c)?"Y":"N");
		}
		superSet.clear();
		
		superSet.addAll(isTargetOfNewInferredRelationship);
		superSet.addAll(wasTargetOfLostInferredRelationship);
		LOGGER.debug ("Creating incoming relationship report for " + superSet.size() + " concepts");
		for (Concept c : SnomedUtils.sort(superSet)) {
			report(OCTONARY_REPORT, c,
				c.getEffectiveTime(),
				c.isActive()?"Y":"N",
				isTargetOfNewInferredRelationship.contains(c)?"Y":"N",
				wasTargetOfLostInferredRelationship.contains(c)?"Y":"N");
		}
		superSet.clear();
		
		//Populate the summary numbers for each type of component
		List<String> summaryCountKeys = new ArrayList<>(summaryCounts.keySet());
		Collections.sort(summaryCountKeys);
		for (String componentType : summaryCountKeys) {
			SummaryCount sc = summaryCounts.get(componentType);
			String componentName = StringUtils.capitalizeFirstLetter(componentType.toString().toLowerCase());
			if (!componentName.endsWith("s") && !componentName.contains(" - ")) {
				componentName += "s";
			}
			report(PRIMARY_REPORT, componentName, sc.isNew, sc.isChanged, sc.isInactivated);
		}
	}
	
	private void populateTraceabilityAndReport(int tabIdx, Concept c, Object... data) throws TermServerScriptException {
		//Are we working on a published release, or "in-flight" project?
		String fromDate = null;
		if (project.getKey().endsWith(".zip")) {
			fromDate = changesFromET;
		} else {
			//We're now working on monthly releases, so it could be anything in the last 3 months tops
			Date fromDateDate = DateUtils.addDays(new Date(),-180);
			fromDate = dateFormat.format(fromDateDate);
		}
		traceabilityService.populateTraceabilityAndReport(fromDate, null, tabIdx, c, data);
	}

	private String getLanguages(Concept c) {
		Set<String> langs = new TreeSet<>();
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			langs.add(d.getLang());
		}
		return String.join(", ", langs);
	}

	private void determineUniqueCountAndTraceability() {
		LOGGER.debug("Determining unique count");
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
	
	class PassThroughTraceability implements TraceabilityService {
		
		TermServerScript ts;
		
		PassThroughTraceability (TermServerScript ts) {
			this.ts = ts;
		}

		@Override
		public void flush() throws TermServerScriptException {
			ts.getReportManager().flushFilesSoft();
		}

		@Override
		public void populateTraceabilityAndReport(int tabIdx, Concept c, Object... details)
				throws TermServerScriptException {
			ts.report(tabIdx, c , details);
		}

		@Override
		public void tidyUp() throws TermServerScriptException {
			ts.getReportManager().flushFilesSoft();
		}

		@Override
		public void populateTraceabilityAndReport(String fromDate, String toDate, int tabIdx, Concept c, Object... details)
				throws TermServerScriptException {
			ts.report(tabIdx, c , details);
		}

		@Override
		public void setBranchPath(String onBranch) {
		}

		@Override
		public int populateTraceabilityAndReport(int tabIdx, Component c, Object... details)
				throws TermServerScriptException {
			return NOT_SET;
		}
	}
	
}

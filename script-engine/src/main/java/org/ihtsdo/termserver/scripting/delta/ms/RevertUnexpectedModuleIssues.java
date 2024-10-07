package org.ihtsdo.termserver.scripting.delta.ms;

import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * MSSP-1425 Find eg descriptions with core namespace SCTIDs which have switched
 * module and have no effective time and check with a published branch if we can
 * revert those back to the core module along with any langrefset entries.
 *
 * Ah, we've a problem here in that these components jumped module without losing their
 * effective time.  So we need a snapshot export to fix it, because the delta won't give
 * you any component that still has an effective tim
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RevertUnexpectedModuleIssues extends DeltaGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(RevertUnexpectedModuleIssues.class);

	private static final String INT_RELEASE_BRANCH = "MAIN/2022-01-31";
	private static final String EXT_RELEASE_BRANCH = "MAIN/SNOMEDCT-SE/2021-11-30";
	private List<String> intReleaseDates = new ArrayList<>();
	
	static List<String> checkNoChangeDelta = new ArrayList<>();

	Map<String, Concept> publishedIntConceptCache = new HashMap<>();
	Map<String, Concept> publishedExtConceptCache = new HashMap<>();
	Map<String, RefsetMember> publishedMemberCache = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		RevertUnexpectedModuleIssues delta = new RevertUnexpectedModuleIssues();
		try {
			delta.runStandAlone = false;
			delta.inputFileHasHeaderRow = true;
			delta.newIdsRequired = false;
			delta.init(args);
			delta.loadProjectSnapshot(false);
			delta.postInit();
			delta.process();
			delta.getRF2Manager().flushFiles(true);
			delta.createOutputArchive(false);
		} finally {
			delta.finish();
		}
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setReleasedFlagPopulated(true);
		ReportSheetManager.setTargetFolderId("1mvrO8P3n94YmNqlWZkPJirmFKaFUnE0o"); //Managed Service
		subsetECL = run.getParamValue(ECL);
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Action, ComponentType, Component Reasserted"};
		String[] tabNames = new String[] {	
				"Reassertions"};
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public void process() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			boolean doOutputRF2 = false;
			Concept published = null;
			if (hasModifiedCoreComponent(c)) {
				published = loadIntConcept(c);
				if (published == null) {
					report(c, ReportActionType.VALIDATION_CHECK, "Extension concept has core components");
				}
				doOutputRF2 = revertCoreComponents(c, published);
			}
			
			//Is this one of the components we're to manually check for no change delta?
			if (revertNoChangeDeltaIfRequired(c)) {
				doOutputRF2 = true;
			}
			
			if (hasStolenComponent(c)) {
				if (published == null) {
					published = loadIntConcept(c);
				}
				revertDescriptionsIfRequired(c, published);
			}
			
			if (published != null || doOutputRF2) {
				outputRF2(c, true);  //Will only output dirty fields.
			}
		}
	}

	Concept loadIntConcept(Concept c) throws TermServerScriptException {
		Concept published = publishedIntConceptCache.get(c.getId());
		if (published == null) {
			published = loadConcept(c, INT_RELEASE_BRANCH);
			publishedIntConceptCache.put(c.getId(), published);
		}
		return published;
	}
	
	Concept loadExtConcept(Concept c) throws TermServerScriptException {
		Concept published = publishedExtConceptCache.get(c.getId());
		if (published == null) {
			published = loadConcept(c, EXT_RELEASE_BRANCH);
			publishedExtConceptCache.put(c.getId(), published);
		}
		return published;
	}
	
	RefsetMember loadMember(String uuid) throws TermServerScriptException {
		RefsetMember published = publishedMemberCache.get(uuid);
		if (published == null) {
			published = loadRefsetMember(uuid, INT_RELEASE_BRANCH);
			publishedMemberCache.put(uuid, published);
		}
		return published;
	}

	private boolean hasModifiedCoreComponent(Concept c) {
		for (Component component : SnomedUtils.getAllComponents(c)) {
			if (SnomedUtils.isCore(component) && StringUtils.isEmpty(component.getEffectiveTime())) {
				return true;
			}
		}
		return false;
	}
	
	
	private boolean revertNoChangeDeltaIfRequired(Concept c) throws TermServerScriptException {
		for (Map.Entry<String, Component> entry : SnomedUtils.getAllComponentsMap(c).entrySet()) {
			if (checkNoChangeDelta.contains(entry.getKey())) {
				Concept published = loadExtConcept(c);
				Component comp = entry.getValue();
				Map<String, Component> publishedMap = SnomedUtils.getAllComponentsMap(published);
				Component publishedComp = publishedMap.get(entry.getKey());
				if (comp.fieldComparison(publishedComp, true).isEmpty()) {
					comp.setEffectiveTime(publishedComp.getEffectiveTime());
					comp.setDirty();
					report(c, ReportActionType.EFFECTIVE_TIME_REVERTED, comp.getComponentType(), comp);
					return true;
				} else {
					report(c, ReportActionType.VALIDATION_CHECK, "No change delta shows change", comp, publishedComp);
				}
			}
		}
		return false;
	}
	
	private boolean revertCoreComponents(Concept c, Concept published) throws TermServerScriptException {
		Map<String, Component> publishedMap = new HashMap<>();
		boolean componentModified = false;
		if (published != null) {
			publishedMap = SnomedUtils.getAllComponentsMap(published);
		}
		
		for (Component component : SnomedUtils.getAllComponents(c)) {
			if (SnomedUtils.isCore(component) && StringUtils.isEmpty(component.getEffectiveTime())) {
				//Do we have this already or do we need to load it specially?
				Component publishedComponent = publishedMap.get(component.getId());
				if (published != null && publishedComponent == null) {
					LOGGER.debug("Recovering published version of {}", component);
					publishedComponent = loadMember(component.getId());
				}
				
				//If this is a refset member and it doesn't exist in the International Edition
				//Then it should probably belong to the extension
				if (publishedComponent == null) {
					if (component.getId().contains("-")) {
						component.setDirty();
						component.setModuleId(targetModuleId);
						report(c, ReportActionType.INFO, "Apparently modified Core component doesn't exist in " + INT_RELEASE_BRANCH);
						report(c, ReportActionType.MODULE_CHANGE_MADE, component.getComponentType(), component);
						componentModified = true;
						continue;
					} else {
						LOGGER.warn("Unable to obtain published version of {}", component);
						continue;
					}
				}
				
				//If the component is a relationship and it has been inactivated, then that should have happened in the extension module
				//otherwise, revert the core component
				if (component.getComponentType().equals(ComponentType.INFERRED_RELATIONSHIP) &&
						publishedComponent.isActiveSafely() != component.isActiveSafely()) {
					component.setModuleId(targetModuleId);
					report(c, ReportActionType.MODULE_CHANGE_MADE, component.getComponentType(), component);
				} else {
					component.setActive(publishedComponent.isActiveSafely());
					component.setEffectiveTime(publishedComponent.getEffectiveTime());
					report(c, ReportActionType.EFFECTIVE_TIME_REVERTED, component.getComponentType(), component);
					if (!component.fieldComparison(publishedComponent, true).isEmpty()) {
						LOGGER.debug("Check Me: {} vs {}", component, publishedComponent);
					}
				}
				component.setDirty();
				componentModified = true;
			}
		}
		return componentModified;
	}

	private void revertDescriptionsIfRequired(Concept c, Concept published) throws TermServerScriptException {
		for (Description d : c.getDescriptions()) {
			if (StringUtils.isEmpty(d.getEffectiveTime())
					&& !SnomedUtils.hasExtensionSCTID(d) 
					&& !SnomedUtils.isCore(d)) {
				Description publishedDesc = published.getDescription(d.getDescriptionId());
				d.setModuleId(publishedDesc.getModuleId());
				//Make sure to set the effectiveTime AFTER the active state, because changing state
				//resets the effective date!
				d.setActive(publishedDesc.isActiveSafely());
				d.setEffectiveTime(publishedDesc.getEffectiveTime());
				d.setDirty();
				report(c, ReportActionType.MODULE_CHANGE_MADE, d.getComponentType(), d);
			}
			
			if (!SnomedUtils.hasExtensionSCTID(d)) {
				//Check all langrefset entries independently of descriptions
				for (LangRefsetEntry l : d.getLangRefsetEntries()) {
					if (StringUtils.isEmpty(l.getEffectiveTime())
							&& !SnomedUtils.isCore(l)
							&& SnomedUtils.isEnglishDialect(l)) {
						//Need to find from members endpoint as concept does not have full details of refset members
						RefsetMember publishedLRS = loadRefsetMember(l.getId(), INT_RELEASE_BRANCH);
						l.setModuleId(publishedLRS.getModuleId());
						//Make sure to set the effectiveTime AFTER the active state, because changing state
						//resets the effective date!
						l.setActive(publishedLRS.isActiveSafely());
						l.setEffectiveTime(publishedLRS.getEffectiveTime());
						l.setDirty();
						report(c, ReportActionType.MODULE_CHANGE_MADE, l.getComponentType(), l);
					}
				}
			}
			
			if (!SnomedUtils.hasExtensionSCTID(d)) {
				//Check all description associations and inactivation indicators
				for (RefsetMember i : d.getInactivationIndicatorEntries()) {
					if (StringUtils.isEmpty(i.getEffectiveTime())
							&& !SnomedUtils.isCore(i)) {
						RefsetMember publishedRM = loadRefsetMember(i.getId(), INT_RELEASE_BRANCH);
						if (publishedRM != null) {
							i.setModuleId(publishedRM.getModuleId());
							//Make sure to set the effectiveTime AFTER the active state, because changing state
							//resets the effective date!
							i.setActive(publishedRM.isActiveSafely());
							i.setEffectiveTime(published.getEffectiveTime());
							i.setDirty();
							report(c, ReportActionType.MODULE_CHANGE_MADE, i.getComponentType(), i);
						}
					}
				}
				
				for (RefsetMember a : d.getAssociationEntries()) {
					if (StringUtils.isEmpty(a.getEffectiveTime())
							&& !SnomedUtils.isCore(a)) {
						RefsetMember publishedRM = loadRefsetMember(a.getId(), INT_RELEASE_BRANCH);
						if (publishedRM != null) {
							a.setModuleId(publishedRM.getModuleId());
							//Make sure to set the effectiveTime AFTER the active state, because changing state
							//resets the effective date!
							a.setActive(publishedRM.isActiveSafely());
							a.setEffectiveTime(published.getEffectiveTime());
							a.setDirty();
							report(c, ReportActionType.MODULE_CHANGE_MADE, a.getComponentType(), a);
						}
					}
				}
			}
		}
	}

	private boolean hasStolenComponent(Concept c) throws TermServerScriptException {
		reportConcetLevelIssues(c);
		
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.BOTH)) {
			reportRelationshipLevelIssues(c, r);
		}
		
		for (Description d : c.getDescriptions()) {
			if (StringUtils.isEmpty(d.getEffectiveTime())
					&& !SnomedUtils.hasExtensionSCTID(d) 
					&& !SnomedUtils.isCore(d)) {
				return true;
			}
			
			if (reportDescriptionLevelIssues(d)) {
				return true;
			}
		}
		return false;
	}

	private boolean reportDescriptionLevelIssues(Description d) {
		if (!SnomedUtils.hasExtensionSCTID(d)) {
			//Langrefset entries might have shifted without the description itself going
			for (LangRefsetEntry l : d.getLangRefsetEntries()) {
				if (StringUtils.isEmpty(l.getEffectiveTime())
						&& SnomedUtils.isEnglishDialect(l) 
						&& !SnomedUtils.isCore(l)) {
					return true;
				}
			}
		}
		
		//Check all description associations and inactivation indicators
		for (RefsetMember i : d.getInactivationIndicatorEntries()) {
			if (StringUtils.isEmpty(i.getEffectiveTime())
					&& !SnomedUtils.isCore(i)) {
				return true;
			}
		}
		
		for (RefsetMember a : d.getAssociationEntries()) {
			if (StringUtils.isEmpty(a.getEffectiveTime())
					&& !SnomedUtils.isCore(a)) {
				return true;
			}
		}
		return false;
	}

	private void reportRelationshipLevelIssues(Concept c, Relationship r) throws TermServerScriptException {
		if (!SnomedUtils.hasExtensionSCTID(r) 
				&& !SnomedUtils.isCore(r)) {
			if (r.isActiveSafely() && StringUtils.isEmpty(r.getEffectiveTime())) {
				report(c, ReportActionType.VALIDATION_CHECK, "Active Modified Rel with Core SCTID in Extension", r);
			} else if (!r.isActiveSafely()) {
				reportInactiveRelationshipIusses(c, r);
			}
		}
		
	}

	private void reportInactiveRelationshipIusses(Concept c, Relationship r) throws TermServerScriptException {
		Concept published = loadConceptPriorTo(c, r.getEffectiveTime());
		Relationship pubRel = published.getRelationship(r.getId());
		if (pubRel == null) {
			report(c, ReportActionType.VALIDATION_CHECK, "Core SCTID not found in " + INT_RELEASE_BRANCH, r);
		} else if (!pubRel.isActiveSafely()) {
			if (StringUtils.isEmpty(r.getEffectiveTime())) {
				report(c, ReportActionType.VALIDATION_CHECK, "Inactive Rel moved without reason. Fresh.", r);
			} else {
				report(c, ReportActionType.VALIDATION_CHECK, "Inactive Rel moved without reason. Historic.", r);
			}
		} else {
			report(c, ReportActionType.INFO, "Active core rel inactivated by extension.  All Good.", r);
		}
	}

	private void reportConcetLevelIssues(Concept c) throws TermServerScriptException {
		if (StringUtils.isEmpty(c.getEffectiveTime())
				&& !SnomedUtils.hasExtensionSCTID(c) 
				&& !SnomedUtils.isCore(c)) {
			Concept published = loadConcept(c, INT_RELEASE_BRANCH);
			if (published.isActiveSafely()) {
				report(c, ReportActionType.VALIDATION_CHECK, "Core concept moved to extension - check me");
			} else {
				if (c.isActiveSafely()) {
					report(c, ReportActionType.INFO, "Core concept inactivated, reactivated in extension");
				} else {
					report(c, ReportActionType.VALIDATION_CHECK, "Core concept inactivated.  Check Me.");
				}
			}
		}
		
	}

	private Concept loadConceptPriorTo(Concept c, String effectiveTime) throws TermServerScriptException {
		if (intReleaseDates.isEmpty()) {
			generateReleaseDates();
		}
		String branch = getIntBranchPriorTo(effectiveTime);
		return loadConcept(c.getId(), branch);
	}

	private String getIntBranchPriorTo(String effectiveTime) {
		if (StringUtils.isEmpty(effectiveTime)) {
			return INT_RELEASE_BRANCH;
		}
		for (int i=0; i < intReleaseDates.size(); i++) {
			if (effectiveTime.compareTo(intReleaseDates.get(i+1)) < 0) {
				String intRelease = intReleaseDates.get(i);
				return "MAIN/" + intRelease.substring(0, 4) + "-" + intRelease.substring(4, 6) + "-" + intRelease.substring(6, 8);
			}
		}
		throw new RuntimeException("Could not find the release prior to " + effectiveTime);
	}

	private void generateReleaseDates() {
		for (int i = 2002; i <= 2022; i++) {
			intReleaseDates.add("" + i + "0131");
			intReleaseDates.add("" + i + "0731");
		}
	}

}

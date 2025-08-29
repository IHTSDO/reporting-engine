package org.ihtsdo.termserver.scripting.fixes.managed_service;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import java.util.*;

public class AlignComponentModuleToOwningConcept extends BatchFix implements ScriptConstants {

	enum FixRequired {ALIGN_MODULE_TO_CONCEPT, ALIGN_MODULE_TO_TARGET}
	Map<Concept, FixRequired> fixRequiredMap = new HashMap<>();

	protected AlignComponentModuleToOwningConcept(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		AlignComponentModuleToOwningConcept fix = new AlignComponentModuleToOwningConcept(null);
		try {
			fix.selfDetermining = true;
			fix.init(args);
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		Concept loadedConcept = loadConcept(c, t.getBranchPath());
		try {
			switch (fixRequiredMap.get(c)) {
				case ALIGN_MODULE_TO_CONCEPT:
					changesMade += alignModuleToConcept(t,loadedConcept);
					break;
				case ALIGN_MODULE_TO_TARGET:
					changesMade += alignModuleToTarget(t,loadedConcept);
					break;
			}

			if (changesMade > 0) {
				updateConcept(t, loadedConcept, info);
			}
		} catch (ValidationFailure v) {
			report(t, c, v);
		} catch (Exception e) {
			report(t, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

	private int alignModuleToConcept(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Component comp : SnomedUtils.getAllComponents(c)) {
			if (!comp.isActiveSafely()) {
				continue;
			}

			if (!comp.getModuleId().equals(c.getModuleId())
				&& !gl.getMdrs().getDependencies(comp.getModuleId()).contains(c.getModuleId())) {
				changesMade += setComponentModule(t, c, comp, c.getModuleId(), "Component");
			}

			//If the component is a langrefset object, we'll need to save those separately because the json view
			//of a langrefset member does not include the moduleId
			if (comp instanceof Description d) {
				changesMade += checkAlignmentOfDescription(t, c, d);
			}
		}
		return changesMade;
	}

	private int checkAlignmentOfDescription(Task t, Concept c, Description d) throws TermServerScriptException {
		int changesMade = 0;
		//We need to use the in-memory version of this description, to have access to the full langrefset entries
		Description dInMemory = gl.getDescription(d.getDescriptionId());
		for (LangRefsetEntry lr : dInMemory.getLangRefsetEntries(ActiveState.ACTIVE)) {
			if (!lr.getModuleId().equals(d.getModuleId())
					&&  !gl.getMdrs().getDependencies(lr.getModuleId()).contains(d.getModuleId())) {
				changesMade += setComponentModule(t, c, lr, d.getModuleId(), "LangRefsetEntry");
				updateRefsetMember(t, lr, "");
			}
		}
		return changesMade;
	}

	private int setComponentModule(Task t, Concept c, Component comp, String moduleId, String updateType) throws TermServerScriptException {
		//Avoid changing module of components currently in the core module
		if (comp.getModuleId().equals(SCTID_CORE_MODULE)) {
			return NO_CHANGES_MADE;
		}
		String details = String.format("%s module %s -> %s : %s ", updateType, comp.getModuleId(), moduleId, comp);
		comp.setModuleId(moduleId);
		report(t, c, Severity.LOW, ReportActionType.COMPONENT_UPDATED, details);
		return CHANGE_MADE;
	}

	private int alignModuleToTarget(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (inScope(r)
					&& !r.isConcrete()
					&& !r.getModuleId().equals(r.getTarget().getModuleId())
					&& !gl.getMdrs().getDependencies(r.getModuleId()).contains(r.getTarget().getModuleId())) {
				changesMade += setComponentModule(t, c, r, r.getTarget().getModuleId(), "Relationship");
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//There are two types of fixes required - ALIGN_MODULE_TO_CONCEPT and ALIGN_MODULE_TO_TARGET
		//collect each and populate the fixesRequired map

		//Aligning to concept is only relevant to extension concepts, so filter for inScope
		gl.getAllConcepts().stream()
				.filter(this::inScope)
				.filter(Concept::isActiveSafely)
				.filter(this::hasComponentsInDifferentModule)
				.forEach(c -> fixRequiredMap.put(c, FixRequired.ALIGN_MODULE_TO_CONCEPT));

		//We also need to check the langrefset entries for translated descriptions
		gl.getAllConcepts().stream()
				.filter(this::hasLangRefsetInDifferentModule)
				.forEach(c -> fixRequiredMap.put(c, FixRequired.ALIGN_MODULE_TO_CONCEPT));

		//Now International Concepts might get given a relationship that points to a concept in some child module.
		//In this case, the relationship itself should align to the module of the _target_ concept
		gl.getAllConcepts().stream()
				.filter(Concept::isActiveSafely)
				.filter(this::hasRelationshipTargetInScopedModule)
				.forEach(c -> fixRequiredMap.put(c, FixRequired.ALIGN_MODULE_TO_TARGET));
		return new ArrayList<>(fixRequiredMap.keySet());


	}

	private boolean hasComponentsInDifferentModule(Concept c) {
		String conceptModule = c.getModuleId();
		return SnomedUtils.getAllComponents(c).stream()
				.filter(Component::isActiveSafely)
				.anyMatch(comp -> !comp.getModuleId().equals(conceptModule));
	}

	private boolean hasRelationshipTargetInScopedModule(Concept c) {
		return c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)
				.stream()
				.filter(this::inScope)
				.anyMatch(r -> !r.isConcrete()
						&& !r.getModuleId().equals(r.getTarget().getModuleId())
						&& !gl.getMdrs().getDependencies(r.getModuleId()).contains(r.getTarget().getModuleId()));
	}


	private boolean hasLangRefsetInDifferentModule(Concept c) {
		return c.getDescriptions(ActiveState.ACTIVE)
				.stream()
				.anyMatch(this::hasLangRefsetInDifferentModule);
	}

	private boolean hasLangRefsetInDifferentModule(Description d) {
		return d.getLangRefsetEntries(ActiveState.ACTIVE)
				.stream()
				.anyMatch(lr-> !lr.getModuleId().equals(d.getModuleId())
						&& !gl.getMdrs().getDependencies(lr.getModuleId()).contains(d.getModuleId()));
	}


}

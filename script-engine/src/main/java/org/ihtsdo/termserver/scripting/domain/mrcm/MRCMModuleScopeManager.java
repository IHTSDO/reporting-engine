package org.ihtsdo.termserver.scripting.domain.mrcm;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

// mrcmRuleRefsetId
public class MRCMModuleScopeManager implements ScriptConstants  {

	private static final Logger LOGGER = LoggerFactory.getLogger(MRCMModuleScopeManager.class);

	private GraphLoader gl;

	private Map<Concept, Map<UUID, MRCMModuleScope>> mrcmStagingModuleScopeMap = new HashMap<>();

	private Map<Concept, Set<MRCMModuleScope>> mrcmModuleScopeMap = new HashMap<>();

	public MRCMModuleScopeManager(GraphLoader graphLoader) {
		this.gl = graphLoader;
	}

	//Note: No need to return the staging variants of these, as they're only used
	//temporarily by the GraphLoader
	public Map<Concept, Set<MRCMModuleScope>> getMrcmModuleScopeMap() {
		return mrcmModuleScopeMap;
	}

	public void reset() {
		mrcmStagingModuleScopeMap = new HashMap<>();
		mrcmModuleScopeMap = new HashMap<>();
	}

	public void loadFile(InputStream is, Boolean isReleased) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				//Allow trailing empty fields
				String[] lineItems = line.split(FIELD_DELIMITER, -1);

				if (gl.doCheckForExcludedModules() && gl.isExcluded(lineItems[IDX_MODULEID])) {
					continue;
				}
				MRCMModuleScope ar = MRCMModuleScope.fromRf2(lineItems);

				//Only set the released flag if it's not set already
				if (ar.isReleased() == null) {
					ar.setReleased(isReleased);
				}
				addToMRCMModuleScopeMap(mrcmStagingModuleScopeMap, ar);
			} else {
				isHeaderLine = false;
			}
		}
	}

	private void addToMRCMModuleScopeMap(Map<Concept, Map<UUID, MRCMModuleScope>> mrcmStagingModuleMap, MRCMModuleScope ar) throws TermServerScriptException {
		//We'll add all entries to a staging structure initially, to allow conflicts to be resolved
		//in a delta.
		Concept module = gl.getConcept(ar.getModuleId());
		Map<UUID, MRCMModuleScope> applicableRefsets = mrcmStagingModuleMap.computeIfAbsent(module, k -> new HashMap<>());
		//This will overwrite any existing MRCM row with the same UUID
		//And allow multiple rows for exist for a given referenced component id
		UUID uuid = UUID.fromString(ar.getId());
		applicableRefsets.put(uuid, ar);
	}

	private boolean finaliseMRCMModuleScope(
			Map<Concept, Map<UUID, MRCMModuleScope>> mrcmStagingModuleScopeMap,
			Map<Concept, Set<MRCMModuleScope>> mrcmModuleScopeMap, List<String> unresolvableConflicts) {
		boolean acceptablyFinalised = true;
		mrcmModuleScopeMap.clear();
		for (Map.Entry<Concept, Map<UUID, MRCMModuleScope>> entry: mrcmStagingModuleScopeMap.entrySet()) {
			Concept module = entry.getKey();
			Map<UUID, MRCMModuleScope> refsetsToApplyToModule = entry.getValue();
			//Split this into a map per refsetId and if we have more than one refset member specifying that refset for each module
			Map<String, Set<MRCMModuleScope>> refsetmembersByRefsetId = refsetsToApplyToModule.values().stream()
					.collect(Collectors.groupingBy(MRCMModuleScope::getRuleRefsetId, Collectors.toSet()));
			for (Set<MRCMModuleScope> refsetMembers: refsetmembersByRefsetId.values()) {
				acceptablyFinalised = finaliseRefsetMembers(module, refsetMembers, unresolvableConflicts);
			}
		}
		return acceptablyFinalised;
	}

	private boolean finaliseRefsetMembers(Concept module, Set<MRCMModuleScope> refsetMembers, List<String> unresolvableConflicts) {
		boolean acceptablyFinalised = true;
		if (refsetMembers.size() == 1) {
			Set<MRCMModuleScope> ruleRefsetToApply = mrcmModuleScopeMap.get(module);
			if (ruleRefsetToApply == null) {
				ruleRefsetToApply = new HashSet<>();
				mrcmModuleScopeMap.put(module, ruleRefsetToApply);
			}
			ruleRefsetToApply.add(refsetMembers.iterator().next());
		} else {
			//We have multiple refset members for the same module and refset
			//We need to pick one to apply
			MRCMModuleScope winningRefset = pickWinningMRCMModuleScope(module, refsetMembers, unresolvableConflicts);
			if (winningRefset != null) {
				Set<MRCMModuleScope> ruleRefsetToApply = mrcmModuleScopeMap.computeIfAbsent(module, k -> new HashSet<>());
				ruleRefsetToApply.add(winningRefset);
			} else {
				acceptablyFinalised = false;
			}
		}
		return acceptablyFinalised;
	}

	private MRCMModuleScope pickWinningMRCMModuleScope(Concept refComp,Set<MRCMModuleScope> refsetMembers, List<String> conflictingAttributes) {
		//Return 1 active row for each of INT and EXT, or null if there's no active row, or multiple active rows
		MRCMModuleScope intAR = pickActiveMRCMModuleScope(refsetMembers, true);
		MRCMModuleScope extAR = pickActiveMRCMModuleScope(refsetMembers, false);
		if (intAR != null && extAR != null) {
			String detail = " (" + intAR.getId() + " in module " + intAR.getModuleId() + " vs " + extAR.getId() + " in module " + extAR.getModuleId() + ")";
			conflictingAttributes.add(refComp + detail);
			return extAR;
		}
		return null;
	}

	private MRCMModuleScope pickActiveMRCMModuleScope(Set<MRCMModuleScope> refsetMembers, boolean isInternational) {
		List<MRCMModuleScope> activeRanges = refsetMembers.stream()
				.filter(Component::isActive)
				.filter(ar -> isInternational == SnomedUtils.isInternational(ar))
				.toList();
		if (activeRanges.size() == 1) {
			return activeRanges.get(0);
		}
		return null;
	}

	public void finaliseFromStagingArea(List<String> integrityWarnings) throws TermServerScriptException {
		//Only current work required here is to verify that the Delta import has resolved
		//any apparent conflicts in the MRCM.  Convert the staging collection (which will hold duplicates)
		//into the non-staging collection only if all duplications have been resolved.
		LOGGER.info("Finalising ModuleScope MRCM from Staging Area");
		List<String> conflictingAttributes = new ArrayList<>();
		boolean allContentAcceptable = finaliseMRCMModuleScope(mrcmStagingModuleScopeMap, mrcmModuleScopeMap, conflictingAttributes);

		if (!conflictingAttributes.isEmpty()) {
			String msg = "MRCM ModuleScope File conflicts: \n";
			msg += conflictingAttributes.stream().collect(Collectors.joining(",\n"));
			if (allContentAcceptable || !gl.isRunIntegrityChecks()) {
				integrityWarnings.add(msg);
			} else {
				throw new TermServerScriptException(msg);
			}
		}
	}
}

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

//id,effectiveTime,active,moduleId,refsetId,referencedComponentId,domainConstraint,parentDomain,proximalPrimitiveConstraint,proximalPrimitiveRefinement,domainTemplateForPrecoordination,domainTemplateForPostcoordination,guideURL
public class MRCMDomainManager implements ScriptConstants  {

	private static final String IN_MODULE = " in module ";
	private static final Logger LOGGER = LoggerFactory.getLogger(MRCMDomainManager.class);

	private GraphLoader gl;

	private Map<Concept, Map<String, MRCMDomain>> mrcmStagingDomainMap = new HashMap<>();

	private Map<Concept, MRCMDomain> mrcmDomainMap = new HashMap<>();

	public MRCMDomainManager(GraphLoader graphLoader) {
		this.gl = graphLoader;
	}

	//Note: No need to return the staging variants of these, as they're only used
	//temporarily by the GraphLoader
	public Map<Concept, MRCMDomain> getMrcmDomainMap() {
		return mrcmDomainMap;
	}

	public void reset() {
		mrcmStagingDomainMap = new HashMap<>();
		mrcmDomainMap = new HashMap<>();

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
				MRCMDomain ar = MRCMDomain.fromRf2(lineItems);

				//Only set the released flag if it's not set already
				if (ar.isReleased() == null) {
					ar.setReleased(isReleased);
				}
				Concept refComp = gl.getConcept(ar.getReferencedComponentId());
				addToMRCMDomainMap(mrcmStagingDomainMap, refComp, ar);

			} else {
				isHeaderLine = false;
			}
		}
	}

	private void addToMRCMDomainMap(Map<Concept, Map<String, MRCMDomain>> mrcmStagingAttribMap, Concept refComp, MRCMDomain ar) {
		//We'll add all entries to a staging structure initially, to allow conflicts to be resolved
		//in a delta.
		Map<String, MRCMDomain> attribRanges = mrcmStagingAttribMap.computeIfAbsent(refComp, k -> new HashMap<>());
		//This will overwrite any existing MRCM row with the same UUID
		//And allow multiple rows for exist for a given referenced component id
		attribRanges.merge(ar.getId(), ar, (existing, value) -> {
			value.setReleased(existing.getReleased());
			return value;
		});
	}

	private boolean finaliseMRCMDomain(
			Map<Concept, Map<String, MRCMDomain>> mrcmStagingDomainMap,
			Map<Concept, MRCMDomain> mrcmDomainMap, List<String> conflictingAttributes) {
		boolean acceptablyFinalised = true;
		mrcmDomainMap.clear();
		for (Concept domain : mrcmStagingDomainMap.keySet()) {
			Map<String, MRCMDomain> conflictingRanges = mrcmStagingDomainMap.get(domain);
			if (conflictingRanges.size() == 1) {
				//No Conflict in this case
				mrcmDomainMap.put(domain, conflictingRanges.values().iterator().next());
			} else {
				//Assuming the conflict is between an International Row and an Extension Row for the same concept
				//but with distinct UUIDs, we'll let the extension row win.
				acceptablyFinalised = acceptablyFinalised && resolveConflicts(domain, conflictingRanges, conflictingAttributes);
			}
		}
		return acceptablyFinalised;
	}

	private boolean resolveConflicts(Concept refComp, Map<String, MRCMDomain> conflictingRanges, List<String> conflictingAttributes) {
		boolean acceptablyFinalised = true;
		MRCMDomain winningAR = pickWinningMRCMDomain(refComp, conflictingRanges, conflictingAttributes);
		if (winningAR == null) {
			//We're OK as long as only 1 of the ranges is active.  Store that one.   Otherwise, add to conflicts list to report
			for (MRCMDomain ar : conflictingRanges.values()) {
				MRCMDomain existing = mrcmDomainMap.get(refComp);
				//We have a problem if the existing one is also active
				//We'll collect all conflicts up and report back on all of them in the calling function
				if (existing != null && existing.isActive() && ar.isActiveSafely()) {
					String detail = " (" + ar.getId() + IN_MODULE + ar.getModuleId() + " vs " + existing.getId() + IN_MODULE + existing.getModuleId() + ")";
					conflictingAttributes.add(refComp + detail);
					acceptablyFinalised = false;
				} else if (existing == null || ar.isActive()) {
					mrcmDomainMap.put(refComp, ar);
				}
			}
		} else {
			mrcmDomainMap.put(refComp, winningAR);
		}
		return acceptablyFinalised;
	}

	private MRCMDomain pickWinningMRCMDomain(Concept refComp, Map<String, MRCMDomain> conflictingRanges, List<String> conflictingAttributes) {
		//Return 1 active row for each of INT and EXT, or null if there's no active row, or multiple active rows
		MRCMDomain intAR = pickActiveMRCMDomain(conflictingRanges, true);
		MRCMDomain extAR = pickActiveMRCMDomain(conflictingRanges, false);
		if (intAR != null && extAR != null) {
			String detail = " (" + intAR.getId() + IN_MODULE + intAR.getModuleId() + " vs " + extAR.getId() + IN_MODULE + extAR.getModuleId() + ")";
			conflictingAttributes.add(refComp + detail);
			return extAR;
		}
		return null;
	}

	private MRCMDomain pickActiveMRCMDomain(Map<String, MRCMDomain> conflictingRanges, boolean isInternational) {
		List<MRCMDomain> activeRanges = conflictingRanges.values().stream()
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
		LOGGER.info("Finalising Domain MRCM from Staging Area");
		List<String> conflictingAttributes = new ArrayList<>();
		boolean allContentAcceptable = finaliseMRCMDomain(mrcmStagingDomainMap, mrcmDomainMap, conflictingAttributes);

		if (!conflictingAttributes.isEmpty()) {
			String msg = "MRCM Domain File conflicts: \n";
			msg += conflictingAttributes.stream().collect(Collectors.joining(",\n"));
			if (allContentAcceptable || !gl.isRunIntegrityChecks()) {
				integrityWarnings.add(msg);
			} else {
				throw new TermServerScriptException(msg);
			}
		}
	}
}

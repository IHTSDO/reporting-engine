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

//id,effectiveTime,active,moduleId,refsetId,referencedComponentId,rangeConstraint,attributeRule,ruleStrengthId,contentTypeId
public class MRCMAttributeRangeManager implements ScriptConstants  {

	private static final String IN_MODULE = " in module ";
	private static final Logger LOGGER = LoggerFactory.getLogger(MRCMAttributeRangeManager.class);

	private GraphLoader gl;

	private Map<Concept, Map<String, MRCMAttributeRange>> mrcmStagingAttributeRangeMapPreCoord = new HashMap<>();
	private Map<Concept, Map<String, MRCMAttributeRange>> mrcmStagingAttributeRangeMapPostCoord = new HashMap<>();
	private Map<Concept, Map<String, MRCMAttributeRange>> mrcmStagingAttributeRangeMapAll = new HashMap<>();
	private Map<Concept, Map<String, MRCMAttributeRange>> mrcmStagingAttributeRangeMapNewPreCoord = new HashMap<>();

	private Map<Concept, MRCMAttributeRange> mrcmAttributeRangeMapPreCoord = new HashMap<>();
	private Map<Concept, MRCMAttributeRange> mrcmAttributeRangeMapPostCoord = new HashMap<>();
	private Map<Concept, MRCMAttributeRange> mrcmAttributeRangeMapAll = new HashMap<>();
	private Map<Concept, MRCMAttributeRange> mrcmAttributeRangeMapNewPreCoord = new HashMap<>();

	private static String translateContentType(String contentTypeId) throws TermServerScriptException {
		switch (contentTypeId) {
			case SCTID_PRE_COORDINATED_CONTENT : return " Pre-coordinated content ";
			case SCTID_POST_COORDINATED_CONTENT : return " Post-coordinated content ";
			case SCTID_ALL_CONTENT : return " All SNOMED content ";
			case SCTID_NEW_PRE_COORDINATED_CONTENT : return " New Pre-coordinated content ";
			default : throw new TermServerScriptException ("Unrecognised MRCM content type encountered: " + contentTypeId);
		}
	}

	public MRCMAttributeRangeManager(GraphLoader graphLoader) {
		this.gl = graphLoader;
	}

	//Note: No need to return the staging variants of these, as they're only used
	//temporarily by the GraphLoader
	public Map<Concept, MRCMAttributeRange> getMrcmAttributeRangeMapPreCoord() {
		return mrcmAttributeRangeMapPreCoord;
	}

	public Map<Concept, MRCMAttributeRange> getMrcmAttributeRangeMapPostCoord() {
		return mrcmAttributeRangeMapPostCoord;
	}

	public Map<Concept, MRCMAttributeRange> getMrcmAttributeRangeMapAll() {
		return mrcmAttributeRangeMapAll;
	}

	public Map<Concept, MRCMAttributeRange> getMrcmAttributeRangeMapNewPreCoord() {
		return mrcmAttributeRangeMapNewPreCoord;
	}

	public void reset() {
		mrcmStagingAttributeRangeMapPreCoord = new HashMap<>();
		mrcmStagingAttributeRangeMapPostCoord = new HashMap<>();
		mrcmStagingAttributeRangeMapAll = new HashMap<>();
		mrcmStagingAttributeRangeMapNewPreCoord = new HashMap<>();

		mrcmAttributeRangeMapPreCoord = new HashMap<>();
		mrcmAttributeRangeMapPostCoord = new HashMap<>();
		mrcmAttributeRangeMapAll = new HashMap<>();
		mrcmAttributeRangeMapNewPreCoord = new HashMap<>();
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
				MRCMAttributeRange ar = MRCMAttributeRange.fromRf2(lineItems);

				//Only set the released flag if it's not set already
				if (ar.isReleased() == null) {
					ar.setReleased(isReleased);
				}
				Concept refComp = gl.getConcept(ar.getReferencedComponentId());
				String contentTypeId = lineItems[MRCM_ATTRIB_RANGE_CONTENT_TYPE];

				switch(contentTypeId) {
					case SCTID_PRE_COORDINATED_CONTENT : addToMRCMAttributeMap(mrcmStagingAttributeRangeMapPreCoord, refComp, ar);
						break;
					case SCTID_POST_COORDINATED_CONTENT : addToMRCMAttributeMap(mrcmStagingAttributeRangeMapPostCoord, refComp, ar);
						break;
					case SCTID_ALL_CONTENT : addToMRCMAttributeMap(mrcmStagingAttributeRangeMapAll, refComp, ar);
						break;
					case SCTID_NEW_PRE_COORDINATED_CONTENT : addToMRCMAttributeMap(mrcmStagingAttributeRangeMapNewPreCoord, refComp, ar);
						break;
					default : throw new TermServerScriptException("Unrecognised content type in MRCM Attribute Range File: " + contentTypeId);
				}
			} else {
				isHeaderLine = false;
			}
		}
	}

	private void addToMRCMAttributeMap(Map<Concept, Map<String, MRCMAttributeRange>> mrcmStagingAttribMap, Concept refComp, MRCMAttributeRange ar) {
		//We'll add all entries to a staging structure initially, to allow conflicts to be resolved
		//in a delta.
		Map<String, MRCMAttributeRange> attribRanges = mrcmStagingAttribMap.computeIfAbsent(refComp, k -> new HashMap<>());
		//This will overwrite any existing MRCM row with the same UUID
		//And allow multiple rows for exist for a given referenced component id
		attribRanges.merge(ar.getId(), ar, (existing, value) -> {
			value.setReleased(existing.isReleased());
			return value;
		});
	}

	private boolean finaliseMRCMAttributeRange(
			Map<Concept, Map<String, MRCMAttributeRange>> mrcmStagingAttributeRangeMap,
			Map<Concept, MRCMAttributeRange> mrcmAttributeRangeMap, List<String> conflictingAttributes) throws TermServerScriptException {
		boolean acceptablyFinalised = true;
		mrcmAttributeRangeMap.clear();
		for (Concept refComp : mrcmStagingAttributeRangeMap.keySet()) {
			Map<String, MRCMAttributeRange> conflictingRanges = mrcmStagingAttributeRangeMap.get(refComp);
			acceptablyFinalised = finaliseConflictingRanges(conflictingRanges, refComp, mrcmAttributeRangeMap, conflictingAttributes) && acceptablyFinalised;
		}
		return acceptablyFinalised;
	}

	private boolean finaliseConflictingRanges(Map<String, MRCMAttributeRange> conflictingRanges, Concept refComp, Map<Concept, MRCMAttributeRange> mrcmAttributeRangeMap, List<String> conflictingAttributes) throws TermServerScriptException {
		boolean acceptablyFinalised = true;
		if (conflictingRanges.size() == 1) {
			//No Conflict in this case
			mrcmAttributeRangeMap.put(refComp, conflictingRanges.values().iterator().next());
		} else {
			//Assuming the conflict is between an International Row and an Extension Row for the same concept
			//but with distinct UUIDs, we'll let the extension row win.
			MRCMAttributeRange winningAR = pickWinningMRCMAttributeRange(refComp, conflictingRanges, conflictingAttributes);
			if (winningAR == null) {
				//We're OK as long as only 1 of the ranges is active.  Store that one.   Otherwise, add to conflicts list to report
				for (MRCMAttributeRange ar : conflictingRanges.values()) {
					MRCMAttributeRange existing = mrcmAttributeRangeMap.get(refComp);
					//We have a problem if the existing one is also active
					//We'll collect all conflicts up and report back on all of them in the calling function
					if (existing != null && existing.isActive() && ar.isActiveSafely()) {
						String contentType = translateContentType(ar.getContentTypeId());
						String detail = " (" + ar.getId() + IN_MODULE + ar.getModuleId() + " vs " + existing.getId() + IN_MODULE + existing.getModuleId() + ")";
						conflictingAttributes.add(contentType + ": " + refComp + detail);
						acceptablyFinalised = false;
					} else if (existing == null || ar.isActive()) {
						mrcmAttributeRangeMap.put(refComp, ar);
					}
				}
			} else {
				mrcmAttributeRangeMap.put(refComp, winningAR);
			}
		}
		return acceptablyFinalised;
	}

	private MRCMAttributeRange pickWinningMRCMAttributeRange(Concept refComp, Map<String, MRCMAttributeRange> conflictingRanges, List<String> conflictingAttributes) throws TermServerScriptException {
		//Return 1 active row for each of INT and EXT, or null if there's no active row, or multiple active rows
		MRCMAttributeRange intAR = pickActiveMRCMAttributeRange(conflictingRanges, true);
		MRCMAttributeRange extAR = pickActiveMRCMAttributeRange(conflictingRanges, false);
		if (intAR != null && extAR != null) {
			String contentType = translateContentType(intAR.getContentTypeId());
			String detail = " (" + intAR.getId() + IN_MODULE + intAR.getModuleId() + " vs " + extAR.getId() + IN_MODULE + extAR.getModuleId() + ")";
			conflictingAttributes.add(contentType + ": " + refComp + detail);
			return extAR;
		}
		return null;
	}

	private MRCMAttributeRange pickActiveMRCMAttributeRange(Map<String, MRCMAttributeRange> conflictingRanges, boolean isInternational) {
		List<MRCMAttributeRange> activeRanges = conflictingRanges.values().stream()
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
		LOGGER.info("Finalising Attribute Range MRCM from Staging Area");
		List<String> conflictingAttributes = new ArrayList<>();
		boolean preCoordAcceptable= finaliseMRCMAttributeRange(mrcmStagingAttributeRangeMapPreCoord, mrcmAttributeRangeMapPreCoord, conflictingAttributes);
		boolean postCoordAcceptable = finaliseMRCMAttributeRange(mrcmStagingAttributeRangeMapPostCoord, mrcmAttributeRangeMapPostCoord, conflictingAttributes);
		boolean allCoordAcceptable = finaliseMRCMAttributeRange(mrcmStagingAttributeRangeMapAll, mrcmAttributeRangeMapAll, conflictingAttributes);
		boolean newCoordAcceptable = finaliseMRCMAttributeRange(mrcmStagingAttributeRangeMapNewPreCoord, mrcmAttributeRangeMapNewPreCoord, conflictingAttributes);
		boolean allContentAcceptable = preCoordAcceptable && postCoordAcceptable && allCoordAcceptable && newCoordAcceptable;

		if (!conflictingAttributes.isEmpty()) {
			String msg = "MRCM Attribute Range File conflicts: \n";
			msg += conflictingAttributes.stream().collect(Collectors.joining(",\n"));
			if (allContentAcceptable || !gl.isRunIntegrityChecks()) {
				integrityWarnings.add(msg);
			} else {
				throw new TermServerScriptException(msg);
			}
		}
	}
}

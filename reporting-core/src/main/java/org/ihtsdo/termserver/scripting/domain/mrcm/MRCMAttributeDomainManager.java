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

//id,effectiveTime,active,moduleId,refsetId,referencedComponentId,domainId,grouped,attributeCardinality,attributeInGroupCardinality,ruleStrengthId,contentTypeId
public class MRCMAttributeDomainManager implements ScriptConstants  {

	private static final String IN_MODULE = " in module ";
	private static final Logger LOGGER = LoggerFactory.getLogger(MRCMAttributeDomainManager.class);

	private GraphLoader gl;

	private Map<Concept, Map<Concept, Map<UUID, MRCMAttributeDomain>>> mrcmStagingAttributeDomainMapPreCoord = new HashMap<>();
	private Map<Concept, Map<Concept, Map<UUID, MRCMAttributeDomain>>> mrcmStagingAttributeDomainMapPostCoord = new HashMap<>();
	private Map<Concept, Map<Concept, Map<UUID, MRCMAttributeDomain>>> mrcmStagingAttributeDomainMapAll = new HashMap<>();
	private Map<Concept, Map<Concept, Map<UUID, MRCMAttributeDomain>>> mrcmStagingAttributeDomainMapNewPreCoord = new HashMap<>();

	private Map<Concept, Map<Concept, MRCMAttributeDomain>> mrcmAttributeDomainMapPreCoord = new HashMap<>();
	private Map<Concept, Map<Concept, MRCMAttributeDomain>> mrcmAttributeDomainMapPostCoord = new HashMap<>();
	private Map<Concept, Map<Concept, MRCMAttributeDomain>> mrcmAttributeDomainMapAll = new HashMap<>();
	private Map<Concept, Map<Concept, MRCMAttributeDomain>> mrcmAttributeDomainMapNewPreCoord = new HashMap<>();

	private static String translateContentType(String contentTypeId) throws TermServerScriptException {
		switch (contentTypeId) {
			case SCTID_PRE_COORDINATED_CONTENT : return " Pre-coordinated content ";
			case SCTID_POST_COORDINATED_CONTENT : return " Post-coordinated content ";
			case SCTID_ALL_CONTENT : return " All SNOMED content ";
			case SCTID_NEW_PRE_COORDINATED_CONTENT : return " New Pre-coordinated content ";
			default : throw new TermServerScriptException ("Unrecognised MRCM content type encountered: " + contentTypeId);
		}
	}

	public MRCMAttributeDomainManager(GraphLoader graphLoader) {
		this.gl = graphLoader;
	}

	//Note: No need to return the staging variants of these, as they're only used
	//temporarily by the GraphLoader
	public Map<Concept, Map<Concept, MRCMAttributeDomain>> getMrcmAttributeDomainMapPreCoord() {
		return mrcmAttributeDomainMapPreCoord;
	}

	public Map<Concept, Map<Concept, MRCMAttributeDomain>> getMrcmAttributeDomainMapPostCoord() {
		return mrcmAttributeDomainMapPostCoord;
	}

	public Map<Concept, Map<Concept, MRCMAttributeDomain>> getMrcmAttributeDomainMapAll() {
		return mrcmAttributeDomainMapAll;
	}

	public Map<Concept, Map<Concept, MRCMAttributeDomain>> getMrcmAttributeDomainMapNewPreCoord() {
		return mrcmAttributeDomainMapNewPreCoord;
	}

	public void reset() {
		mrcmStagingAttributeDomainMapPreCoord = new HashMap<>();
		mrcmStagingAttributeDomainMapPostCoord = new HashMap<>();
		mrcmStagingAttributeDomainMapAll = new HashMap<>();
		mrcmStagingAttributeDomainMapNewPreCoord = new HashMap<>();

		mrcmAttributeDomainMapPreCoord = new HashMap<>();
		mrcmAttributeDomainMapPostCoord = new HashMap<>();
		mrcmAttributeDomainMapAll = new HashMap<>();
		mrcmAttributeDomainMapNewPreCoord = new HashMap<>();
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
				MRCMAttributeDomain ad = MRCMAttributeDomain.fromRf2(lineItems);

				//Only set the released flag if it's not set already
				if (ad.isReleased() == null) {
					ad.setReleased(isReleased);
				}
				Concept refComp = gl.getConcept(ad.getReferencedComponentId());
				String contentTypeId = lineItems[MRCM_ATTRIB_DOMAIN_CONTENT_TYPE];

				switch(contentTypeId) {
					case SCTID_PRE_COORDINATED_CONTENT : addToMRCMAttributeDomainMap(mrcmStagingAttributeDomainMapPreCoord, refComp, ad);
						break;
					case SCTID_POST_COORDINATED_CONTENT : addToMRCMAttributeDomainMap(mrcmStagingAttributeDomainMapPostCoord, refComp, ad);
						break;
					case SCTID_ALL_CONTENT : addToMRCMAttributeDomainMap(mrcmStagingAttributeDomainMapAll, refComp, ad);
						break;
					case SCTID_NEW_PRE_COORDINATED_CONTENT : addToMRCMAttributeDomainMap(mrcmStagingAttributeDomainMapNewPreCoord, refComp, ad);
						break;
					default : throw new TermServerScriptException("Unrecognised content type in MRCM Attribute Domain File: " + contentTypeId);
				}
			} else {
				isHeaderLine = false;
			}
		}
	}

	private void addToMRCMAttributeDomainMap(Map<Concept, Map<Concept, Map<UUID, MRCMAttributeDomain>>> mrcmStagingAttribDomainMap, Concept refComp, MRCMAttributeDomain ar) throws TermServerScriptException {
		//We'll add all entries to a staging structure initially, to allow conflicts to be resolved
		//in a delta.
		Map<Concept, Map<UUID, MRCMAttributeDomain>> attribDomains = mrcmStagingAttribDomainMap.computeIfAbsent(refComp, k -> new HashMap<>());

		//What rules do we have for this domain?
		Concept domain = gl.getConcept(ar.getDomainId());
		Map<UUID, MRCMAttributeDomain> domainRules = attribDomains.computeIfAbsent(domain, k -> new HashMap<>());

		//This will overwrite any existing MRCM row with the same UUID
		//And allow multiple rows for exist for a given attribute & domain
		UUID uuid = UUID.fromString(ar.getId());
		domainRules.merge(uuid, ar, (existing, value) -> {
			value.setReleased(existing.isReleased());
			return value;
		});
	}

	private boolean finaliseMRCMAttributeDomain(
			Map<Concept, Map<Concept, Map<UUID, MRCMAttributeDomain>>> mrcmStagingAttributeDomainMap,
			Map<Concept, Map<Concept, MRCMAttributeDomain>> mrcmAttributeDomainMap, List<String> unresolvableConflicts) throws TermServerScriptException {
		boolean acceptablyFinalised = true;
		mrcmAttributeDomainMap.clear();
		for (Concept attribute : mrcmStagingAttributeDomainMap.keySet()) {
			Map<Concept, Map<UUID, MRCMAttributeDomain>> attributeDomainsStaging = mrcmStagingAttributeDomainMap.get(attribute);
			Map<Concept, MRCMAttributeDomain> attributeDomainsFinal = mrcmAttributeDomainMap.computeIfAbsent(attribute, k -> new HashMap<>());

			for (Map.Entry<Concept, Map<UUID, MRCMAttributeDomain>> entry: attributeDomainsStaging.entrySet()) {
				Concept domain = entry.getKey();
				Map<UUID, MRCMAttributeDomain> conflictingRules = entry.getValue();
				acceptablyFinalised = finaliseConflictingRules(attribute, domain, conflictingRules, attributeDomainsFinal, unresolvableConflicts) && acceptablyFinalised;
			}
		}
		return acceptablyFinalised;
	}

	private boolean finaliseConflictingRules(Concept attribute, Concept domain, Map<UUID, MRCMAttributeDomain> conflictingRules, Map<Concept, MRCMAttributeDomain> attributeDomainsFinal, List<String> unresolvableConflicts) throws TermServerScriptException {
		boolean acceptablyFinalised = true;
		if (conflictingRules.size() == 1) {
			//No Conflict in this case
			attributeDomainsFinal.put(domain, conflictingRules.values().iterator().next());
		} else {
			//Assuming the conflict is between an International Row and an Extension Row for the same concept
			//but with distinct UUIDs, we'll let the extension row win.
			MRCMAttributeDomain winningAD = pickWinningMRCMAttributeDomain(domain, conflictingRules, unresolvableConflicts);
			if (winningAD == null) {
				//We're OK as long as only 1 of the ranges is active.  Store that one.   Otherwise, add to conflicts list to report
				for (MRCMAttributeDomain ad : conflictingRules.values()) {
					MRCMAttributeDomain existing = attributeDomainsFinal.get(domain);
					//We have a problem if the existing one is also active
					//We'll collect all conflicts up and report back on all of them in the calling function
					if (existing != null && existing.isActive() && ad.isActiveSafely()) {
						String contentType = translateContentType(ad.getContentTypeId());
						String detail = " (" + ad.getId() + IN_MODULE + ad.getModuleId() + " vs " + existing.getId() + IN_MODULE + existing.getModuleId() + ")";
						unresolvableConflicts.add(contentType + ": " + attribute + detail);
						acceptablyFinalised = false;
					} else if (existing == null || ad.isActive()) {
						attributeDomainsFinal.put(domain, ad);
					}
				}
			} else {
				attributeDomainsFinal.put(domain, winningAD);
			}
		}
		return acceptablyFinalised;
	}

	private MRCMAttributeDomain pickWinningMRCMAttributeDomain(Concept domain, Map<UUID, MRCMAttributeDomain> conflictingRules, List<String> unresolvableConflicts) throws TermServerScriptException {
		//Return 1 active row for each of INT and EXT, or null if there's no active row, or multiple active rows
		MRCMAttributeDomain intAR = pickActiveMRCMAttributeDomain(conflictingRules, true);
		MRCMAttributeDomain extAR = pickActiveMRCMAttributeDomain(conflictingRules, false);
		if (intAR != null && extAR != null) {
			String contentType = translateContentType(intAR.getContentTypeId());
			String detail = " (" + intAR.getId() + IN_MODULE + intAR.getModuleId() + " vs " + extAR.getId() + IN_MODULE + extAR.getModuleId() + ")";
			unresolvableConflicts.add(contentType + ": " + domain + detail);
			return extAR;
		}
		return null;
	}

	private MRCMAttributeDomain pickActiveMRCMAttributeDomain(Map<UUID, MRCMAttributeDomain> conflictingRules, boolean isInternational) {
		List<MRCMAttributeDomain> activeRules = conflictingRules.values().stream()
				.filter(Component::isActive)
				.filter(ar -> isInternational == SnomedUtils.isInternational(ar))
				.toList();
		if (activeRules.size() == 1) {
			return activeRules.get(0);
		}
		return null;
	}

	public void finaliseFromStagingArea(List<String> integrityWarnings) throws TermServerScriptException {
		//Only current work required here is to verify that the Delta import has resolved
		//any apparent conflicts in the MRCM.  Convert the staging collection (which will hold duplicates)
		//into the non-staging collection only if all duplications have been resolved.
		LOGGER.info("Finalising Attribute Range MRCM from Staging Area");
		List<String> conflictingAttributes = new ArrayList<>();
		boolean preCoordAcceptable= finaliseMRCMAttributeDomain(mrcmStagingAttributeDomainMapPreCoord, mrcmAttributeDomainMapPreCoord, conflictingAttributes);
		boolean postCoordAcceptable = finaliseMRCMAttributeDomain(mrcmStagingAttributeDomainMapPostCoord, mrcmAttributeDomainMapPostCoord, conflictingAttributes);
		boolean allCoordAcceptable = finaliseMRCMAttributeDomain(mrcmStagingAttributeDomainMapAll, mrcmAttributeDomainMapAll, conflictingAttributes);
		boolean newCoordAcceptable = finaliseMRCMAttributeDomain(mrcmStagingAttributeDomainMapNewPreCoord, mrcmAttributeDomainMapNewPreCoord, conflictingAttributes);
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

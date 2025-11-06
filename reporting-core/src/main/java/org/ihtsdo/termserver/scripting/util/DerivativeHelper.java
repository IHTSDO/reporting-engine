package org.ihtsdo.termserver.scripting.util;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.authoringservices.AuthoringServicesClient;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.EclCache;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.client.TermServerClient;
import org.ihtsdo.termserver.scripting.domain.CodeSystem;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DerivativeHelper implements ScriptConstants {
	private static final String DERIVATIVE_LOCATIONS = "resources/derivative-locations.tsv";
	private static final Logger LOGGER = LoggerFactory.getLogger(DerivativeHelper.class);

	private boolean initialised = false;
	private final Map<String, TermServerClient> serverTsClientMap = new HashMap<>();
	private final Map<String, AuthoringServicesClient> serverAsClientMap = new HashMap<>();
	private final Map<String, DerivativeLocation> derivativeLocationMap = new HashMap<>();
	private final Map<String, String> branchForProjectOrCodeSystem = new HashMap<>();
	private final TermServerScript script;

	public DerivativeHelper(TermServerScript script) {
		this.script = script;
	}

	public void initialise() throws TermServerScriptException {
		importDerivativeLocations();
		initialised = true;
	}

	public boolean isDerivativeRefset(String refsetId) {
		ensureInitialised();
		return derivativeLocationMap.containsKey(refsetId);
	}

	private void ensureInitialised() {
		if (!initialised) {
			throw new IllegalStateException("DoseFormHelper has not been initialised");
		}
	}

	private void importDerivativeLocations() throws TermServerScriptException {
		LOGGER.debug("Loading {}", DERIVATIVE_LOCATIONS);

		try (BufferedReader br = new BufferedReader(new FileReader(DERIVATIVE_LOCATIONS))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] columns = line.split(TAB, -1);
				//Is this the header row?
				if (columns[0].equals("SCTID")) {
					continue;
				}

				if (columns.length >= 5) {
					DerivativeLocation location = new DerivativeLocation();
					location.sctId = columns[0];
					location.pt = columns[1];
					location.server = columns[2];
					location.codeSystem = hasDataOrNull(columns[3]);
					location.project = hasDataOrNull(columns[4]);
					derivativeLocationMap.put(location.sctId, location);
				} else {
					LOGGER.warn("Check {} for correct format (5 columns): {}", DERIVATIVE_LOCATIONS, line);
				}
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to read " + DERIVATIVE_LOCATIONS, e);
		}
		LOGGER.info("Configured the location of {} derivatives", derivativeLocationMap.size());
	}

	public List<Concept> getDerivativeRefsetConcepts() {
		List<Concept> derivativeRefsetConcepts = new ArrayList<>();

		for (DerivativeLocation location : derivativeLocationMap.values()) {
			Concept refset = new Concept(location.sctId);
			refset.setPreferredSynonym(location.pt);
			derivativeRefsetConcepts.add(refset);
		}
		return derivativeRefsetConcepts;
	}

	public EclCache getEclCacheForDerivativeRefset(String refsetId) {
		String authenticatedCookie = script.getAuthenticatedCookie();
		GraphLoader gl = script.getGraphLoader();

		DerivativeLocation location = derivativeLocationMap.get(refsetId);
		TermServerClient tsClient = serverTsClientMap.computeIfAbsent(location.server, k -> new TermServerClient(k, authenticatedCookie));
		AuthoringServicesClient asClient = serverAsClientMap.computeIfAbsent(location.server, k -> new AuthoringServicesClient(getAsServerURL(k), authenticatedCookie));
		String branch = branchForProjectOrCodeSystem.computeIfAbsent(location.getProjectOrCodeSystem(), k -> getBranchForDerivativeLocation(tsClient, asClient, location));

		return EclCache.getCache(branch, tsClient, gl, false);
	}

	private String getBranchForDerivativeLocation(TermServerClient tsClient, AuthoringServicesClient asClient, DerivativeLocation location) {
		String errorMessage = "Unable to recover " ;
		try {
			//Look up a project if we have that, otherwise use the code system
			if (location.project != null) {
				errorMessage += "project " + location.project + " from " + location.server;
				//For projects, we need to work with authoring-services.  Allow first time release so we don't check Metadata
				return asClient.getProject(location.project, true).getBranchPath();
			}
			//What is the current branch for this CodeSystem?
			errorMessage += "code system " + location.codeSystem + " from " + location.server;
			CodeSystem cs = tsClient.getCodeSystem(location.codeSystem);
			if (cs != null && cs.getLatestVersion() != null) {
				return cs.getLatestVersion().getBranchPath();
			}
		} catch (RestClientException | TermServerScriptException e) {
			errorMessage += " due to : " + e.getMessage();
		}
		throw new IllegalStateException(errorMessage);
	}

	private String hasDataOrNull(String str) {
		return StringUtils.isEmpty(str)? null : str;
	}

	private String getAsServerURL(String serverUrl) {
		return serverUrl.replace("snowstorm/snomed-ct", "");
	}

	private class DerivativeLocation {
		String sctId;
		String pt;
		String server;
		String codeSystem;
		String project;

		public String getProjectOrCodeSystem() {
			return project != null ? project : codeSystem;
		}
	}
}
package org.ihtsdo.termserver.scripting.util;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.ReportingServerClient;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.snomed.otf.scheduler.domain.JobRun;
import org.snomed.otf.scheduler.domain.WhiteListedConcept;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;

/**
 * Holds the concepts whitelisted for a report, whether supplied with the JobRun
 * (running in the reporting engine) or recovered from the Reporting Service (running locally)
 */
public class WhitelistHelper {

	private static final Logger LOGGER = LoggerFactory.getLogger(WhitelistHelper.class);

	public static final String DEFAULT_CODE_SYSTEM = "SNOMEDCT";

	private Set<WhiteListedConcept> whiteList = new HashSet<>();
	private Set<String> whiteListedConceptIds = new HashSet<>();

	public void populateFromJobRun(JobRun jobRun) {
		if (jobRun.getWhiteList() != null) {
			populate(jobRun.getWhiteList());
		}
	}

	public void populateViaAPI(String serverUrl, String cookie, String reportName, String codeSystemShortname) throws TermServerScriptException {
		try {
			populate(new ReportingServerClient(serverUrl, cookie).getWhiteListForReport(reportName, codeSystemShortname));
			LOGGER.info("Recovered {} concepts whitelisted for {} in {}", whiteList.size(), reportName, codeSystemShortname);
		} catch (RestClientException e) {
			throw new TermServerScriptException("Unable to recover whitelist for " + reportName + " in " + codeSystemShortname, e);
		}
	}

	private void populate(Set<WhiteListedConcept> concepts) {
		whiteList = concepts;
		whiteListedConceptIds = concepts.stream()
				.map(w -> SnomedUtils.makeMachineReadable(w.getSctId()))
				.collect(Collectors.toSet());
	}

	public boolean isWhiteListed(Concept c) {
		return c != null && isWhiteListed(c.getId());
	}

	public boolean isWhiteListed(String sctId) {
		return whiteListedConceptIds.contains(sctId);
	}

	public List<WhiteListedConcept> getWhiteListSorted() {
		return whiteList.stream()
				.sorted(Comparator.comparing(WhiteListedConcept::getSctId))
				.toList();
	}

	/**
	 * @return the code system for a branch path, eg MAIN/SNOMEDCT-SE/PROJ gives SNOMEDCT-SE
	 */
	public static String deriveCodeSystem(String branchPath) {
		if (branchPath != null) {
			for (String part : branchPath.split("/")) {
				if (part.startsWith(DEFAULT_CODE_SYSTEM + "-")) {
					return part;
				}
			}
		}
		return DEFAULT_CODE_SYSTEM;
	}
}

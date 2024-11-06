package org.ihtsdo.termserver.scripting.pipeline.nuva;

import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class NuvaVaccine extends NuvaConcept implements Comparable<NuvaVaccine>, NuvaConstants {
	private static final Logger LOGGER = LoggerFactory.getLogger(NuvaVaccine.class);

	private final Map<String, NuvaValence> valenceMap = new HashMap<>();
	private final List<String> valenceRefs = new ArrayList<>();
	private final List<String> matches = new ArrayList<>();
	private final List<String> snomedCodes = new ArrayList<>();

	protected NuvaVaccine(String externalIdentifier) {
		super(externalIdentifier);
	}

	public static NuvaVaccine fromResource(String nuvaId, StmtIterator stmtIterator) {
		NuvaVaccine vaccine = new NuvaVaccine(nuvaId);
		if (nuvaId.equals("VAC1061")) {
			LOGGER.debug("here");
		}
		while (stmtIterator.hasNext()) {
			vaccine.fromStatement(stmtIterator.next());
		}
		return vaccine;
	}

	private void fromStatement(Statement stmt) {
		if (!isCommonPredicate(stmt)) {
			if (isPredicate(stmt, NuvaOntologyLoader.NuvaUri.VALENCE)) {
				valenceRefs.add(getObject(stmt));
			} else if (isPredicate(stmt, NuvaOntologyLoader.NuvaUri.MATCH)) {
				String match = getObject(stmt);
				if (match.startsWith(SNOMED_LABEL)) {
					snomedCodes.add(match.substring(SNOMED_LABEL.length()));
				} else {
					matches.add(match);
				}
			}
		}
	}

	@Override
	public int compareTo(@NotNull NuvaVaccine o) {
		return o.externalIdentifier.compareTo(this.externalIdentifier);
	}

	public List<String> getSnomedCodes() {
		return snomedCodes;
	}

	public List<String> getMatches() {
		return matches;
	}

	public List<String> getValenceRefs() {
		return valenceRefs;
	}

	public void addValence(NuvaValence valence) {
		valenceMap.put(valence.getExternalIdentifier(), valence);
	}

	public Collection<NuvaValence> getValences() {
		return valenceMap.values();
	}
}
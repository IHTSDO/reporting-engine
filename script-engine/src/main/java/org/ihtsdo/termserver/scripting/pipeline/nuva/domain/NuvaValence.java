package org.ihtsdo.termserver.scripting.pipeline.nuva.domain;

import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.ihtsdo.termserver.scripting.pipeline.nuva.NuvaOntologyLoader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class NuvaValence extends NuvaConcept implements Comparable<NuvaValence> {

	private static final Logger LOGGER = LoggerFactory.getLogger(NuvaValence.class);

	protected NuvaDisease disease;
	private final List<String> prevents = new ArrayList<>();
	private final List<String> parentValenceIds = new ArrayList<>();
	protected List<NuvaValence> parentValences = new ArrayList<>();

	protected NuvaValence(String externalIdentifier) {
		super(externalIdentifier);
	}

	public List<String> getParentValenceIds() {
		return parentValenceIds;
	}

	public NuvaDisease getDisease() {
		return disease;
	}

	public static NuvaValence fromResource(String nuvaId, StmtIterator stmtIterator) {
		NuvaValence valence = new NuvaValence(nuvaId);
		while (stmtIterator.hasNext()) {
			valence.fromStatement(stmtIterator.next());
		}
		return valence;
		
	}

	private void fromStatement(Statement stmt) {
		if (!isCommonPredicate(stmt)) {
			if (isPredicate(stmt, NuvaOntologyLoader.NuvaUri.SUBCLASSOF)) {
				String parentValence = getObject(stmt);
				//Record this Valence if it's not "Valence" which is the top level object
				if (!parentValence.equals(NuvaOntologyLoader.NuvaClass.VALENCE.value)) {
					parentValenceIds.add(getObject(stmt));
				}
			} else if (isPredicate(stmt, NuvaOntologyLoader.NuvaUri.CONTAINED_IN_VACCINE)) {
				//The Vaccines list their valences, so we don't need to populate this twice
			} else if (isPredicate(stmt, NuvaOntologyLoader.NuvaUri.PREVENTS)) {
				prevents.add(getObject(stmt));
			} else {
				LOGGER.warn("Unhandled predicate: {}", stmt.getPredicate());
			}
		}
	}

	@Override
	public String getLongDisplayName() {
		return getEnLabel();
	}

	@Override
	public int compareTo(@NotNull NuvaValence o) {
		return o.externalIdentifier.compareTo(this.externalIdentifier);
	}

	public List<String> getPrevents() {
		return prevents;
	}

	public void setDisease(NuvaDisease disease) {
		if (this.disease != null) {
			throw new IllegalStateException("Disease already set: " + this.disease + ". Asked to set to: " + disease);
		}
		this.disease = disease;
	}

	public void addParentValence(NuvaValence parentValence) {
		parentValences.add(parentValence);
	}

	public List<NuvaValence> getParentValences() {
		return parentValences;
	}
}

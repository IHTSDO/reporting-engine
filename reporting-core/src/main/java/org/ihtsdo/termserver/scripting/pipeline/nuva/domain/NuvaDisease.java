package org.ihtsdo.termserver.scripting.pipeline.nuva.domain;

import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.jetbrains.annotations.NotNull;

public class NuvaDisease extends NuvaConcept implements Comparable<NuvaDisease> {

	protected NuvaDisease(String externalIdentifier) {
		super(externalIdentifier);
	}

	public static NuvaDisease fromResource(String nuvaId, StmtIterator stmtIterator) {
		NuvaDisease disease = new NuvaDisease(nuvaId);
		while (stmtIterator.hasNext()) {
			disease.fromStatement(stmtIterator.next());
		}
		return disease;
	}

	private void fromStatement(Statement stmt) {
		isCommonPredicate(stmt);
	}

	@Override
	public int compareTo(@NotNull NuvaDisease o) {
		return o.externalIdentifier.compareTo(this.externalIdentifier);
	}

}

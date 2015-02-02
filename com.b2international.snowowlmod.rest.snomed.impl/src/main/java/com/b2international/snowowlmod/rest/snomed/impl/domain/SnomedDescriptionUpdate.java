/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowlmod.rest.snomed.impl.domain;

import java.util.Map;

import com.b2international.snowowl.rest.snomed.domain.Acceptability;
import com.b2international.snowowl.rest.snomed.domain.CaseSignificance;
import com.b2international.snowowl.rest.snomed.domain.ISnomedDescriptionUpdate;

/**
 * @author apeteri
 */
public class SnomedDescriptionUpdate extends AbstractSnomedComponentUpdate implements ISnomedDescriptionUpdate {

	private CaseSignificance caseSignificance;
	private Map<String, Acceptability> acceptability;

	@Override
	public CaseSignificance getCaseSignificance() {
		return caseSignificance;
	}

	@Override
	public Map<String, Acceptability> getAcceptability() {
		return acceptability;
	}

	public void setCaseSignificance(final CaseSignificance caseSignificance) {
		this.caseSignificance = caseSignificance;
	}

	public void setAcceptability(final Map<String, Acceptability> acceptability) {
		this.acceptability = acceptability;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SnomedDescriptionUpdate [getModuleId()=");
		builder.append(getModuleId());
		builder.append(", getCaseSignificance()=");
		builder.append(getCaseSignificance());
		builder.append(", getAcceptability()=");
		builder.append(getAcceptability());
		builder.append(", isActive()=");
		builder.append(isActive());
		builder.append("]");
		return builder.toString();
	}
}

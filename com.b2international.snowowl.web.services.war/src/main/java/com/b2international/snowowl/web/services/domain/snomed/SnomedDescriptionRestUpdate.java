/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain.snomed;

import java.util.Map;

import com.b2international.snowowl.rest.snomed.domain.Acceptability;
import com.b2international.snowowl.rest.snomed.domain.CaseSignificance;
import com.b2international.snowowl.rest.snomed.impl.domain.SnomedDescriptionUpdate;

/**
 * @author apeteri
 * @since 1.0
 */
public class SnomedDescriptionRestUpdate extends AbstractSnomedComponentRestUpdate<SnomedDescriptionUpdate> {

	private CaseSignificance caseSignificance;
	private Map<String, Acceptability> acceptability;

	/**
	 * @return
	 */
	public CaseSignificance getCaseSignificance() {
		return caseSignificance;
	}

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
	protected SnomedDescriptionUpdate createComponentUpdate() {
		return new SnomedDescriptionUpdate();
	}

	/**
	 * @return
	 */
	@Override
	public SnomedDescriptionUpdate toComponentUpdate() {
		final SnomedDescriptionUpdate result = super.toComponentUpdate();
		result.setCaseSignificance(getCaseSignificance());
		result.setAcceptability(getAcceptability());
		return result;
	}
}

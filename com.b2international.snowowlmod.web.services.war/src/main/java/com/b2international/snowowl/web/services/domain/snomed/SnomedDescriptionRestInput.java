/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain.snomed;

import static com.b2international.snowowl.rest.domain.ComponentCategory.DESCRIPTION;

import java.util.Map;

import com.b2international.snowowl.rest.domain.ComponentCategory;
import com.b2international.snowowl.rest.snomed.domain.Acceptability;
import com.b2international.snowowl.rest.snomed.domain.CaseSignificance;
import com.b2international.snowowl.rest.snomed.impl.domain.SnomedDescriptionInput;

/**
 * @author apeteri
 * @since 1.0
 */
public class SnomedDescriptionRestInput extends AbstractSnomedComponentRestInput<SnomedDescriptionInput> {

	private String typeId;
	private String term;
	private String languageCode;
	private String conceptId;
	private CaseSignificance caseSignificance = CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE;
	private Map<String, Acceptability> acceptability;

	/**
	 * @return
	 */
	public String getTypeId() {
		return typeId;
	}

	/**
	 * @return
	 */
	public String getTerm() {
		return term;
	}

	/**
	 * @return
	 */
	public String getLanguageCode() {
		return languageCode;
	}

	/**
	 * @return
	 */
	public String getConceptId() {
		return conceptId;
	}

	/**
	 * @return
	 */
	public CaseSignificance getCaseSignificance() {
		return caseSignificance;
	}

	public Map<String, Acceptability> getAcceptability() {
		return acceptability;
	}

	public void setTypeId(final String typeId) {
		this.typeId = typeId;
	}

	public void setTerm(final String term) {
		this.term = term;
	}

	public void setLanguageCode(final String languageCode) {
		this.languageCode = languageCode;
	}

	public void setConceptId(final String conceptId) {
		this.conceptId = conceptId;
	}

	public void setCaseSignificance(final CaseSignificance caseSignificance) {
		this.caseSignificance = caseSignificance;
	}

	public void setAcceptability(final Map<String, Acceptability> acceptability) {
		this.acceptability = acceptability;
	}

	@Override
	protected SnomedDescriptionInput createComponentInput() {
		return new SnomedDescriptionInput();
	}

	/**
	 * @return
	 */
	@Override
	public SnomedDescriptionInput toComponentInput(final String version, final String taskId) {
		final SnomedDescriptionInput result = super.toComponentInput(version, taskId);

		result.setCaseSignificance(getCaseSignificance());
		result.setConceptId(getConceptId());
		result.setLanguageCode(getLanguageCode());
		result.setTerm(getTerm());
		result.setTypeId(getTypeId());
		result.setAcceptability(getAcceptability());

		return result;
	}

	@Override
	protected ComponentCategory getComponentCategory() {
		return DESCRIPTION;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("SnomedDescriptionRestInput [typeId=");
		builder.append(typeId);
		builder.append(", term=");
		builder.append(term);
		builder.append(", languageCode=");
		builder.append(languageCode);
		builder.append(", conceptId=");
		builder.append(conceptId);
		builder.append(", caseSignificance=");
		builder.append(caseSignificance);
		builder.append(", acceptability=");
		builder.append(acceptability);
		builder.append("]");
		return builder.toString();
	}
}

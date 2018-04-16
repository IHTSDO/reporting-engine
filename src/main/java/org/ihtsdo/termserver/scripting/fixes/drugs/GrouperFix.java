package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.List;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

/*
All concepts must be fully defined.
All concepts must have one and only one stated |Is a| relationship.
 - The parent concept for all concepts must be 373873005| Pharmaceutical / biologic product (product).
All concepts must have one or more Has active ingredient attributes.
 - The attribute values must be a descendant of 105590001|Substance (substance).
 */
public class GrouperFix extends BatchFix implements RF2Constants{

	protected GrouperFix(BatchFix clone) {
		super(clone);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = ensureDefinitionStatus(task, concept, DefinitionStatus.FULLY_DEFINED);
		changesMade += ensureAcceptableParent(task, concept, PHARM_BIO_PRODUCT);
		return changesMade;
	}

	@Override
	public String getScriptName() {
		return "GrouperFix";
	}

	@Override
	protected List<Concept> loadLine(String[] lineItems) throws TermServerScriptException {
		throw new TermServerScriptException("Not Implemented");
	}

}

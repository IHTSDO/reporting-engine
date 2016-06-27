package org.ihtsdo.termserver.scripting.fixes;

import java.util.List;

import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Task;

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
	public int doFix(Task task, Concept concept) throws TermServerFixException {
		int changesMade = ensureDefinitionStatus(task, concept, DEFINITION_STATUS.FULLY_DEFINED);
		changesMade += ensureAcceptableParent(task, concept, graph.getConcept(PHARM_BIO_PRODUCT_SCTID));
		return changesMade;
	}

	@Override
	public String getFixName() {
		return "GrouperFix";
	}

	@Override
	Batch formIntoBatch(String fileName, List<Concept> allConcepts,
			String branchPath) throws TermServerFixException {
		throw new TermServerFixException("Not Implemented");
	}

}

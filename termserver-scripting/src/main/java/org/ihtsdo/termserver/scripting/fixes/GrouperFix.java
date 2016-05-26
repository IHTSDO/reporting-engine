package org.ihtsdo.termserver.scripting.fixes;

import java.util.List;

import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;

/*

 */
public class GrouperFix extends BatchFix implements RF2Constants{

	protected GrouperFix(BatchFix clone) {
		super(clone);
	}

	@Override
	public int doFix(Batch batch, Concept concept) throws TermServerFixException {
		int changesMade = ensureDefinitionStatus(batch, concept, DEFINITION_STATUS.PRIMITIVE);
		changesMade += ensureAcceptableParent(batch, concept, graph.getConcept(PHARM_BIO_PRODUCT_SCTID));
		return changesMade;
	}

	@Override
	public String getFixName() {
		return "GrouperFix";
	}

	@Override
	List<Batch> formIntoBatches(String fileName, List<Concept> allConcepts,
			String branchPath) throws TermServerFixException {
		throw new TermServerFixException("Not Implemented");
	}

}

package org.ihtsdo.snowowl.authoring.api.model.lexical;

import org.ihtsdo.snowowl.authoring.api.model.work.*;

public class LexicalModelContentValidator {

	public void validate(WorkingContent content, LexicalModel lexicalModel, ContentValidationResult result) {
		ConceptResultFactory resultFactory = result.getConceptResultFactory();
		for (WorkingConcept concept : content.getConcepts()) {
			ConceptValidationResult conceptResult = resultFactory.next();
			String term = concept.getTerm();
			String termMessage = "";
			if (term == null || term.isEmpty()) {
				termMessage = "Lexical term is required.";
			}
			conceptResult.setTermMessage(termMessage);
		}
	}

}

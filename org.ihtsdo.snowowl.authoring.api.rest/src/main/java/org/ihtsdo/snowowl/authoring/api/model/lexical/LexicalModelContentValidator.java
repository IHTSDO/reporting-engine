package org.ihtsdo.snowowl.authoring.api.model.lexical;

import org.ihtsdo.snowowl.authoring.api.model.AuthoringContent;
import org.ihtsdo.snowowl.authoring.api.model.AuthoringContentValidationResult;

public class LexicalModelContentValidator {

	public void validate(AuthoringContent content, LexicalModel lexicalModel, AuthoringContentValidationResult result) {

		String term = content.getTerm();
		String termMessage = "";
		if (term == null || term.isEmpty()) {
			termMessage = "Lexical term is required.";
		}
		result.setTermMessage(termMessage);
	}

}

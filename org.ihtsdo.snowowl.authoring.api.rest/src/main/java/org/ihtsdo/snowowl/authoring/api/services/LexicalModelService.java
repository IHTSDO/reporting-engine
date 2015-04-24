package org.ihtsdo.snowowl.authoring.api.services;

import org.ihtsdo.snowowl.authoring.api.model.lexical.LexicalModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.List;

public class LexicalModelService {

	@Autowired
	private ModelDAO modelDAO;

	public void saveModel(LexicalModel lexicalModel) throws IOException {
		String name = lexicalModel.getName();
		Assert.notNull(name, "Lexical model name can not be null.");
		modelDAO.writeModel(lexicalModel);
	}

	// TODO
	public List<String> validateModel(LexicalModel lexicalModel) {
		return null;
	}

	public LexicalModel loadModel(String name) throws IOException {
		Assert.notNull(name, "Lexical model name can not be null.");
		return modelDAO.loadModel(LexicalModel.class, name);
	}

	public List<String> listModelNames() {
		return modelDAO.listModelNames(LexicalModel.class);
	}
}

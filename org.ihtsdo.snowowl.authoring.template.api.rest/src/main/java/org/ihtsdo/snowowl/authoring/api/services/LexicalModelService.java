package org.ihtsdo.snowowl.authoring.single.api.services;

import org.ihtsdo.snowowl.authoring.single.api.model.lexical.LexicalModel;
import org.ihtsdo.snowowl.authoring.single.api.model.lexical.Term;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LexicalModelService {

	@Autowired
	private ModelDAO modelDAO;

	public void saveModel(LexicalModel lexicalModel) throws IOException {
		String name = lexicalModel.getName();
		Assert.notNull(name, "Lexical model name can not be null.");
		modelDAO.writeModel(lexicalModel);
	}

	public List<String> validateModel(LexicalModel lexicalModel) {
		List<String> messages = new ArrayList<>();

		String name = lexicalModel.getName();
		if (name == null || name.isEmpty()) {
			messages.add("Name is required.");
		}
		Term fsn = lexicalModel.getFsn();
		if (fsn == null) {
			messages.add("FSN is required.");
		}
		Term synonom = lexicalModel.getSynonom();
		if (synonom == null) {
			messages.add("Synonom is required.");
		}

		return messages;
	}

	public LexicalModel loadModelOrThrow(String lexicalModelName) throws IOException {
		LexicalModel lexicalModel = loadModel(lexicalModelName);
		if (lexicalModel == null) throw new SomethingNotFoundException(LexicalModel.class.getSimpleName(), lexicalModelName);
		return lexicalModel;
	}

	public LexicalModel loadModel(String name) throws IOException {
		Assert.notNull(name, "Lexical model name can not be null.");
		return modelDAO.loadModel(LexicalModel.class, name);
	}

	public List<String> listModelNames() {
		return modelDAO.listModelNames(LexicalModel.class);
	}
}

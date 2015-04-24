package org.ihtsdo.snowowl.authoring.api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ihtsdo.snowowl.authoring.api.model.Model;
import org.ihtsdo.snowowl.authoring.api.model.logical.LogicalModel;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ModelDAO {

	public static final String JSON_EXTENSION = ".json";

	@Autowired
	private ObjectMapper jsonMapper;

	private File baseFilesDirectory;

	public ModelDAO() {
		this(new File(""));
	}

	public ModelDAO(File baseFilesDirectory) {
		this.baseFilesDirectory = baseFilesDirectory;
	}

	public void writeModel(Model model) throws IOException {
		try (FileWriter writer = new FileWriter(getModelFile(model))) {
			jsonMapper.writeValue(writer, model);
		}
	}

	public List<String> listModelNames(Class<? extends Model> modelClass) {
		File modelsDirectory = getModelsDirectory(modelClass);
		File[] files = modelsDirectory.listFiles();

		List<String> names = new ArrayList<>();
		for (File file : files) {
			String name = file.getName();
			if (name.endsWith(JSON_EXTENSION)) {
				names.add(name.replaceFirst("\\.json$", ""));
			}
		}
		return names;
	}

	public <T extends Model> T loadModel(Class<T> modelClass, String name) throws IOException {
		File modelFile = getModelFile(modelClass, name);
		if (modelFile.isFile()) {
			try (FileReader src = new FileReader(modelFile)) {
				return jsonMapper.readValue(src, modelClass);
			}
		} else {
			throw (modelClass.equals(LogicalModel.class)) ? new LogicalModelNotFoundException(name) : new LexicalModelNotFoundException(name);
		}
	}

	private File getModelFile(Model model) {
		return getModelFile(model.getClass(), model.getName());
	}

	private File getModelFile(Class<? extends Model> modelClass, String modelName) {
		return new File(getModelsDirectory(modelClass), modelName + JSON_EXTENSION);
	}

	public File getModelsDirectory(Class<? extends Model> modelClass) {
		File modelsDirectory = new File(baseFilesDirectory, "resources/org.ihtsdo.snowowl.authoring/" + modelClass.getSimpleName());
		if (!modelsDirectory.isDirectory()) {
			modelsDirectory.mkdirs();
		}
		return modelsDirectory;
	}

	public void setBaseFilesDirectory(File baseFilesDirectory) {
		this.baseFilesDirectory = baseFilesDirectory;
	}
}

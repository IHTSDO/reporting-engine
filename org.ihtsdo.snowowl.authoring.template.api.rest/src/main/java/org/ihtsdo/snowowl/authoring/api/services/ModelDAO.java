package org.ihtsdo.snowowl.authoring.single.api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ihtsdo.snowowl.authoring.single.api.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private Logger logger = LoggerFactory.getLogger(getClass());

	// Used by Spring
	@SuppressWarnings("unused")
	public ModelDAO() {
		this(new File(".").getParentFile());
	}

	public ModelDAO(File baseFilesDirectory) {
		this.baseFilesDirectory = baseFilesDirectory;
	}

	public void writeModel(Model model) throws IOException {
		writeModel(model, getModelFile(model));
	}

	public void writeModel(Model parentModel, Model model) throws IOException {
		writeModel(model, getModelFile(parentModel, model.getClass(), model.getName()));
	}

	private void writeModel(Model model, File modelFile) throws IOException {
		logger.info("Writing model {}:{} to {}", model.getClass().getSimpleName(), model.getName(), modelFile.getAbsolutePath());
		try (FileWriter writer = new FileWriter(modelFile)) {
			jsonMapper.writeValue(writer, model);
		}
	}

	public List<String> listModelNames(Class<? extends Model> modelClass) {
		return listModelNames(getModelsDirectory(modelClass));
	}

	public List<String> listModelNames(Model parentModel, Class<? extends Model> modelClass) {
		return listModelNames(getModelsDirectory(parentModel, modelClass));
	}

	private List<String> listModelNames(File modelsDirectory) {
		File[] files = modelsDirectory.listFiles();
		List<String> names = new ArrayList<>();
		if (files != null) {
			for (File file : files) {
				String name = file.getName();
				if (name.endsWith(JSON_EXTENSION)) {
					names.add(name.replaceFirst("\\.json$", ""));
				}
			}
		}
		return names;
	}

	public <T extends Model> T loadModel(Class<T> modelClass, String name) throws IOException {
		return readModelFile(modelClass, getModelFile(modelClass, name));
	}

	public <T extends Model> T loadModel(Model parentModel, Class<T> modelClass, String name) throws IOException {
		return readModelFile(modelClass, getModelFile(parentModel, modelClass, name));
	}

	private <T extends Model> T readModelFile(Class<T> modelClass, File modelFile) throws IOException {
		if (modelFile.isFile()) {
			try (FileReader src = new FileReader(modelFile)) {
				return jsonMapper.readValue(src, modelClass);
			}
		} else {
			return null;
		}
	}

	private File getModelFile(Model model) {
		return getModelFile(model.getClass(), model.getName());
	}

	private File getModelFile(Model parentModel, Class<? extends Model> modelClass, String modelName) {
		File parentModelDir = getModelsDirectory(parentModel, modelClass);
		return new File(parentModelDir, modelName + JSON_EXTENSION);
	}

	private File getModelFile(Class<? extends Model> modelClass, String modelName) {
		return new File(getModelsDirectory(modelClass), modelName + JSON_EXTENSION);
	}

	private File getModelsDirectory(Model parentModel, Class<? extends Model> modelClass) {
		File parentModelsDir = getModelsDirectory(parentModel.getClass());
		File parentModelDir = new File(parentModelsDir, parentModel.getName() + "/" + modelClass.getSimpleName());
		parentModelDir.mkdirs();
		return parentModelDir;
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

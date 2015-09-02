package org.ihtsdo.snowowl.authoring.single.api.service.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.IOException;

public class ObjectJsonStore extends ArbitraryJsonStore {

	@Autowired
	private ObjectMapper objectMapper;

	public ObjectJsonStore() {
	}

	public ObjectJsonStore(File baseDirectory) {
		super(baseDirectory);
	}

	public void writeObject(String path, Object objectToSerialise) throws IOException {
		objectMapper.writeValue(getFile(path).toFile(), objectToSerialise);
	}

	public <T> T readObject(String path, Class<T> type) throws IOException {
		return objectMapper.readValue(getFile(path).toFile(), type);
	}

	void setObjectMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}
}

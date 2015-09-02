package org.ihtsdo.snowowl.authoring.single.api.service.dao;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Files;
import org.ihtsdo.otf.rest.client.ClassificationResults;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.FileNotFoundException;

public class ObjectJsonStoreTest {

	private static final String PATH = "a/b/c";

	private ObjectJsonStore objectJsonStore;

	@Before
	public void setUp() throws Exception {
		objectJsonStore = new ObjectJsonStore(Files.createTempDir());
		final ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		objectJsonStore.setObjectMapper(objectMapper);
	}

	@Test
	public void testWriteObject() throws Exception {
		final ClassificationResults results = new ClassificationResults();
		objectJsonStore.writeObject(PATH, results);
	}

	@Test
	public void testWriteReadObject() throws Exception {
		final ClassificationResults results = new ClassificationResults();
		final String classificationId = "123";
		results.setClassificationId(classificationId);
		objectJsonStore.writeObject(PATH, results);

		final ClassificationResults readResults = objectJsonStore.readObject(PATH, ClassificationResults.class);
		Assert.assertNotNull(readResults);
		Assert.assertEquals(classificationId, readResults.getClassificationId());
	}

	@Test(expected = FileNotFoundException.class)
	public void testReadNonExistentObject() throws Exception {
		objectJsonStore.readObject(PATH + "/something", ClassificationResults.class);
	}
}

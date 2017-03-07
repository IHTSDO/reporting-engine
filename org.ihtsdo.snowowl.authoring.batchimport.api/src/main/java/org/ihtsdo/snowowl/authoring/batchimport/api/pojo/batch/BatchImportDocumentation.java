package org.ihtsdo.snowowl.authoring.batchimport.api.pojo.batch;

import java.util.Map;
import java.util.TreeMap;

public class BatchImportDocumentation {
	private Map<String, String> items = new TreeMap<>();
	
	public void addItem (String key, String value) {
		items.put(key, value);
	}

}

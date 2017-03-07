package org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo;

import java.util.Map;
import java.util.TreeMap;

public class BatchImportDocumentation {
	private Map<String, String> items = new TreeMap<>();
	
	public void addItem (String key, String value) {
		items.put(key, value);
	}

}

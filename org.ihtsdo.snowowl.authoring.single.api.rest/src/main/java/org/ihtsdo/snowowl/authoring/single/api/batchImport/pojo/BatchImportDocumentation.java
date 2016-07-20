package org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo;

import java.util.Map;
import java.util.TreeMap;

public class BatchImportDocumentation {
	Map<String, String> items = new TreeMap<String, String>();
	
	public void addItem (String key, String value) {
		items.put(key, value);
	}

}

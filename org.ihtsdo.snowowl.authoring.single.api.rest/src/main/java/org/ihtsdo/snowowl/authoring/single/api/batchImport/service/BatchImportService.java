package org.ihtsdo.snowowl.authoring.single.api.batchImport.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.csv.CSVRecord;
import org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo.BatchImportStatus;
import org.springframework.stereotype.Service;

@Service
public class BatchImportService {
	
	Map<UUID, BatchImportStatus> currentImports = new HashMap<UUID, BatchImportStatus>();

	public void startImport(String projectKey, List<CSVRecord> rows,
			String username) {
		
		//Check for > N children in the same file as the parent
		
		//Loop through the input file in groups of N
	}

	public BatchImportStatus getImportStatus(UUID batchImportId) {
		return currentImports.get(batchImportId);
	}

	public List<CSVRecord> getImportResults(UUID batchImportId) {
		// TODO Auto-generated method stub
		return null;
	}

}

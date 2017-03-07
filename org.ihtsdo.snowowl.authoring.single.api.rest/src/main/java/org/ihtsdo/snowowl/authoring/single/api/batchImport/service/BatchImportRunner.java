package org.ihtsdo.snowowl.authoring.single.api.batchImport.service;

import org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo.BatchImportRun;
import org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo.BatchImportState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;


class BatchImportRunner implements Runnable {
	
	private BatchImportRun batchImportRun;
	private BatchImportService service;
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private SecurityContext securityContext;
	
	public BatchImportRunner(BatchImportRun batchImportRun,
			BatchImportService batchImportService) {
		this.batchImportRun = batchImportRun;
		this.service = batchImportService;
		this.securityContext = SecurityContextHolder.getContext();
	}

	@Override
	public void run() {
		boolean completed = false;
		try{
			//Set the security context on this thread before Jira tries to use it
			SecurityContextHolder.setContext(this.securityContext);
			service.loadConceptsOntoTasks(batchImportRun);
			completed = true;
		} catch (Exception e) {
			logger.error("Batch Import Runner encountered unexepected error",e);
		} finally {
				BatchImportState finalState = completed ? BatchImportState.COMPLETED : BatchImportState.FAILED;
				service.getBatchImportStatus(batchImportRun.getId()).setState(finalState);
				service.outputCSV(batchImportRun);
				logger.info("Batch Importing {} in project {} - batch import id {} ", finalState.toString(),batchImportRun.getImportRequest().getProjectKey(), batchImportRun.getId().toString());
			}
	}
	


}

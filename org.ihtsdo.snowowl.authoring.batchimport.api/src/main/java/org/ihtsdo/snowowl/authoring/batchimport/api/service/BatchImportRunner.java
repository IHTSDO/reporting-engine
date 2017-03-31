package org.ihtsdo.snowowl.authoring.batchimport.api.service;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.ihtsdo.otf.rest.client.snowowl.SnowOwlRestClient;
import org.ihtsdo.snowowl.authoring.batchimport.api.client.AuthoringServicesClient;
import org.ihtsdo.snowowl.authoring.batchimport.api.pojo.batch.BatchImportRun;
import org.ihtsdo.snowowl.authoring.batchimport.api.pojo.batch.BatchImportState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

class BatchImportRunner implements Runnable {
	
	private BatchImportRun batchImportRun;
	private BatchImportService service;
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private SecurityContext securityContext;
	private AuthoringServicesClient asClient;
	private SnowOwlRestClient soClient;
	
	public BatchImportRunner(BatchImportRun batchImportRun,
			BatchImportService batchImportService, 
			AuthoringServicesClient asClient,
			SnowOwlRestClient soClient) {
		this.batchImportRun = batchImportRun;
		this.service = batchImportService;
		this.securityContext = SecurityContextHolder.getContext();
		this.asClient = asClient;
		this.soClient = soClient;
	}

	@Override
	public void run() {
		boolean completed = false;
		try{
			//Set the security context on this thread before Jira tries to use it
			SecurityContextHolder.setContext(this.securityContext);
			service.loadConceptsOntoTasks(batchImportRun, asClient, soClient);
			completed = true;
		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			String msg = "Batch Import Runner encountered unexepected error: " + e.getClass().getSimpleName();
			logger.error(msg,e);
			msg += ": \n" + sw.toString();
			service.getBatchImportStatus(batchImportRun.getId()).setMessage(msg);
		} finally {
				BatchImportState finalState = completed ? BatchImportState.COMPLETED : BatchImportState.FAILED;
				service.getBatchImportStatus(batchImportRun.getId()).setState(finalState);
				service.outputCSV(batchImportRun);
				logger.info("Batch Importing {} in project {} - batch import id {} ", finalState.toString(),batchImportRun.getImportRequest().getProjectKey(), batchImportRun.getId().toString());
			}
	}
	
}

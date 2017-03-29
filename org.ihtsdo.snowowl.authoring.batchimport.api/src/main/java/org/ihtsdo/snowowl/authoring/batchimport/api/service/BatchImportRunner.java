package org.ihtsdo.snowowl.authoring.batchimport.api.service;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.ihtsdo.snowowl.authoring.batchimport.api.client.AuthoringServicesClient;
import org.ihtsdo.snowowl.authoring.batchimport.api.pojo.batch.BatchImportRun;
import org.ihtsdo.snowowl.authoring.batchimport.api.pojo.batch.BatchImportState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.google.common.base.Throwables;


class BatchImportRunner implements Runnable {
	
	private BatchImportRun batchImportRun;
	private BatchImportService service;
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private SecurityContext securityContext;
	private AuthoringServicesClient client;
	
	public BatchImportRunner(BatchImportRun batchImportRun,
			BatchImportService batchImportService, AuthoringServicesClient client) {
		this.batchImportRun = batchImportRun;
		this.service = batchImportService;
		this.securityContext = SecurityContextHolder.getContext();
		this.client = client;
	}

	@Override
	public void run() {
		boolean completed = false;
		try{
			//Set the security context on this thread before Jira tries to use it
			SecurityContextHolder.setContext(this.securityContext);
			service.loadConceptsOntoTasks(batchImportRun, client);
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

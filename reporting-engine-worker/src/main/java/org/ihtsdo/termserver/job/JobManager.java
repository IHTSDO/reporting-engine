package org.ihtsdo.termserver.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.ihtsdo.otf.dao.resources.ResourceManager;
import org.ihtsdo.termserver.job.mq.Transmitter;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JobManager {
	
	static final String METADATA = "METADATA";
	
	protected Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private final ResourceManager snomedReleaseResourceManager;
	
	Map<String, Class<? extends JobClass>> knownJobs = new HashMap<>();
	
	@Autowired(required = false)
	private BuildProperties buildProperties;
	String buildVersion = "<Version Unknown>";
	
	@Autowired
	Transmitter transmitter;
	
	JobManager (
			@Autowired SnomedReleaseResourceConfiguration snomedReleaseResourceConfiguration,
			@Autowired ResourceLoader cloudResourceLoader) {
		logger.info("Configuring Release Resource Manager");
		snomedReleaseResourceManager = new ResourceManager(snomedReleaseResourceConfiguration, cloudResourceLoader);
	}

	@PostConstruct
	public void init(){
		if (knownJobs.size() > 0) {
			logger.info("Job Manager rejecting attempt at 2nd initialisation");
			return;
		}
		logger.info("Job Manager Initialising");
		
		//Now what jobs do I know about?
		Reflections reflections = new Reflections("org.ihtsdo.termserver.scripting");
		Set<Class<? extends JobClass>> jobClasses = reflections.getSubTypesOf(JobClass.class);
		
		logger.info("Job Manager detected {} job classes", jobClasses.size());
		for (Class<? extends JobClass> jobClass : jobClasses) {
			try {
				//Is this a thing we can actually instantiate?
				if (!jobClass.isInterface()) {
					Job thisJob = jobClass.newInstance().getJob();
					logger.info("Registering known job: {}", thisJob.getName());
					knownJobs.put(thisJob.getName(), jobClass);
				} else {
					logger.info("Ignoring interface {}", jobClass);
				}
			} catch (IllegalAccessException | InstantiationException e) {
				logger.error("Failed to register job {}", jobClass, e);
			} 
		}
		
		if (buildProperties != null) {
			buildVersion = buildProperties.getVersion();
		}
	}

	public void run(JobRun jobRun) {
		boolean metadataRequest = false;
		try {
			//Is this a special metadata request?
			if (jobRun.getJobName().equals(METADATA)) {
				metadataRequest = true;
				transmitMetadata();
			} else {
				//Do I know about this job?
				Class<? extends JobClass> jobClass = knownJobs.get(jobRun.getJobName());
				if (jobClass == null) {
					jobRun.setStatus(JobStatus.Failed);
					jobRun.setDebugInfo("Job '" + jobRun.getJobName() + "' not known to Reporting Engine Worker - " + buildVersion);
				} else {
					try {
						if (ensureJobValid(jobRun)) {
							JobClass thisJob = jobClass.newInstance();
							thisJob.instantiate(jobRun);
						}
					} catch (IllegalAccessException | InstantiationException e) {
						jobRun.setStatus(JobStatus.Failed);
						jobRun.setDebugInfo("Job '" + jobRun.getJobName() + "' failed due to " + e);
					} 
				}
			}
		} finally {
			if (!metadataRequest)
				transmitter.send(jobRun);
		}
	}

	private boolean ensureJobValid(JobRun jobRun) {
		if (StringUtils.isEmpty(jobRun.getAuthToken())) {
			jobRun.setStatus(JobStatus.Failed);
			jobRun.setDebugInfo("No valid authenticatin token included in request");
			return false;
		}
		
		if (StringUtils.isEmpty(jobRun.getTerminologyServerUrl())) {
			jobRun.setStatus(JobStatus.Failed);
			jobRun.setDebugInfo("No terminology server url included in request");
			return false;
		}
		return true;
	}

	private void transmitMetadata() {
		List<Job> jobs = new ArrayList<>();
		for (Map.Entry<String, Class<? extends JobClass>> knownJobClass : knownJobs.entrySet()) {
			try {
				Job thisJob = knownJobClass.getValue().newInstance().getJob();
				jobs.add(thisJob);
			} catch (IllegalAccessException | InstantiationException e) {
				logger.error("Unable to return metadata on {}",knownJobClass.getKey(), e);
			} 
		}
		JobMetadata metadata = new JobMetadata();
		metadata.setJobs(jobs);
		transmitter.send(metadata);
	}
}

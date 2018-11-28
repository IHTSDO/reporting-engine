package org.ihtsdo.termserver.job;

import java.util.*;

import javax.annotation.PostConstruct;

import org.ihtsdo.otf.resourcemanager.*;
import org.ihtsdo.termserver.job.mq.Transmitter;
import org.ihtsdo.termserver.scripting.TermServerScript;
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
	Map<String, JobType> knownJobTypes = new HashMap<>();
	
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
	
	public Job getJob (String jobName) {
		//Do I know about this job?
		Class<? extends JobClass> jobClass = knownJobs.get(jobName);
		if (jobClass != null) {
			try {
				return jobClass.newInstance().getJob();
			} catch (IllegalAccessException | InstantiationException e) {
				TermServerScript.warn("Unable to instantiate job: " + jobName);
			}
		}
		return null;
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
						if (ensureJobValid(jobRun, jobClass.newInstance().getJob())) {
							JobClass thisJob = jobClass.newInstance();
							jobRun.setStatus(JobStatus.Running);
							transmitter.send(jobRun);
							thisJob.instantiate(jobRun);
						} else {
							jobRun.setStatus(JobStatus.Failed);
						}
					} catch (IllegalAccessException | InstantiationException e) {
						jobRun.setStatus(JobStatus.Failed);
						jobRun.setDebugInfo("Job '" + jobRun.getJobName() + "' failed due to: '" + e + "'");
					} 
				}
			}
		} finally {
			if (!metadataRequest) {
				jobRun.setResultTime(new Date());
				transmitter.send(jobRun);
			}
		}
	}

	private boolean ensureJobValid(JobRun jobRun, Job job) {
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
		
		//Check we have all mandatory parameters
		if (job != null) {
			for (String paramKey : job.getParameters().keySet()) {
				if (job.getParameters().get(paramKey).getMandatory()) {
					String value = jobRun.getParamValue(paramKey);
					if (StringUtils.isEmpty(value)) {
						jobRun.setDebugInfo("Mandatory parameter '" + paramKey + "' not supplied.");
						return false;
					}
				}
			}
		}
		return true;
	}

	private void transmitMetadata() {
		for (Map.Entry<String, Class<? extends JobClass>> knownJobClass : knownJobs.entrySet()) {
			try {
				Job thisJob = knownJobClass.getValue().newInstance().getJob();
				
				//Some jobs shouldn't see the light of day.
				//TODO Make this code environemnt aware so it allows testing status in Dev and UAT
				if (thisJob.getProductionStatus().equals(Job.ProductionStatus.HIDEME)) {
					continue;
				}
				
				JobType indicatedType = thisJob.getCategory().getType();
				if (indicatedType == null) {
					indicatedType = new JobType(JobType.REPORT);
				}
				//The indicated type was created fresh in passing.  We need to construct
				//this as a single object hierarchy using the same common objects.
				JobType jobType = knownJobTypes.get(indicatedType.getName());
				if (jobType == null) {
					jobType = indicatedType;
					knownJobTypes.put(indicatedType.getName(), indicatedType);
				}
				if (thisJob.getCategory() == null) {
					logger.error("Job '{}' does not indicate its category.  Unable to transmit.",thisJob.getName());
				} else {
					JobCategory jobCategory = jobType.getCategory(thisJob.getCategory().getName());
					if (jobCategory == null) {
						jobCategory = new JobCategory(null, thisJob.getCategory().getName());
						jobType.addCategory(jobCategory);
					}
					jobCategory.addJob(thisJob);
					thisJob.setCategory(jobCategory);
				}
				thisJob.getCategory().setType(indicatedType);
			} catch (IllegalAccessException | InstantiationException e) {
				logger.error("Unable to return metadata on {}",knownJobClass.getKey(), e);
			} 
		}
		JobMetadata metadata = new JobMetadata();
		metadata.setJobTypes(new ArrayList<>(knownJobTypes.values()));
		transmitter.send(metadata);
	}
}

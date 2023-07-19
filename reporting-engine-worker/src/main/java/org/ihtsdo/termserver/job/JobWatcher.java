package org.ihtsdo.termserver.job;

import java.net.InetAddress;
import java.util.*;

import org.ihtsdo.termserver.job.mq.Transmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.JobRun;
import org.snomed.otf.scheduler.domain.SnomedServiceException;
import org.springframework.web.client.RestTemplate;

public class JobWatcher implements Runnable {
	
	protected Logger logger = LoggerFactory.getLogger(this.getClass());
	
	int timeout;
	JobRun jobRun;
	Transmitter transmitter;
	
	public JobWatcher (int timeout, JobRun jobRun, Transmitter transmitter) {
		this.timeout = timeout;
		this.jobRun = jobRun;
		this.transmitter = transmitter;
	}

	@Override
	public void run() {
		try {
			logger.debug("JobWatcher starting for " + jobRun.getJobName() + " expecting completion within " + timeout + " minutes");
			Thread.sleep(timeout * 60 * 1000);
			SnomedServiceException sse = new SnomedServiceException();
			Map<String, Object> configuration = new HashMap<>();
			configuration.put("JobRun", jobRun);
			configuration.put("Machine Details", getMachineDetails());
			sse.setServiceName("Reporting Engine");
			sse.setEventStartTime(jobRun.getRequestTime());
			sse.setEventFailureTime(new Date());
			sse.setMessage("Report still running after " + timeout + " minutes.  Please check.");
			sse.setConfiguration(configuration);
			transmitter.send(sse);
		} catch (InterruptedException e) {
			logger.debug("JobWatcher standing down for job: " + jobRun.getJobName());
		}
	}

	private Map<String, Object> getMachineDetails() {
		Map<String, Object> machineDetails = new HashMap<>();
		//Are we running on an EC2 instance?
		RestTemplate rt = new RestTemplate();
		try {
			String ipInfo = rt.getForObject("http://169.254.169.254/latest/meta-data/public-ipv4", String.class);
			machineDetails.put("IPV4", ipInfo);
			
			String instance = rt.getForObject("http://169.254.169.254/latest/meta-data/public-hostname", String.class);
			machineDetails.put("EC2 Instance", instance);
		} catch (Exception e) {
			logger.error("Failed to recover EC2 IP information", e);
			try {
				InetAddress inetAddress = InetAddress.getLocalHost();
				machineDetails.put("IPV4",inetAddress.getHostAddress());
			} catch (Exception e2) {}
		}
		return machineDetails;
	}

}

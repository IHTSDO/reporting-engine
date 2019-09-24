package org.ihtsdo.termserver.scripting.dao;

import java.io.*;

import org.apache.commons.io.IOUtils;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.aws.core.io.s3.SimpleStorageResourceLoader;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.amazonaws.auth.*;
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

@Service
public class ArchiveDataLoader {
	
	@Autowired
	private ArchiveLoaderConfig archiveLoaderConfig;
	
	@Value("${cloud.aws.region.static}")
	private String region;
	
	@Value("${aws.key}")
	private String awsKey;
	
	@Value("${aws.secretKey}")
	private String awsSecretKey;
	
	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		TermServerScript.info("ArchiveDataLoader initialised - SpringBoot configuration");
		if (awsKey == null) {
			TermServerScript.info("ArchiveDataLoader - AWS Key missing?");
		} else if (awsKey.isEmpty()) {
			TermServerScript.info("ArchiveDataLoader - AWS Key configured through EC2 instance");
		} else {
			TermServerScript.info("ArchiveDataLoader using AWS Key: " + awsKey);
		}
	}
	
	public void download (File archive) throws TermServerScriptException {
		try {
			if (archiveLoaderConfig == null) {
				archiveLoaderConfig = new ArchiveLoaderConfig();
				archiveLoaderConfig.init("archives");
			}
			AWSCredentialsProvider awsCredProv;
			if (awsKey == null || awsKey.isEmpty()) {
				awsCredProv = new EC2ContainerCredentialsProviderWrapper();
				TermServerScript.info("Connecting to S3 with EC2 environment configured credentials");
			} else {
				AWSCredentials awsCreds = new BasicAWSCredentials(awsKey, awsSecretKey);
				awsCredProv = new AWSStaticCredentialsProvider(awsCreds);
				TermServerScript.info("Connecting to S3 with locally specified account: " + awsKey);
			}

			AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
									.withCredentials(awsCredProv)
									.withRegion(region)
									.build();
			ResourceManager resourceManager = new ResourceManager(archiveLoaderConfig, new SimpleStorageResourceLoader(s3Client));
			try (InputStream input = resourceManager.readResourceStream(archive.getName());
				OutputStream out = new FileOutputStream(archive);) {
				TermServerScript.info("Downloading " + archive.getName() + " from S3");
				IOUtils.copy(input, out);
				TermServerScript.info("Download complete");
			}
		} catch (Throwable  t) {
			final String msg = "Error when trying to download " + archive.getName() + " from S3 via :" +  archiveLoaderConfig;
			throw new TermServerScriptException(msg, t);
		} 
	}

	public static ArchiveDataLoader create() throws TermServerScriptException {
		ArchiveDataLoader config = new ArchiveDataLoader();
		LocalProperties properties = new LocalProperties(null);
		config.region = properties.getProperty("cloud.aws.region.static");
		config.awsKey = properties.getProperty("aws.key");
		config.awsSecretKey = properties.getProperty("aws.secretKey");
		return config;
	}
	
}

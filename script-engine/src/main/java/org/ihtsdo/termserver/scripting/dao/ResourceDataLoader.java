package org.ihtsdo.termserver.scripting.dao;

import java.io.*;

import org.apache.commons.io.IOUtils;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.aws.core.io.s3.SimpleStorageResourceLoader;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

@Service
public class ResourceDataLoader {
	private static final String[] fileNames = new String[] { 	"cs_words.tsv",
																"acceptable_dose_forms.tsv",
																"us-to-gb-terms-map.txt",
																"HighVolumeSCTIDs.txt"};
	
	@Autowired
	private ResourceLoaderConfig resourceConfig;
	
	@Value("${cloud.aws.region.static}")
	private String region;
	
	@Value("${aws.key}")
	private String awsKey;
	
	@Value("${aws.secretKey}")
	private String awsSecretKey;
	
	@Value("${resources.useCloud}")
	private String useCloudStr;
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ResourceDataLoader.class);

	@EventListener(ApplicationReadyEvent.class)
	private void init() throws TermServerScriptException {
		boolean useCloud = false;
		if (!StringUtils.isEmpty(useCloudStr)) {
			useCloud = useCloudStr.toLowerCase().equals("true");
		} else {
			throw new TermServerScriptException("Check application.properties - resources.useCloud is not specified");
		}
		
		if (useCloud) {
			try {
				AWSCredentialsProvider awsCredProv;
				if (awsKey == null || awsKey.isEmpty()) {
					awsCredProv = new EC2ContainerCredentialsProviderWrapper();
					TermServerScript.info("Connecting to S3 with EC2 environment configured credentials for resources");
				} else {
					AWSCredentials awsCreds = new BasicAWSCredentials(awsKey, awsSecretKey);
					awsCredProv = new AWSStaticCredentialsProvider(awsCreds);
					TermServerScript.info("Connecting to S3 for resources with locally specified account: " + awsKey);
				}
	
				AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
										.withCredentials(awsCredProv)
										.withRegion(region)
										.build();
				ResourceManager resourceManager = new ResourceManager(resourceConfig, new SimpleStorageResourceLoader(s3Client));
				for (String fileName : fileNames) {
					File localFile = new File ("resources/" + fileName);
					TermServerScript.info ("Downloading " + fileName + " from S3");
					try (InputStream input = resourceManager.readResourceStreamOrNullIfNotExists(fileName);
							OutputStream out = new FileOutputStream(localFile);) {
						if (input != null) {
							IOUtils.copy(input, out);
						}
					} catch (Exception e) {
						throw new TermServerScriptException ("Unable to load " + fileName + " from S3", e);
					}
				}
			} catch (Throwable  t) {
				final String errorMsg = "Error when trying to download the us-to-gb-terms-map.txt file from S3 via :" +  resourceConfig;
				LOGGER.error(errorMsg, t);
			}
			TermServerScript.info ("Resources download complete");
		} else {
			LOGGER.info("AWS S3 marked as local due to 'resources.useCloud=false' setting.");
		}
	}
	
	public static ResourceDataLoader create() throws TermServerScriptException {
		ResourceDataLoader loader = new ResourceDataLoader();
		LocalProperties properties = new LocalProperties(null);
		loader.region = properties.getProperty("cloud.aws.region.static");
		loader.awsKey = properties.getProperty("aws.key");
		loader.awsSecretKey = properties.getProperty("aws.secretKey");
		loader.useCloudStr = properties.getProperty("resources.useCloud");
		return loader;
	}
}

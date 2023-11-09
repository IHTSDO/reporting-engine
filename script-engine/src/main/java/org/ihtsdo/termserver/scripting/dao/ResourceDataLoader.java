package org.ihtsdo.termserver.scripting.dao;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.LocalProperties;
import org.snomed.otf.script.dao.SimpleStorageResourceLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

@Service
public class ResourceDataLoader {
	private static final String[] fileNames = new String[] {	"cs_words.tsv",
																"acceptable_dose_forms.tsv",
																"us-to-gb-terms-map.txt",
																"HighVolumeSCTIDs.txt",
																"legacy_int_release_summary.json",
																"prepositions.txt",
																"preposition-exceptions.txt"};
	
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
				S3Client s3Client;
				if (StringUtils.isEmpty(awsKey)) {
					s3Client = S3Client.builder()
							.region(DefaultAwsRegionProviderChain.builder().build().getRegion())
							.credentialsProvider(ProfileCredentialsProvider.create())
							.build();
					TermServerScript.info("Connecting to S3 with EC2 environment configured credentials for resources");
				} else {
					s3Client = S3Client.builder()
							.region(Region.of(region))
							.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(awsKey, awsSecretKey)))
							.build();
					TermServerScript.info("Connecting to S3 with locally specified account: " + awsKey);
				}

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

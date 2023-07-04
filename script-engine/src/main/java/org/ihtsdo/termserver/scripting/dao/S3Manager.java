package org.ihtsdo.termserver.scripting.dao;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.LocalProperties;
import org.snomed.otf.script.dao.SimpleStorageResourceLoader;
import org.snomed.otf.script.dao.StandAloneResourceConfig;

public class S3Manager {
	
	Logger logger = LoggerFactory.getLogger(this.getClass());

    private String region;
    private String awsKey;
    private String awsSecretKey;

    private StandAloneResourceConfig standAloneResourceConfig;
    private ResourceManager resourceManager;

    public S3Manager(StandAloneResourceConfig standAloneResourceConfig) {
        this.standAloneResourceConfig = standAloneResourceConfig;
    }

    public S3Manager(StandAloneResourceConfig standAloneResourceConfig,
                     String region,
                     String awsKey,
                     String awsSecretKey) {
        this(standAloneResourceConfig);
        this.region = region;
        this.awsKey = awsKey;
        this.awsSecretKey = awsSecretKey;
    }

    public S3Manager(StandAloneResourceConfig standAloneResourceConfig,
                     String configurationPrefix) throws TermServerScriptException {
        this(standAloneResourceConfig);

        // load properties if local
        if (!StringUtils.isEmpty(configurationPrefix)) {
            loadProperties(configurationPrefix);
        }
    }

    public ResourceManager getResourceManager() throws TermServerScriptException {

        if (resourceManager != null) {
            return resourceManager;
        }
        synchronized (this) {
            if (resourceManager != null) {
                return resourceManager;
            }
            try {
                AmazonS3 s3Client;
                if (StringUtils.isEmpty(awsKey)) {
                    s3Client = AmazonS3ClientBuilder.standard().build();
                    TermServerScript.info("Connecting to S3 with EC2 environment configured credentials");
                } else {
                    s3Client = AmazonS3ClientBuilder.standard()
                            .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(awsKey, awsSecretKey)))
                            .withRegion(region)
                            .build();
                    TermServerScript.info("Connecting to S3 with locally specified account: " + awsKey);
                    //AWS will still attempt to connect locally to discover its credentials, so let's only
                    //do debugging at "WARN"   See logback.xml file for configuration
                }
                SimpleStorageResourceLoader simpleStorageResourceLoader = new SimpleStorageResourceLoader(s3Client);
                simpleStorageResourceLoader.setTaskExecutor(task -> {
                });
                resourceManager = new ResourceManager(standAloneResourceConfig, simpleStorageResourceLoader);
            } catch (Throwable t) {
                final String msg = "Error when trying get the resource manager for S3 via :" + standAloneResourceConfig;
                throw new TermServerScriptException(msg, t);
            }
        }
        return resourceManager;
    }

    public boolean isUseCloud() {
        return standAloneResourceConfig.isUseCloud();
    }

    private boolean isConfigurationValid() {
        if (StringUtils.isEmpty(region) ||
                StringUtils.isEmpty(awsKey) ||
                StringUtils.isEmpty(awsSecretKey)) {
            return false;
        }
        return true;
    }

    private void loadProperties(String configurationPrefix) throws TermServerScriptException {
        try {
            LocalProperties properties = new LocalProperties(null);
            this.region = properties.getProperty("cloud.aws.region.static");
            this.awsKey = properties.getProperty("aws.key");
            this.awsSecretKey = properties.getProperty("aws.secretKey");

            if (standAloneResourceConfig != null) {
                standAloneResourceConfig.init(configurationPrefix);
            }
        } catch (Exception e) {
            String message = "Unable to create using local configuration. " +
                    "Check availability of application-local.properties";
            throw new TermServerScriptException (message,e);
        }
    }

    // For debugging
    public StandAloneResourceConfig getStandAloneResourceConfig() {
        return standAloneResourceConfig;
    }
}

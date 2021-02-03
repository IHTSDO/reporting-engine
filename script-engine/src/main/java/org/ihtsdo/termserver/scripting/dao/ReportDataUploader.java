package org.ihtsdo.termserver.scripting.dao;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;

@Service
public class ReportDataUploader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportDataUploader.class);

    private ReportDataUploaderConfig reportDataUploaderConfig;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Value("${aws.key}")
    private String awsKey;

    @Value("${aws.secretKey}")
    private String awsSecretKey;

    @Value("${archives.useCloud}")
    private String useCloudStr;

    private static S3Manager s3Manager;

    @Autowired
    public void setReportDataUploaderConfig(ReportDataUploaderConfig reportDataUploaderConfig) {
        this.reportDataUploaderConfig = reportDataUploaderConfig;
        s3Manager = new S3Manager(reportDataUploaderConfig, region, awsKey, awsSecretKey);
    }

    public String getUploadLocation(String protocol, String domain) {
        ResourceConfiguration.Cloud cloud  = reportDataUploaderConfig.getCloud();
        return protocol + cloud.getBucketName() + domain + cloud.getPath();
    }

    public void upload(File outputFile, File inputFile) throws TermServerScriptException {
        try {
            //In case we're running on a PC we need to convert backslashes to forward
            String filePath = outputFile.getPath().replaceAll("\\\\", "/");
            s3Manager.getResourceManager().writeResource(filePath,
                    new BufferedInputStream(new FileInputStream(inputFile)));
        } catch (Exception e) {
            throw new TermServerScriptException(e);
        }
    }

    public void setReportDataUploaderConfig(ReportDataUploaderConfig reportDataUploaderConfig, S3Manager s3Manager) {
        this.reportDataUploaderConfig = reportDataUploaderConfig;
        this.s3Manager = s3Manager;
    }

    public static ReportDataUploader create() throws TermServerScriptException {
        LOGGER.info("Creating ReportDataUploader based on local properties");
        ReportDataUploader uploader = new ReportDataUploader();

        ReportDataUploaderConfig reportDataUploaderConfig = new ReportDataUploaderConfig();
        s3Manager = new S3Manager(reportDataUploaderConfig, getConfigurationPrefix());
        uploader.setReportDataUploaderConfig(reportDataUploaderConfig, s3Manager);
        return uploader;
    }

    private static String getConfigurationPrefix() {
        return ReportDataUploaderConfig.class.getAnnotation(ConfigurationProperties.class).prefix();
    }
}

package org.ihtsdo.termserver.scripting.dao;

import org.apache.commons.io.IOUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.DataBroker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;

import java.io.*;
import java.nio.charset.StandardCharsets;

@Service
public class ReportDataBroker extends AbstractS3Component implements DataBroker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportDataBroker.class);

    private Gson gson;

    @Autowired
    public void setReportDataBrokerConfig(ReportDataBrokerConfig reportDataBrokerConfig) {
        initS3Manager(reportDataBrokerConfig);
    }

    public String getUploadLocation(String protocol, String domain) {
        ResourceConfiguration.Cloud cloud = s3Manager.getStandAloneResourceConfig().getCloud();
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
    
	public void upload(File outputFile, String data) throws TermServerScriptException {
        try {
            //In case we're running on a PC we need to convert backslashes to forward
            String filePath = outputFile.getPath().replaceAll("\\\\", "/");
            InputStream is = IOUtils.toInputStream(data, StandardCharsets.UTF_8);
            s3Manager.getResourceManager().getBucketNamePath();
            LOGGER.info("Uploading to S3 ({}): {}", s3Manager.getResourceManager().getBucketNamePath(),  outputFile);
            s3Manager.getResourceManager().writeResource(filePath, is);
        } catch (Exception e) {
            throw new TermServerScriptException(e);
        }
	}
    
    public InputStream download(File file) throws TermServerScriptException {
        try {
            //In case we're running on a PC we need to convert backslashes to forward
            String filePath = file.getPath().replaceAll("\\\\", "/");
            return s3Manager.getResourceManager().readResourceStream(filePath);
        } catch (Exception e) {
            throw new TermServerScriptException(e);
        }
    }

    public static ReportDataBroker create() throws TermServerScriptException {
        LOGGER.info("Creating ReportDataBroker based on local properties");
        ReportDataBroker broker = new ReportDataBroker();
        broker.initS3Manager(new ReportDataBrokerConfig(), getConfigurationPrefix(ReportDataBrokerConfig.class));
        return broker;
    }

	public boolean exists(File file) throws IOException, TermServerScriptException {
		boolean exists = s3Manager.getResourceManager().doesObjectExist(file);
		if (!exists) {
			LOGGER.debug("{} not found in S3.", file);
		}
		return exists;
	}

	public void setGson(Gson gson) {
		this.gson = gson;
	}
	
	public Gson getGson() {
		return this.gson;
	}


}

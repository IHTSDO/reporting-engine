package org.ihtsdo.termserver.job;

import org.snomed.otf.script.dao.StandAloneResourceConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Application properties in either a Spring or standalone context.
 */
@Component
public class ApplicationProperties extends StandAloneResourceConfig {
    @Value("${versioned-content-source.readonly}")
    private String versionedContentSourceReadOnly;

    @Value("${versioned-content-source.useCloud}")
    private String versionedContentSourceUseCloud;

    @Value("${versioned-content-source.local.path}")
    private String versionedContentSourceLocalPath;

    @Value("${versioned-content-source.cloud.bucketName}")
    private String versionedContentSourceCloudBucketName;

    @Value("${versioned-content-source.cloud.path}")
    private String versionedContentSourceCloudPath;

    @Value("${versioned-content.readonly}")
    private String versionedContentTargetReadOnly;

    @Value("${versioned-content.useCloud}")
    private String versionedContentTargetUseCloud;

    @Value("${versioned-content.local.path}")
    private String versionedContentTargetLocalPath;

    @Value("${versioned-content.cloud.bucketName}")
    private String versionedContentTargetCloudBucketName;

    public static ApplicationProperties from(ApplicationProperties applicationProperties) {
        ApplicationProperties clone = new ApplicationProperties();
        clone.versionedContentSourceReadOnly = applicationProperties.versionedContentSourceReadOnly;
        clone.versionedContentSourceUseCloud = applicationProperties.versionedContentSourceUseCloud;
        clone.versionedContentSourceLocalPath = applicationProperties.versionedContentSourceLocalPath;
        clone.versionedContentSourceCloudBucketName = applicationProperties.versionedContentSourceCloudBucketName;
        clone.versionedContentSourceCloudPath = applicationProperties.versionedContentSourceCloudPath;
        clone.versionedContentTargetReadOnly = applicationProperties.versionedContentTargetReadOnly;
        clone.versionedContentTargetUseCloud = applicationProperties.versionedContentTargetUseCloud;
        clone.versionedContentTargetLocalPath = applicationProperties.versionedContentTargetLocalPath;
        clone.versionedContentTargetCloudBucketName = applicationProperties.versionedContentTargetCloudBucketName;

        return clone;
    }

    public void initStandAloneResourceConfig(boolean readOnly, boolean useCloud, String localPath, String bucketName, String bucketPath) {
        this.setReadonly(readOnly);
        this.setUseCloud(useCloud);
        this.setLocal(new Local(localPath));
        this.setCloud(new Cloud(bucketName, bucketPath));
    }

    public boolean getVersionedContentSourceReadOnly() {
        return Boolean.parseBoolean(versionedContentSourceReadOnly);
    }

    public boolean getVersionedContentSourceUseCloud() {
        return Boolean.parseBoolean(versionedContentSourceUseCloud);
    }

    public String getVersionedContentSourceLocalPath() {
        return versionedContentSourceLocalPath;
    }

    public String getVersionedContentSourceCloudBucketName() {
        return versionedContentSourceCloudBucketName;
    }

    public String getVersionedContentSourceCloudPath() {
        return versionedContentSourceCloudPath;
    }

    public boolean getVersionedContentTargetReadOnly() {
        return Boolean.parseBoolean(versionedContentTargetReadOnly);
    }

    public boolean getVersionedContentTargetUseCloud() {
        return Boolean.parseBoolean(versionedContentTargetUseCloud);
    }

    public String getVersionedContentTargetLocalPath() {
        return versionedContentTargetLocalPath;
    }

    public String getVersionedContentTargetCloudBucketName() {
        return versionedContentTargetCloudBucketName;
    }
}

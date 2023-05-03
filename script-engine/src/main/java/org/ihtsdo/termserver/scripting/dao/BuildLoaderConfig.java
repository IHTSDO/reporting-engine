package org.ihtsdo.termserver.scripting.dao;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="builds")
public class BuildLoaderConfig extends ArchiveLoaderConfig {
}

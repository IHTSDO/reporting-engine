package org.ihtsdo.termserver.scripting.dao;

import org.snomed.otf.script.dao.StandAloneResourceConfig;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix="archives")
@EnableAutoConfiguration
public class ArchiveLoaderConfig extends StandAloneResourceConfig {
}

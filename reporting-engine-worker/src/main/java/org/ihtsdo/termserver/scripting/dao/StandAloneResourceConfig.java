package org.ihtsdo.termserver.scripting.dao;

import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.ihtsdo.termserver.scripting.TermServerScriptException;

/**
 * This is needed when we're not running as a Spring Boot application and don't 
 * have access to Autowired and all that goodness
 */
public class StandAloneResourceConfig extends ResourceConfiguration {
	
	public void init(String prefix) throws TermServerScriptException {
			LocalProperties properties = new LocalProperties(prefix);
			setReadonly(properties.getBooleanProperty("readonly"));
			setUseCloud(properties.getBooleanProperty("useCloud"));
			setLocal(new Local(properties.getProperty("local.path")));
			setCloud(new Cloud(properties.getProperty("cloud.bucketName"), 
					properties.getProperty("cloud.path")));
	}
}

package org.ihtsdo.termserver.scripting.dao;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Properties;

import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.ihtsdo.termserver.scripting.TermServerScriptException;

/**
 * This is needed when we're not running as a Spring Boot application and don't 
 * have access to Autowired and all that goodness
 */
public class LocalProperties extends ResourceConfiguration {
	
	InputStream is;
	String prefix;
	String propertiesFilename = "application-local.properties";
	boolean isInitialised = false;
	Properties prop = new Properties();
	
	LocalProperties(String prefix) {
		if (prefix != null) {
			if (!prefix.endsWith(".")) {
				prefix += ".";
			}
			this.prefix = prefix;
		} else {
			this.prefix = "";
		}
	}

	public void init() throws TermServerScriptException {
		try {
			is = getClass().getClassLoader().getResourceAsStream(propertiesFilename);
			if (is == null) {
				//If running locally, not from a jar file
				is = new FileInputStream(propertiesFilename);
			}
			if (is != null) {
				prop.load(is);
			} else {
				throw new FileNotFoundException("Property file '" + propertiesFilename + "' not found in the classpath");
			}
			isInitialised = true;
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to load " + propertiesFilename, e);
		} finally {
			try {
				is.close();
			} catch (Exception e) {}
		}
	}
	
	public String getProperty (String propName) throws TermServerScriptException {
		if (!isInitialised) {
			init();
		}
		return prop.getProperty(prefix + propName);
	}
	
	public Boolean getBooleanProperty (String propName) throws TermServerScriptException {
		if (!isInitialised) {
			init();
		}
		String bool = prop.getProperty(prefix + propName);
		if (bool == null) {
			throw new IllegalArgumentException(prefix + propName + " was not found in local properties file");
		}
		return bool.toLowerCase().equals("true") ? Boolean.TRUE : Boolean.FALSE;
	}
}

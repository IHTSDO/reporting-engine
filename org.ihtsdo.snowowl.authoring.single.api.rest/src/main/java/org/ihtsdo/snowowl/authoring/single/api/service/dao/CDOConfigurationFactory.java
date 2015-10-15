package org.ihtsdo.snowowl.authoring.single.api.service.dao;

import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.config.SnowOwlConfiguration;
import com.b2international.snowowl.datastore.config.RepositoryConfiguration;

import java.util.Map;

public class CDOConfigurationFactory {

	private Map<Object, Object> snomedStore;

	public CDOConfigurationFactory() {
		RepositoryConfiguration config = ApplicationContext.getInstance().getServiceChecked(SnowOwlConfiguration.class).getModuleConfig(RepositoryConfiguration.class);
		snomedStore = config.getDatasourceProperties("snomedStore");
	}

	public String getUrl() {
		return snomedStore.get("uRL").toString();
	}

	public String getDriverClassName() {
		return snomedStore.get("class").toString();
	}

	public String getUsername() {
		return snomedStore.get("user").toString();
	}

	public String getPassword() {
		return snomedStore.get("password").toString();
	}

}

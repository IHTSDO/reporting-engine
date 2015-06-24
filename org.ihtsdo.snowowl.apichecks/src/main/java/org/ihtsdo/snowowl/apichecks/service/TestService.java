package org.ihtsdo.snowowl.apichecks.service;

import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.eventbus.IEventBus;

import java.util.HashMap;
import java.util.Map;

public class TestService {

	public Map<Class, Boolean> checkServicesAvailable() {
		Map<Class, Boolean> services = new HashMap<>();
		checkService(services, IEventBus.class);
		return services;
	}

	private void checkService(Map<Class, Boolean> services, Class serviceInterface) {
		services.put(serviceInterface, ApplicationContext.getInstance().getServiceChecked(serviceInterface) != null);
	}

}

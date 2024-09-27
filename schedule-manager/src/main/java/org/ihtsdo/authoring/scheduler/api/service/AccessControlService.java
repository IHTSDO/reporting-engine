package org.ihtsdo.authoring.scheduler.api.service;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.time.DateUtils;
import org.ihtsdo.otf.rest.client.authoringservices.AuthoringServicesClient;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service	
public class AccessControlService {

	private static final Logger LOGGER = LoggerFactory.getLogger(AccessControlService.class);

	private static final int CACHE_TIMEOUT_MINS = 30;

	private Map<String, UserProjects> cache = new HashMap<>();

	private AuthoringServicesClient authoringServices;

	public Set<String> getProjects(String username, String serverUrl, String authToken) {
		if (!cache.containsKey(username) || cache.get(username).isExpired()) {
			UserProjects userProjects = getUserProjects(serverUrl, authToken);
			LOGGER.info("Caching {}'s access to projects {}", username, userProjects.getProjects());
			cache.put(username, userProjects);
			//If the list of projects is only 1 long (ie MAIN) then we've probably a problem with 
			//configuration for that user.  Force expiry of the visible project list in this case
			if (userProjects.size() <= 1) {
				LOGGER.info("User has access to {} projects.  Suspected configuration issue.  Expiring cache.", userProjects.size());
				userProjects.expire();
			}
		}
		UserProjects userProjects = cache.get(username);
		return userProjects.getProjects();
	}

	public void clearCache(String username) {
		if (cache.containsKey(username)) {
			LOGGER.info("Clearing {}'s project access cache", username);
			cache.get(username).expire();
		}
	}

	//Synchronized because we don't want the authoring services client have its token updated
	//and then another call to come through from another user
	private synchronized UserProjects getUserProjects(String serverUrl, String authToken) {
		//We don't need the username passed in here, as authoring-services will only
		//tell us about projects that the user has access to.
		prepareAuthServices(serverUrl, authToken);
		List<Project> projects = authoringServices.listProjects();
		Set<String> projectStrs = projects.stream().map(Project::getKey)
				.collect(Collectors.toSet());
		//Add the branch paths of the code systems for each project, in line with UI behaviour
		projectStrs.addAll(getCodeSystemPaths(projects));
		//MAIN is not a project in Jira.  OK to add since MS generally doesn't use this
		projectStrs.add("MAIN");
		return new UserProjects(projectStrs);
	}

	private Set<String> getCodeSystemPaths(List<Project> projects) {
		return projects.stream()
				.map(p -> p.getCodeSystem().getBranchPath())
				.collect(Collectors.toSet());
	}

	private void prepareAuthServices(String serverUrl, String authToken) {
		//Is this our first call?  Create AuthServices if so, else update 
		if (authoringServices == null) {
			authoringServices = new AuthoringServicesClient(serverUrl, authToken);
		} else {
			authoringServices.updateAuthToken(authToken);
		}
	}

	public void clearAllCaches() {
		LOGGER.info("Clearing all project access caches");
		cache.clear();
	}

	private class UserProjects {
		Date created;
		Set<String> projects;
		
		public UserProjects(Set<String> projects) {
			this.projects = projects;
			created = new Date();
		}
		
		public int size() {
			return projects == null ? 0 : projects.size();
		}
		
		public Set<String> getProjects() {
			return projects;
		}
		
		public boolean isExpired() {
			return DateUtils.addMinutes(created, CACHE_TIMEOUT_MINS).before(new Date());
		}
		
		public void expire() {
			created = DateUtils.addMinutes(created, (-1 * CACHE_TIMEOUT_MINS));
		}
	}
}

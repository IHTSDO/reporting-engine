package org.ihtsdo.snowowl.authoring.single.api.service.eventhandler;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTask;
import org.ihtsdo.snowowl.authoring.single.api.pojo.EntityType;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.ihtsdo.snowowl.authoring.single.api.pojo.User;
import org.ihtsdo.snowowl.authoring.single.api.review.domain.Branch;
import org.ihtsdo.snowowl.authoring.single.api.review.domain.ReviewMessage;
import org.ihtsdo.snowowl.authoring.single.api.review.service.ReviewMessageSentListener;
import org.ihtsdo.snowowl.authoring.single.api.service.NotificationService;
import org.ihtsdo.snowowl.authoring.single.api.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

public class ReviewMessageSentHandler implements ReviewMessageSentListener {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private TaskService taskService;

	private Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public void messageSent(ReviewMessage message) {
		try {
			final List<String> usersToNotify = new ArrayList<>();
			final Branch branch = message.getBranch();
			final String project = branch.getProject();
			final String task = branch.getTask();
			final AuthoringTask authoringTask = taskService.retrieveTask(project, task);
			addIfNotNull(usersToNotify, authoringTask.getAssignee());
			addIfNotNull(usersToNotify, authoringTask.getReviewer());
			usersToNotify.remove(message.getFromUsername());
			logger.info("Feedback message for task {} notification to {}", task, usersToNotify);
			for (String username : usersToNotify) {
				notificationService.queueNotification(username, new Notification(project, task, EntityType.Feedback, "new"));
			}
		} catch (BusinessServiceException e) {
			logger.error("Failed to notify user of review feedback.", e);
		}
	}

	private void addIfNotNull(List<String> usersToNotify, User user) {
		if (user != null) {
			usersToNotify.add(user.getUsername());
		}
	}
}

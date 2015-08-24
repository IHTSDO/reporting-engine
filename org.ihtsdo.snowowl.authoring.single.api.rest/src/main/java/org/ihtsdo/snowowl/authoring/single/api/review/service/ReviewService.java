package org.ihtsdo.snowowl.authoring.single.api.review.service;

import com.b2international.snowowl.core.exceptions.BadRequestException;
import net.rcarz.jiraclient.JiraException;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.StateTransition;
import org.ihtsdo.snowowl.authoring.single.api.review.domain.Branch;
import org.ihtsdo.snowowl.authoring.single.api.review.domain.ReviewMessage;
import org.ihtsdo.snowowl.authoring.single.api.review.domain.ReviewMessageRead;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.AuthoringTaskReview;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ReviewConcept;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ReviewMessageCreateRequest;
import org.ihtsdo.snowowl.authoring.single.api.review.repository.BranchRepository;
import org.ihtsdo.snowowl.authoring.single.api.review.repository.ReviewMessageReadRepository;
import org.ihtsdo.snowowl.authoring.single.api.review.repository.ReviewMessageRepository;
import org.ihtsdo.snowowl.authoring.single.api.service.BranchService;
import org.ihtsdo.snowowl.authoring.single.api.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

import javax.transaction.Transactional;

@Service
public class ReviewService {

	@Autowired
	private ReviewMessageRepository messageRepository;

	@Autowired
	private ReviewMessageReadRepository messageReadRepository;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private BranchService branchService;
	
	@Autowired
	private TaskService taskService;

	@Autowired
	private ApplicationContext applicationContext;

	private Logger logger = LoggerFactory.getLogger(getClass());

	@Transactional
	public AuthoringTaskReview retrieveTaskReview(String projectKey, String taskKey, List<Locale> locales, String username) throws BusinessServiceException {
		try {
			final AuthoringTaskReview authoringTaskReview = branchService.diffTaskBranch(projectKey, taskKey, locales);
			final List<ReviewConcept> reviewConcepts = authoringTaskReview.getConcepts();
			final Branch branch = branchRepository.findOneByProjectAndTask(projectKey, taskKey);
			if (branch != null) {
				Map<String, List<ReviewMessage>> conceptMessagesMap = getConceptMessagesMap(branch);
				Map<String, List<ReviewMessage>> conceptMessagesReadMap = getMessagesReadMap(branch, username);
				for (ReviewConcept reviewConcept : reviewConcepts) {
					final String conceptId = reviewConcept.getId();
					final List<ReviewMessage> conceptMessages = conceptMessagesMap.get(conceptId);
					reviewConcept.setMessages(conceptMessages);
					reviewConcept.setRead(conceptMessages == null || conceptMessages.isEmpty() ||
							(conceptMessagesReadMap.containsKey(conceptId) && conceptMessagesReadMap.get(conceptId).containsAll(conceptMessages)));
				}
			}
			
			markConceptsModifiedSinceReview(projectKey, taskKey, reviewConcepts);
			return authoringTaskReview;
		} catch (ExecutionException|InterruptedException|JiraException e) {
			throw new BusinessServiceException ("Unable to generate task review.", e);
		}
	}

	private void markConceptsModifiedSinceReview(String projectKey,
			String taskKey, List<ReviewConcept> reviewConcepts) throws JiraException, BusinessServiceException {
		//Ensure that we're currently in state 'In Review' and find out from Jira when task entered that state
		if (!taskService.taskIsState(projectKey, taskKey, StateTransition.STATE_IN_REVIEW)) {
			logger.error("Unable to mark concepts modified since review as task not currently in state 'In Review'." );
			return;
		}
		
		Date reviewStated = taskService.getDateOfChange(projectKey, taskKey, "Status", StateTransition.STATE_IN_REVIEW);
		if (!taskService.taskIsState(projectKey, taskKey, StateTransition.STATE_IN_REVIEW)) {
			logger.error("Unable to mark concepts modified since review as cannot determine review start date." );
			return;
		}
	}

	/**
	 * Builds a map of conceptIds and messages with that concept in the subject.
	 * @param branch
	 * @return
	 */
	private Map<String, List<ReviewMessage>> getConceptMessagesMap(Branch branch) {
		final List<ReviewMessage> reviewMessages = messageRepository.findByBranch(branch);
		Map<String, List<ReviewMessage>> conceptMessagesMap = new HashMap<>();
		for (ReviewMessage reviewMessage : reviewMessages) {
			for (String conceptId : reviewMessage.getSubjectConceptIds()) {
				if (!conceptMessagesMap.containsKey(conceptId)) {
					conceptMessagesMap.put(conceptId, new ArrayList<ReviewMessage>());
				}
				conceptMessagesMap.get(conceptId).add(reviewMessage);
			}
		}
		return conceptMessagesMap;
	}

	/**
	 * Builds map where key is conceptId and value is a list of read messages for this user.
	 * @param branch
	 * @param username
	 * @return
	 */
	private Map<String, List<ReviewMessage>> getMessagesReadMap(Branch branch, String username) {
		Map<String, List<ReviewMessage>> conceptMessagesReadMap = new HashMap<>();
		final List<ReviewMessageRead> messageReadStatuses = messageReadRepository.findByReviewMessageBranchAndUser(branch, username);
		logger.info("messageReadStatuses {}", messageReadStatuses);
		for (ReviewMessageRead messageReadStatus : messageReadStatuses) {
			final String conceptId = messageReadStatus.getConceptId();
			if (!conceptMessagesReadMap.containsKey(conceptId)) {
				conceptMessagesReadMap.put(conceptId, new ArrayList<ReviewMessage>());
			}
			conceptMessagesReadMap.get(conceptId).add(messageReadStatus.getMessage());
		}
		return conceptMessagesReadMap;
	}

	public ReviewMessage postReviewMessage(String projectKey, String taskKey, ReviewMessageCreateRequest createRequest, String fromUsername) {
		final List<String> subjectConceptIds = createRequest.getSubjectConceptIds();
		if (subjectConceptIds == null || subjectConceptIds.isEmpty()) {
			throw new BadRequestException("There must be at least one id in subjectConceptIds");
		}
		final Branch branch = getCreateBranch(projectKey, taskKey);
		final ReviewMessage message = messageRepository.save(
				new ReviewMessage(branch, createRequest.getMessageHtml(),
						subjectConceptIds, createRequest.isFeedbackRequested(), fromUsername));
		for (ReviewMessageSentListener listener : getReviewMessageSentListeners()) {
			listener.messageSent(message);
		}
		return message;
	}

	public void markAsRead(String projectKey, String taskKey, String conceptId, String username) {
		final Branch branch = branchRepository.findOneByProjectAndTask(projectKey, taskKey);
		final List<ReviewMessage> messages = messageRepository.findByBranchAndConcept(branch, conceptId);
		for (ReviewMessage message : messages) {
			if (messageReadRepository.findOneByMessageAndConceptIdAndUsername(message, conceptId, username) == null) {
				messageReadRepository.save(new ReviewMessageRead(message, conceptId, username));
			}
		}
	}

	@Transactional
	public boolean anyUnreadMessages(String projectKey, String taskKey, String username) {
		logger.info("anyUnreadMessages {}, {}, {}", projectKey, taskKey, username);
		final Branch branch = branchRepository.findOneByProjectAndTask(projectKey, taskKey);
		if (branch != null) {
			final List<ReviewMessage> messages = messageRepository.findByBranch(branch);
			logger.info("anyUnreadMessages messages {}", messages);
			for (ReviewMessageRead messageRead : messageReadRepository.findByReviewMessageBranchAndUser(branch, username)) {
				logger.info("anyUnreadMessages read {}", messageRead.getMessage().getId());
				messages.remove(messageRead.getMessage());
			}
			logger.info("anyUnreadMessages unread {}", messages);
//			return messageRepository.anyUnreadMessages(branch, username);
			return !messages.isEmpty();
		}
		return false;
	}

	private Branch getCreateBranch(String projectKey, String taskKey) {
		Branch branch = branchRepository.findOneByProjectAndTask(projectKey, taskKey);
		if (branch == null) {
			branch = branchRepository.save(new Branch(projectKey, taskKey));
		}
		return branch;
	}

	/**
	 * Autowire ReviewMessageSentListener beans
	 * @return
	 */
	private Collection<ReviewMessageSentListener> getReviewMessageSentListeners() {
		return applicationContext.getBeansOfType(ReviewMessageSentListener.class).values();
	}
}

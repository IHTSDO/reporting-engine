package org.ihtsdo.snowowl.authoring.single.api.review.service;

import com.b2international.commons.http.ExtendedLocale;
import com.b2international.snowowl.core.exceptions.BadRequestException;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
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
import org.ihtsdo.snowowl.authoring.single.api.service.TaskStatus;
import org.ihtsdo.snowowl.authoring.single.api.service.dao.CdoStore;
import org.ihtsdo.snowowl.authoring.single.api.service.util.TimerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
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
	
	@Autowired
	private CdoStore cdoStore;

	private ExecutorService executorService;

	private Logger logger = LoggerFactory.getLogger(getClass());

	// TODO WRP-1032 Investigate with B2i why refsets are showing as being modified.
	private static final String TEMPORARY_CONCEPT_REMOVAL = "(foundation metadata concept)";

	public ReviewService() {
		executorService = Executors.newCachedThreadPool();
	}

	@Transactional
	public AuthoringTaskReview retrieveTaskReview(String projectKey, String taskKey, List<ExtendedLocale> locales, String username) throws BusinessServiceException {
		try {
			TimerUtil timer = new TimerUtil("Retrieve task review");

			// Kick off collecting review changes in a separate thread so the branch diff runs in parallel
			Future<Set<String>> currentReviewConceptChanges = getCurrentReviewConceptChangesOrNullIfNotInReview(projectKey, taskKey, false);

			final AuthoringTaskReview authoringTaskReview = branchService.diffTaskAgainstProject(projectKey, taskKey, locales);
			timer.checkpoint("diff task against project");

			final List<ReviewConcept> reviewConcepts = authoringTaskReview.getConcepts();
			removeRefsets(reviewConcepts);
			putReviewMessagesAgainstConcepts(projectKey, taskKey, username, reviewConcepts);
			markConceptsModifiedSinceReview(reviewConcepts, currentReviewConceptChanges.get());
			timer.finish();
			return authoringTaskReview;
		} catch (ExecutionException | InterruptedException | JiraException | SQLException e) {
			throw new BusinessServiceException ("Unable to generate task review.", e);
		}
	}

	@Transactional
	public AuthoringTaskReview retrieveProjectReview(String projectKey, List<ExtendedLocale> locales, String username) throws BusinessServiceException {
		try {
			Future<Set<String>> currentReviewConceptChanges = getCurrentReviewConceptChangesOrNullIfNotInReview(projectKey, null, true);

			final AuthoringTaskReview authoringTaskReview = branchService.diffProjectAgainstMain(projectKey, locales);
			final List<ReviewConcept> reviewConcepts = authoringTaskReview.getConcepts();
			removeRefsets(reviewConcepts);
			putReviewMessagesAgainstConcepts(projectKey, null, username, reviewConcepts);
			markConceptsModifiedSinceReview(reviewConcepts, currentReviewConceptChanges.get());
			return authoringTaskReview;
		} catch (ExecutionException | InterruptedException | JiraException | SQLException e) {
			throw new BusinessServiceException("Unable to generate project review.", e);
		} catch (BusinessServiceException e) {
			throw new BusinessServiceException("Unable to generate project review. Failed to load project ticket.", e);
		}
	}

	private void putReviewMessagesAgainstConcepts(String projectKey, String taskKey, String username, List<ReviewConcept> reviewConcepts) {
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
	}

	private void markConceptsModifiedSinceReview(List<ReviewConcept> reviewConcepts, Set<String> currentReviewConceptChanges) {
		if (currentReviewConceptChanges != null) {
			//Now loop through all our review concepts and see if any of them need to be marked as changed
			for (ReviewConcept thisConcept : reviewConcepts) {
				if (currentReviewConceptChanges.contains(thisConcept.getId())) {
					thisConcept.setModifiedSinceReview(true);
				}
			}
		}
	}
	private Future<Set<String>> getCurrentReviewConceptChangesOrNullIfNotInReview(final String projectKey, final String taskKeyParam, final boolean projectTicket) throws JiraException, BusinessServiceException, SQLException {
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return executorService.submit(new Callable<Set<String>>() {
			@Override
			public Set<String> call() throws Exception {
				SecurityContextHolder.getContext().setAuthentication(authentication);

				String taskKey = taskKeyParam;
				if (projectTicket) {
					taskKey = taskService.getProjectTicket(projectKey).getKey();
				}

				//Ensure that we're currently in state 'In Review' and find out from Jira when task entered that state
				if (!taskService.taskIsState(projectKey, taskKey, TaskStatus.IN_REVIEW)) {
					logger.warn("Unable to mark concepts modified since review as task not currently in state 'In Review'." );
					return null;
				}

				Date currentReviewStartDate = taskService.getDateOfChange(projectKey, taskKey, "status", TaskStatus.IN_REVIEW.getLabel());
				if (currentReviewStartDate == null) {
					logger.error("Unable to mark concepts modified since review as cannot determine review start date." );
					return null;
				}

				String parentBranchName = projectKey;
				String childBranchKey = taskKey;
				if (projectTicket) {
					parentBranchName = "MAIN";
					childBranchKey = projectKey;
				}
				Integer branchId = cdoStore.getBranchId(parentBranchName, childBranchKey);
				if (branchId == null) {
					logger.error("Unable to mark concepts modified since review as cannot determine branch Id." );
					return null;
				}
				return cdoStore.getConceptChanges(branchId, currentReviewStartDate);
			}
		});
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
	public TaskMessagesStatus getTaskMessagesStatus(String projectKey, String taskKey, String username) {
		final Branch branch = branchRepository.findOneByProjectAndTask(projectKey, taskKey);
		if (branch != null) {
			final List<ReviewMessage> messages = messageRepository.findByBranch(branch);
			for (ReviewMessageRead messageRead : messageReadRepository.findByReviewMessageBranchAndUser(branch, username)) {
				messages.remove(messageRead.getMessage());
			}
			return messages.isEmpty() ? TaskMessagesStatus.read : TaskMessagesStatus.unread;
		}
		return TaskMessagesStatus.none;
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
	
	private void removeRefsets(List<ReviewConcept> concepts) {
		Iterator<ReviewConcept> i = concepts.iterator();
		while (i.hasNext()) {
			ReviewConcept c = i.next(); 
			if (c.getTerm().contains(TEMPORARY_CONCEPT_REMOVAL)) {
				i.remove();
				logger.warn("Removed concept " + c.getTerm() + " from review list.");
			}
		}
	}

}

package org.ihtsdo.snowowl.authoring.single.api;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.ConflictReport;
import org.ihtsdo.snowowl.authoring.single.api.pojo.MergeRequest;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.AuthoringTaskReview;
import org.ihtsdo.snowowl.authoring.single.api.review.service.ReviewService;
import org.ihtsdo.snowowl.authoring.single.api.service.BranchService;
import org.ihtsdo.snowowl.authoring.single.api.service.ServiceException;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.b2international.snowowl.datastore.server.branch.Branch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

@ComponentScan(basePackages = "org.ihtsdo.snowowl.authoring.single.api.review")
@SpringBootApplication
@Configuration
public class TestSpringConfiguration {

	/**
	 * Context Test Method
	 * TODO - not yet working
	 * @param args
	 */
	public static void main(String[] args) {
		final ApplicationContext applicationContext = new ClassPathXmlApplicationContext(new String [] {"review-context.xml"},
				new AnnotationConfigApplicationContext(TestSpringConfiguration.class));

		final ReviewService reviewService = applicationContext.getBean(ReviewService.class);
	}

	@Bean
	public BranchService getBranchService() {
		return new BranchService() {
			@Override
			public void createTaskBranchAndProjectBranchIfNeeded(String projectKey, String taskKey) throws ServiceException {

			}

			@Override
			public Branch.BranchState getBranchState(String project, String taskKey) throws ServiceException {
				return null;
			}

			@Override
			public AuthoringTaskReview diffTaskAgainstProject(String projectKey, String taskKey, List<Locale> locales) throws ExecutionException, InterruptedException {
				return null;
			}

			@Override
			public String getTaskPath(String projectKey, String taskKey) {
				return null;
			}

			@Override
			public String getProjectPath(String projectKey) {
				return null;
			}

			@Override
			public ConflictReport retrieveConflictReport(String projectKey,
					String taskKey, ArrayList<Locale> list)
					throws BusinessServiceException {
				return null;
			}

			@Override
			public Branch rebaseTask(String projectKey, String taskKey,
					MergeRequest mergeRequest, String username) {
				return null;
			}

			@Override
			public AuthoringTaskReview diffProjectAgainstTask(
					String projectKey, String taskKey, List<Locale> locales)
					throws ExecutionException, InterruptedException {
				return null;
			}

			@Override
			public AuthoringTaskReview diffProjectAgainstMain(String projectKey, List<Locale> locales) throws ExecutionException, InterruptedException {
				return null;
			}

			@Override
			public Branch promoteTask(String projectKey, String taskKey,
					MergeRequest mergeRequest, String username)
					throws BusinessServiceException {
				return null;
			}

			@Override
			public ConflictReport retrieveConflictReport(String projectKey,
					ArrayList<Locale> list) {
				return null;
			}

			@Override
			public void rebaseProject(String projectKey,
					MergeRequest mergeRequest, String username)
					throws BusinessServiceException {
				return;
			}


			@Override
			public void promoteProject(String projectKey,
					MergeRequest mergeRequest, String username)
					throws BusinessServiceException {
				return;
			}
		};
	}

}

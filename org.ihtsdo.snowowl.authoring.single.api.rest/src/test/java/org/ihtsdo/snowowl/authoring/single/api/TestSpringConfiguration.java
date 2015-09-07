package org.ihtsdo.snowowl.authoring.single.api;

import org.ihtsdo.snowowl.authoring.single.api.review.service.ReviewService;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ClassPathXmlApplicationContext;

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

}

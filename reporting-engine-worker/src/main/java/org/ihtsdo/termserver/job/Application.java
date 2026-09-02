package org.ihtsdo.termserver.job;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;
import org.springframework.jms.annotation.EnableJms;

@SpringBootApplication
@ImportResource("classpath:services-context.xml")
@ComponentScan(basePackages = { "org.ihtsdo.termserver.job",
								"org.ihtsdo.termserver.scripting",
								"org.snomed.otf.scheduler.domain",
								"org.snomed.otf.script"})
@EnableJms
public class Application  {

	static TermServerScript job;

	public static void main(String[] args) {
		new SpringApplicationBuilder(Application.class)
		.web(WebApplicationType.NONE) // .REACTIVE, .SERVLET
		.run(args);
	}
	
}

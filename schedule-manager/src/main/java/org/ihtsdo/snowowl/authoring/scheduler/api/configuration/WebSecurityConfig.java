package org.ihtsdo.snowowl.authoring.scheduler.api.configuration;

import org.ihtsdo.snowowl.authoring.scheduler.api.security.RequestHeaderAuthenticationDecorator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

	@Value("${ims-security.required-role}")
	private String requiredRole;

	@Value("${authentication.override.username}")
	private String overrideUsername;

	@Value("${authentication.override.roles}")
	private String overrideRoles;

	@Value("${authentication.override.token}")
	private String overrideToken;

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http
				.csrf().disable();

		http.addFilterBefore(new RequestHeaderAuthenticationDecorator(overrideUsername, overrideRoles, overrideToken), FilterSecurityInterceptor.class);

		if (requiredRole != null && !requiredRole.isEmpty()) {
			if (requiredRole.startsWith("ROLE_")) {
				requiredRole = requiredRole.replace("ROLE_", "");
			}
			http
					.authorizeRequests()
					.antMatchers(
							"/",
							"/version",
							"/ui-configuration",

							// Swagger API Docs:
							"/swagger-ui.html",
							"/v2/api-docs",
							"/authoring-services-websocket/**/*",
							"/swagger-resources",
							"/swagger-resources/**/*",
							"/webjars/springfox-swagger-ui/**/*").permitAll()

					.anyRequest().hasRole(requiredRole);// automatically adds "ROLE_" prefix
		}
	}

}

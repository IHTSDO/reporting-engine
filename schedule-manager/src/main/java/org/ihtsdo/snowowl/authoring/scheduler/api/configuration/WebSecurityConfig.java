package org.ihtsdo.snowowl.authoring.scheduler.api.configuration;

import org.ihtsdo.snowowl.authoring.scheduler.api.security.RequestHeaderAuthenticationDecoratorWithOverride;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {
	@Value("${ims-security.required-role}")
	private String requiredRole;

	@Value("${authentication.override.username}")
	private String overrideUsername;

	@Value("${authentication.override.roles}")
	private String overrideRoles;

	@Value("${authentication.override.token}")
	private String overrideToken;

	private final String[] excludedUrlPatterns = {
			"/",
			"/version",
			// Swagger API Docs:
			"/swagger-ui/**"
	};


	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf().disable();
		http.addFilterBefore(new RequestHeaderAuthenticationDecoratorWithOverride(overrideUsername, overrideRoles, overrideToken), FilterSecurityInterceptor.class);

		if (requiredRole != null && !requiredRole.isEmpty()) {
			http.authorizeRequests()
					.antMatchers(excludedUrlPatterns).permitAll()
					.anyRequest().hasAuthority(requiredRole)
					.and().httpBasic();
		} else {
			http.authorizeRequests()
					.antMatchers(excludedUrlPatterns).permitAll()
					.anyRequest().authenticated()
					.and().httpBasic();
		}
		return http.build();
	}

	public String getOverrideToken() {
		return overrideToken;
	}
	
	public String getOverrideUsername() {
		return overrideUsername;
	}

}

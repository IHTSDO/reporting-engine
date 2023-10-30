package org.ihtsdo.authoring.scheduler.api.configuration;

import com.google.common.base.Strings;
import org.ihtsdo.authoring.scheduler.api.security.RequestHeaderAuthenticationDecoratorWithOverride;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

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
		http.csrf(AbstractHttpConfigurer::disable);
		http.addFilterBefore(new RequestHeaderAuthenticationDecoratorWithOverride(overrideUsername, overrideRoles, overrideToken), AuthorizationFilter.class);

		if (!Strings.isNullOrEmpty(requiredRole)) {
			http.authorizeHttpRequests(c -> c
					.requestMatchers(excludedUrlPatterns).permitAll()
					.anyRequest().hasAuthority(requiredRole));
		} else {
			http.authorizeHttpRequests(c -> c
					.requestMatchers(excludedUrlPatterns).permitAll()
					.anyRequest().authenticated());
		}
		http.httpBasic(Customizer.withDefaults());
		return http.build();
	}

	public String getOverrideToken() {
		return overrideToken;
	}
	
	public String getOverrideUsername() {
		return overrideUsername;
	}

}

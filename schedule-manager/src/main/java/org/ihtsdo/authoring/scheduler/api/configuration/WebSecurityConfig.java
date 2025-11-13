package org.ihtsdo.authoring.scheduler.api.configuration;

import com.google.common.base.Strings;
import jakarta.servlet.http.HttpServletResponse;
import org.ihtsdo.authoring.scheduler.api.security.RequestHeaderAuthenticationDecoratorWithOverride;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
			"/swagger-ui/**",
			"/v3/api-docs/**"
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

		// Configure exception handling to prevent Basic Auth popup
		// Returns JSON response instead of triggering browser Basic Auth popup
		http.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, authException) -> {
					response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
					response.setContentType("application/json;charset=UTF-8");
					String message = authException.getMessage() != null ? authException.getMessage().replace("\"", "\\\"") : "Authentication required";
					response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}");
				})
		);
		return http.build();
	}

	public String getOverrideToken() {
		return overrideToken;
	}
	
	public String getOverrideUsername() {
		return overrideUsername;
	}

}

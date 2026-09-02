package org.ihtsdo.authoring.scheduler.api.configuration;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.UrlHandlerFilter;

@Configuration
public class WebConfiguration {

	private static final Logger LOGGER = LoggerFactory.getLogger(WebConfiguration.class);

	/**
	 * PIP-379 Trailing slashes are not matched by default. PathMatchConfigurer.setUseTrailingSlashMatch
	 * was removed in Spring Framework 7, so permissive matching is now restored by wrapping the request
	 * with the trailing slash stripped. Ordered ahead of the security filter chain so that the
	 * permitAll patterns in WebSecurityConfig also see the stripped path.
	 * <p>
	 * Every rewrite is logged at DEBUG so we can tell whether any client still sends trailing
	 * slashes, without the log volume that a slash-appending crawler would otherwise cause. To
	 * collect that evidence set:
	 *     logging.level.org.ihtsdo.authoring.scheduler.api.configuration=DEBUG
	 * If nothing appears over a release cycle, this whole bean can be deleted and the trailing
	 * slash URLs allowed to 404.
	 */
	@Bean
	FilterRegistrationBean<UrlHandlerFilter> urlHandlerFilterRegistration() {
		UrlHandlerFilter filter = UrlHandlerFilter
				.trailingSlashHandler("/**")
				.intercept(WebConfiguration::logTrailingSlashRequest)
				.wrapRequest()
				.build();
		FilterRegistrationBean<UrlHandlerFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 5);
		return registration;
	}

	private static void logTrailingSlashRequest(HttpServletRequest request) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Trailing slash rewritten: {} {} (from {}, user-agent {})",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"));
        }
    }
}

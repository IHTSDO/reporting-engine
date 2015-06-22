package org.ihtsdo.snowowl.api.rest.common.servlet;

import java.io.IOException;
import javax.servlet.*;

public class RetryOnErrorFilter implements Filter {

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {

	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
		filterChain.doFilter(servletRequest, servletResponse);
		ServletResponseWrapper servletResponseWrapper = new ServletResponseWrapper(servletResponse);
	}

	@Override
	public void destroy() {

	}
}

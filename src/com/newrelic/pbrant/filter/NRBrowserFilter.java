package com.newrelic.pbrant.filter;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.newrelic.api.agent.NewRelic;

/**
 * Servlet Filter implementation class NRBrowserFilter. This filter injects the
 * New Relic browser JavaScript into the page.
 * 
 */
public class NRBrowserFilter implements Filter {
	private static final Logger logger = Logger.getLogger(NRBrowserFilter.class.getName());
	private static final int flags = Pattern.CASE_INSENSITIVE
			| Pattern.MULTILINE;
	private static final String metaRegex = "<\\s*meta[^>]*>";
	private static final Pattern metaPattern = Pattern
			.compile(metaRegex, flags);
	private static final String headRegex = "<\\s*head[^>]*>";
	private static final Pattern headPattern = Pattern
			.compile(headRegex, flags);
	private static final String bodyRegex = "<\\s*body[^>]*>";
	private static final Pattern bodyPattern = Pattern
			.compile(bodyRegex, flags);
	private static final String scriptRegex = "<\\s*script[^>]*>";
	private static final Pattern scriptPattern = Pattern.compile(scriptRegex,
			flags);
	private static final String endBodyRegex = "</body[^>]*>";
	private static final Pattern endBodyPattern = Pattern.compile(endBodyRegex,
			flags);
	private static final String doctypeHtmlRegex = "\\A\\s*<!DOCTYPE\\s*html";
	private static final Pattern doctypeHtmlPattern = Pattern.compile(
			doctypeHtmlRegex, flags);
	private static final String htmlRegex = "\\A\\s*<\\s*html";
	private static final Pattern htmlPattern = Pattern
			.compile(htmlRegex, flags);
	
	private int numberRequestsProcessed = 0;
	private long currentIntervalStart = 0;
	private int maxRequestsPerSecond = 0;

	/**
	 * @see Filter#doFilter(ServletRequest, ServletResponse, FilterChain)
	 */
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {

		if (this.shouldSkipRequest(request) || this.isRequestThrottled()) {
			chain.doFilter(request, response);
			return;
		}

		CharResponseWrapper wrapper = new CharResponseWrapper(
				(HttpServletResponse) response);

		chain.doFilter(request, wrapper);

		try {
			String html = "";
			String contentType = wrapper.getContentType();
			if (contentType != null && contentType.contains("text/html")) {
				html = wrapper.toString();
			}

			if (this.shouldProcessResponse(html)) {
				PrintWriter out = response.getWriter();
				this.insertNewRelicJS(html, out);
				out.close();
			} else {
				logger.fine("New Relic Browser Filter skipping request because content type not text/html or content doesn't start with <html> or <!DOCTYPE html");
				if (wrapper.getContentLength() > 0) {
					response.setContentLength(wrapper.getContentLength());
				}
				wrapper.copyToResponse(response);
			}
		} catch (Exception e) {
			// if something goes wrong, try and put the original response back
			logger.warning("New Relic Browser Filter caught exception in URI: "
							+ ((HttpServletRequest) request).getRequestURI()
									.toLowerCase()
							+ " original message: "
							+ e.getMessage());
			if (wrapper.getContentLength() > 0) {
				response.setContentLength(wrapper.getContentLength());
			}
			wrapper.copyToResponse(response);
		}
	}

	// make sure the request is type text/html
	// and the response starts with <html> type content
	private boolean shouldProcessResponse(String html) {
		if (html == null || html.length() == 0) {
			// this means content type was not text/html
			return false;
		}

		Matcher docMatcher = doctypeHtmlPattern.matcher(html);
		if (docMatcher.find()) {
			return true;
		}
		Matcher htmlMatcher = htmlPattern.matcher(html);
		if (htmlMatcher.find()) {
			return true;
		}

		// this means we did not find the open html tag or doctype
		// at the start of the html
		return false;
	}

	private boolean shouldSkipRequest(ServletRequest req) {
		HttpServletRequest httpReq;
		if (req instanceof HttpServletRequest) {
			httpReq = (HttpServletRequest) req;
		} else {
			// only process http requests
			return true;
		}
		String uri = httpReq.getRequestURI().toLowerCase();
		if (uri.endsWith(".js")) {
			// skip javascript requests
			// CBA encodes js requests as text/html
			logger.fine("New Relic Browser Filter skipping request for .js URI: "
							+ uri);
			return true;
		}
		if (uri.endsWith(".png")) {
			logger.fine("New Relic Browser Filter skipping request for png URI: "
							+ uri);
			return true;
		}
		if (uri.endsWith(".jpg")) {
			logger.fine("New Relic Browser Filter skipping request for jpg URI: "
							+ uri);
			return true;
		}
		if (uri.endsWith(".jpeg")) {
			logger.fine("New Relic Browser Filter skipping request for jpeg URI: "
							+ uri);
			return true;
		}
		if (uri.endsWith(".gif")) {
			logger.fine("New Relic Browser Filter skipping request for gif URI: "
							+ uri);
			return true;
		}
		if (uri.endsWith(".css")) {
			logger.fine("New Relic Browser Filter skipping request for css URI: "
							+ uri);
			return true;
		}
		logger.finer("New Relic Browser Filter processing URI " + uri);
		return false;
	}

	private void insertNewRelicJS(String html, PrintWriter out) {
		String nrHeader = NewRelic.getBrowserTimingHeader();
		if (nrHeader == null || nrHeader.length() == 0) {
			out.write(html);
			return;
		}
		String footer = NewRelic.getBrowserTimingFooter();
		if (footer == null || footer.length() == 0) {
			out.write(html);
			return;
		}
		int headerIndex = findHeaderIndex(html);
		if (headerIndex == 0) {
			out.write(html);
			return;
		}
		int footerIndex = findFooterIndex(html);
		if (footerIndex == 0) {
			out.write(html);
			return;
		}
		
		logger.finer("New Relic Browser Filter inserting NRJS. Header at: " + headerIndex + " Footer at: " + footerIndex);
		
		String origSnippet = html.substring(0, headerIndex);
		out.write(origSnippet);
		out.write(nrHeader);
		out.write("\n");

		origSnippet = html.substring(headerIndex, footerIndex);
		out.write(origSnippet);
		out.write(footer);
		out.write("\n");

		origSnippet = html.substring(footerIndex);
		out.write(origSnippet);

	}
	
	private int findFooterIndex(String html) {
		Matcher endBody = endBodyPattern.matcher(html);
		if (endBody.find()) {
			return endBody.start();
		}
		return 0;
	}
	
	private int findHeaderIndex(String html) {
		int metaIndex = findMetaIndex(html, metaPattern);
		int scriptIndex = findScriptIndex(html);
		if (metaIndex > 0 || scriptIndex > 0) {
			if (scriptIndex == 0) {
				return metaIndex;
			}
			if (metaIndex == 0) {
				return scriptIndex;
			}
			return scriptIndex < metaIndex ? scriptIndex : metaIndex;
		} else {
			Matcher headMatcher = headPattern.matcher(html);
			if (headMatcher.find()) {
				return headMatcher.end();
			} else {
				Matcher bodyMatcher = bodyPattern.matcher(html);
				if (bodyMatcher.find()) {
					return bodyMatcher.start();
				}
			}
		}
		return 0;
	}

	private int findMetaIndex(String html, Pattern regex) {
		int index = 0;
		Matcher metaMatcher = regex.matcher(html);
		while (metaMatcher.find()) {
			index = metaMatcher.end();
		}
		return index;
	}

	/**
	 * Find the first script tag -- NR header should be before the first script
	 * tag Ran into a situation where javascript was opening a second window and
	 * adding meta headers
	 * 
	 * @param html
	 * @return index of where to insert our header
	 */
	private int findScriptIndex(String html) {
		int index = 0;
		Matcher matcher = scriptPattern.matcher(html);
		if (matcher.find()) {
			index = matcher.start();
		}
		return index;
	}

	private boolean isRequestThrottled() {
		if (maxRequestsPerSecond == 0) {
			return false;
		}
		return isRequestLimitExceeded(System.currentTimeMillis());
	}
	
	private synchronized boolean isRequestLimitExceeded(long currentTime) {
		if (currentTime - currentIntervalStart > 1000) {
			// new 1 second interval
			currentIntervalStart = currentTime;
			numberRequestsProcessed = 0;
			return false;
		}
		
		numberRequestsProcessed++;
		return numberRequestsProcessed >= maxRequestsPerSecond;
	}
	
	/**
	 * @see Filter#init(FilterConfig)
	 */
	public void init(FilterConfig fConfig) throws ServletException {
		Handler handlers[] = logger.getHandlers();
		if (handlers.length == 0) {
			try {
				String logFilename = fConfig.getInitParameter("LogFile");
				String logLevel =fConfig.getInitParameter("LogLevel");
				if (logFilename != null && logFilename.length() > 0 ) {
					FileHandler handler = new FileHandler(logFilename);
					handler.setFormatter(new SimpleFormatter());
					logger.addHandler(handler);
					logger.setLevel(Level.parse(logLevel));
				}
			} catch (SecurityException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		String configMaxRequestsPerSecond = fConfig.getInitParameter("MaxRequestsPerSecond");
		if (configMaxRequestsPerSecond != null && configMaxRequestsPerSecond.length() > 0) {
			maxRequestsPerSecond = Integer.parseInt(configMaxRequestsPerSecond);
		}
		logger.config("New Relic Browser Filter V20140820 initialized");
	}

	/**
	 * @see Filter#destroy()
	 */
	public void destroy() {
	}
}

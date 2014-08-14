package com.newrelic.pbrant.filter;

import java.io.IOException;
import java.io.PrintWriter;
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
	private FilterConfig filterConfig = null;
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

	/**
	 * @see Filter#doFilter(ServletRequest, ServletResponse, FilterChain)
	 */
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {

		if (this.shouldSkipRequest(request)) {
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
				html = this.insertHeader(html);
				html = this.insertFooter(html);

//				 Can't use string length for content length -- need to know
//				 encoding, double byte characters, etc.
//				 response.setContentLength(html.length());

//				System.out
//						.println("New Relic Browser Filter setting content length to: "
//								+ html.length()
//								+ " response buffer size = "
//								+ response.getBufferSize());

				PrintWriter out = response.getWriter();
				out.write(html);
				out.close();
			} else {
				System.out
						.println("New Relic Browser Filter skipping request because content type not text/html or content doesn't start with <html> or <!DOCTYPE html");
				if (wrapper.getContentLength() > 0) {
					response.setContentLength(wrapper.getContentLength());
				}
				wrapper.copyToResponse(response);
			}
		} catch (Exception e) {
			// if something goes wrong, try and put the original response back
			System.out
					.println("New Relic Browser Filter caught exception in URI: "
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
			System.out
					.println("New Relic Browser Filter skipping request for .js URI: "
							+ uri);
			return true;
		}
		if (uri.endsWith(".png")) {
			System.out
					.println("New Relic Browser Filter skipping request for png URI: "
							+ uri);
			return true;
		}
		if (uri.endsWith(".jpg")) {
			System.out
					.println("New Relic Browser Filter skipping request for jpg URI: "
							+ uri);
			return true;
		}
		if (uri.endsWith(".jpeg")) {
			System.out
					.println("New Relic Browser Filter skipping request for jpeg URI: "
							+ uri);
			return true;
		}
		if (uri.endsWith(".gif")) {
			System.out
					.println("New Relic Browser Filter skipping request for gif URI: "
							+ uri);
			return true;
		}
		if (uri.endsWith(".css")) {
			System.out
					.println("New Relic Browser Filter skipping request for css URI: "
							+ uri);
			return true;
		}
		return false;
	}

	private String insertFooter(String html) {
		Matcher endBody = endBodyPattern.matcher(html);
		if (endBody.find()) {
			System.out
					.println("New Relic Browser Filter found the end body tag at index: "
							+ endBody.start());
			return endBody.replaceAll(com.newrelic.api.agent.NewRelic
					.getBrowserTimingFooter() + "\n</body>");
		} else {
			// if no body tag then add footer at bottom with </body and </html>
			// return html + "\n" + NewRelic.getBrowserTimingFooter()
			// + "\n</body>\n</html>";
			throw new IllegalArgumentException(
					"New Relic Browser Filter could NOT find where to insert New Relic Footer (no end body tag)");
		}
	}

	private String insertHeader(String html) {
		int index = findHeaderIndex(html);

		StringBuffer outputHtml = new StringBuffer(html.length() + 10000);
		outputHtml.append(html.substring(0, index));
		String nrHeader = NewRelic.getBrowserTimingHeader();
		System.out.println("New Relic Browser filter adding header of length: "
				+ nrHeader.length());
		outputHtml.append(nrHeader);
		outputHtml.append(html.substring(index));
		return outputHtml.toString();
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
		throw new IllegalArgumentException(
				"Couldn't find where to put the header");
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

	/**
	 * @see Filter#init(FilterConfig)
	 */
	public void init(FilterConfig fConfig) throws ServletException {
		this.filterConfig = fConfig;
		System.out.println("New Relic Browser Filter V20140814 initialized");
	}

	/**
	 * @see Filter#destroy()
	 */
	public void destroy() {
		this.filterConfig = null;
	}
}

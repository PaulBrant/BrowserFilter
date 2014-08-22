package com.newrelic.pbrant.filter;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;

import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

public class CharResponseWrapper extends HttpServletResponseWrapper {
	private static final Logger logger = Logger.getLogger(NRBrowserFilter.class.getName());
	private CharArrayWriter outWriter;
	private MyServletOutputStream outStream;
	private int contentLength;

	public CharResponseWrapper(HttpServletResponse response) {
		super(response);
	}

	public String toString() {
		if (outWriter != null) {
			return outWriter.toString();
		} else if (outStream != null) {
			String encoding = this.getResponse().getCharacterEncoding();
			return outStream.getHtmlString(encoding);
		}
		return "";
	}
	
	public String getHtmlString(String encoding) {
		if (outWriter != null) {
			return this.toString();
		}
		if (outStream != null) {
			logger.fine("New Relic Browser Filter -- stream using character encoding: " + encoding);
			return outStream.getHtmlString(encoding);
		}
		return "";
	}

	
	@Override
	public void setContentLength(int len) {
		logger.fine("New Relic Browser Filter -- intercepting content length set: " + len);
		this.contentLength = len;
	}
	
	public int getContentLength() {
		return this.contentLength;
	}

	public PrintWriter getWriter() {
		if (outStream != null) {
			throw new IllegalStateException(
					"Attempted to get writer after obtaining ServletOutputStream");
		}
		if (outWriter == null) {
			outWriter = new CharArrayWriter(2048);
		}
		return new PrintWriter(outWriter);
	}

	public ServletOutputStream getOutputStream() {
		if (outWriter != null) {
			throw new IllegalStateException(
					"Attempted to get stream after getting Servlet writer");
		}
		if (outStream == null) {
			outStream = new MyServletOutputStream();
		}
		return outStream;
	}
	
	void copyToResponse(ServletResponse response) throws IOException {
		if (outWriter != null) {
			PrintWriter out = response.getWriter();
			out.write(outWriter.toString());
			out.close();
			return;
		}
		if (outStream != null) {
			ServletOutputStream out = response.getOutputStream();
			outStream.copyToStream(out);
			return;
		}
		logger.info("New Relic Browser Filter -- trying to copy original response but no writer or stream created");
	}
}

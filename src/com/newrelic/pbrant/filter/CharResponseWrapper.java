package com.newrelic.pbrant.filter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.logging.Logger;

import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

public class CharResponseWrapper extends HttpServletResponseWrapper {
	private static final Logger logger = Logger.getLogger(NRBrowserFilter.class.getName());
	private PrintWriter myWriter;
	private ByteArrayOutputStream buf;
	private OutputStreamWriter oStream;
	private MyServletOutputStream outStream;
	private int contentLength;
	private boolean gotStream = false;
	private boolean gotWriter = false;

	public CharResponseWrapper(HttpServletResponse response) {
		super(response);
	}

	public String toString() {
		if (outStream != null) {
			String encoding = this.getResponse().getCharacterEncoding();
			return outStream.getHtmlString(encoding);
		}
		if (buf != null) {
			try {
				oStream.close();
				return buf.toString(this.getResponse().getCharacterEncoding());
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage());
			}
		}
		return "";
	}
	
	public String getHtmlString(String encoding) {
		if (outStream != null) {
			return outStream.getHtmlString(encoding);
		}
		if (buf != null) {
			try {
				return buf.toString(encoding);
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e.getMessage());
			}
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
		if (gotStream) {
			throw new IllegalStateException(
					"Attempted to get writer after obtaining ServletOutputStream");
		}
		if (myWriter == null) {
			buf = new ByteArrayOutputStream();
			oStream = new OutputStreamWriter(buf, Charset.forName(this.getResponse().getCharacterEncoding()));
			myWriter = new PrintWriter(oStream, true);
			gotWriter = true;
		}
		return myWriter;
	}

	public ServletOutputStream getOutputStream() {
		if (gotWriter) {
			throw new IllegalStateException(
					"Attempted to get stream after getting Servlet writer");
		}
		if (outStream == null) {
			outStream = new MyServletOutputStream();
			gotStream = true;
		}
		return outStream;
	}
	
	void copyToResponse(ServletResponse response) throws IOException {
		if (outStream != null) {
			ServletOutputStream out = response.getOutputStream();
			outStream.copyToStream(out);
			return;
		}
		if (buf != null) {
			ServletOutputStream out = response.getOutputStream();
			out.write(buf.toByteArray());
		}
		logger.info("New Relic Browser Filter -- trying to copy original response but no writer or stream created");
	}
}

package com.newrelic.pbrant.filter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.servlet.ServletOutputStream;

public class MyServletOutputStream extends ServletOutputStream {
	ByteArrayOutputStream buf = new ByteArrayOutputStream();
	
	public MyServletOutputStream() {
		super();
	}

	@Override
	public void write(int b) throws IOException {
		buf.write(b);
	}

	public String toString() {
		return buf.toString();
	}
	
	public void copyToStream(ServletOutputStream out) throws IOException {
		byte bufBytes[] = buf.toByteArray();
		for (int i = 0; i < bufBytes.length; i++) {
			out.write(bufBytes[i]);
		}
	}

	public String getHtmlString(String encoding) {
		try {
			return new String(buf.toByteArray(), encoding);
		} catch (UnsupportedEncodingException e) {
			System.out.println("New Relic Browser Filter -- Unsupported Encoding: " + encoding);
			throw new RuntimeException(e.getMessage());
		}
	}
}

package org.liveSense.misc.log.webconsole;

import java.util.Date;

public class LogEntry {

	String logger = null;
	Date date = null;
	String level = null;
	String threadName = null;
	String thread = null;
	String message = null;
	String className = null;
	String fileName = null;
	String method = null;
	String exception = null;
	long line = 0L;
	/**
	 * @return the logger
	 */
	public String getLogger() {
		return logger;
	}
	/**
	 * @return the date
	 */
	public Date getDate() {
		return date;
	}
	/**
	 * @return the level
	 */
	public String getLevel() {
		return level;
	}
	/**
	 * @return the threadName
	 */
	public String getThreadName() {
		return threadName;
	}
	/**
	 * @return the thread
	 */
	public String getThread() {
		return thread;
	}
	/**
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}
	/**
	 * @return the className
	 */
	public String getClassName() {
		return className;
	}
	/**
	 * @return the fileName
	 */
	public String getFileName() {
		return fileName;
	}
	/**
	 * @return the method
	 */
	public String getMethod() {
		return method;
	}
	/**
	 * @return the exception
	 */
	public String getException() {
		return exception;
	}
	/**
	 * @return the line
	 */
	public long getLine() {
		return line;
	}
	/**
	 * @param logger the logger to set
	 */
	public void setLogger(String logger) {
		this.logger = logger;
	}
	/**
	 * @param date the date to set
	 */
	public void setDate(Date date) {
		this.date = date;
	}
	/**
	 * @param level the level to set
	 */
	public void setLevel(String level) {
		this.level = level;
	}
	/**
	 * @param threadName the threadName to set
	 */
	public void setThreadName(String threadName) {
		this.threadName = threadName;
	}
	/**
	 * @param thread the thread to set
	 */
	public void setThread(String thread) {
		this.thread = thread;
	}
	/**
	 * @param message the message to set
	 */
	public void setMessage(String message) {
		this.message = message;
	}
	/**
	 * @param className the className to set
	 */
	public void setClassName(String className) {
		this.className = className;
	}
	/**
	 * @param fileName the fileName to set
	 */
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	/**
	 * @param method the method to set
	 */
	public void setMethod(String method) {
		this.method = method;
	}
	/**
	 * @param exception the exception to set
	 */
	public void setException(String exception) {
		this.exception = exception;
	}
	/**
	 * @param line the line to set
	 */
	public void setLine(long line) {
		this.line = line;
	}

	public String toString() {
		return getDate()+" | "+getLevel()+" | "+getClassName()+"."+getMethod()+"("+getLine()+") | "+getMessage() + (getException() == null ? "" : "\n"+getException());
	}
	
	
	
}

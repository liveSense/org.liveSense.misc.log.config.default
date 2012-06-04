/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.liveSense.misc.log.webconsole;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.log4j.MDC;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import de.huxhorn.lilith.api.FileConstants;
import de.huxhorn.lilith.data.eventsource.EventWrapper;
import de.huxhorn.lilith.data.eventsource.SourceIdentifier;
import de.huxhorn.lilith.data.logging.LoggingEvent;
import de.huxhorn.lilith.data.logging.LoggingEvent.Level;
import de.huxhorn.lilith.engine.EventHandler;
import de.huxhorn.lilith.engine.EventSource;
import de.huxhorn.lilith.engine.LogFileFactory;
import de.huxhorn.lilith.engine.LoggingFileBufferFactory;
import de.huxhorn.lilith.engine.impl.EventSourceImpl;
import de.huxhorn.lilith.engine.impl.LogFileFactoryImpl;
import de.huxhorn.lilith.engine.impl.sourcemanager.SourceManagerImpl;
import de.huxhorn.lilith.engine.impl.sourceproducer.LoggingEventProtobufServerSocketEventSourceProducer;
import de.huxhorn.lilith.engine.xml.sourceproducer.LilithXmlMessageLoggingServerSocketEventSourceProducer;
import de.huxhorn.lilith.eventhandlers.FileDumpEventHandler;
import de.huxhorn.lilith.eventhandlers.FileSplitterEventHandler;
import de.huxhorn.lilith.logback.appender.ClassicMultiplexSocketAppender;
import de.huxhorn.lilith.logback.appender.ClassicXmlMultiplexSocketAppender;
import de.huxhorn.sulky.buffers.BlockingCircularBuffer;
import de.huxhorn.sulky.buffers.FileBuffer;
@Component(immediate=true, metatype=true)
@Service
@Properties(value={
		@Property(name="felix.webconsole.label", value="logviewer" ),
		@Property(name="felix.webconsole.title", value="liveSense Log viewer"),
		@Property(name="felix.webconsole.configprinter.modes", value="always"),
		@Property(name=LogWebConsolePlugin.PAR_TIMEOUT, intValue=LogWebConsolePlugin.DEFAULT_TIMEOUT)
})
public class LogWebConsolePlugin extends
        HttpServlet {


	  static final String ENCODING = "UTF-8";
	  static final int DEFAULT_TIMEOUT = 60*15;
	  static final  String PAR_TIMEOUT = "logview.timeout";
	  private int timeOut = DEFAULT_TIMEOUT;
	  
	  private static final String RECORD_END = "</record>";
	  private String partialEvent;
	  
	static Logger log = LoggerFactory.getLogger(LogWebConsolePlugin.class);
    private static final long serialVersionUID = 0;
    private transient ServiceRegistration service;
    
    
    private String getParameterValue(HttpServletRequest req, String key, String defaultValue) {
    	if (req.getParameter(key) == null ||  "".equals(req.getParameter(key))) {
    		return defaultValue;
    	} else {
    		return req.getParameter(key);
    	}
    }

	String slingHomePath = null;
	String logHomePath = null;
	//Thread logFileTailer;
	LogPollerThread logFileTailer = null;
	
    @Activate
    protected void activate(BundleContext context, ComponentContext comp) {
	      // determinate sling.home
			slingHomePath = context.getProperty("sling.home");
			if (StringUtils.isEmpty(slingHomePath)) {
				slingHomePath = System.getProperty("sling.home");
			}
			if (StringUtils.isEmpty(slingHomePath)) {
				slingHomePath = System.getProperty("user.dir")+"/sling";
			}

			logHomePath = context.getProperty("logs.home");
			if (StringUtils.isEmpty(logHomePath)) {
				logHomePath = System.getProperty("logs.home");
			}
			if (StringUtils.isEmpty(logHomePath)) {
				logHomePath = slingHomePath+"/logs";
			}
			log.info("Sling home: "+slingHomePath);
			log.info("Log home:"+logHomePath);
/*
			timeOut = PropertiesUtil.toInteger(comp.getProperties().get(PAR_TIMEOUT), DEFAULT_TIMEOUT);
			logFileTailer =  new LogPollerThread(logHomePath+"/session.xml");
			*/

			/*
			// Initialize a circular file logger buffer
			LogFileFactory loggingFileFactory = new LogFileFactoryImpl(new File(logHomePath+"/session"));
			BlockingCircularBuffer<EventWrapper<LoggingEvent>> loggingEventQueue = new BlockingCircularBuffer<EventWrapper<LoggingEvent>>(1000);
			
			Map<String, String> loggingMetaData = new HashMap<String, String>();
			loggingMetaData.put(FileConstants.CONTENT_TYPE_KEY, FileConstants.CONTENT_TYPE_VALUE_LOGGING);
			loggingMetaData.put(FileConstants.CONTENT_FORMAT_KEY, FileConstants.CONTENT_FORMAT_VALUE_PROTOBUF);
			loggingMetaData.put(FileConstants.COMPRESSION_KEY, FileConstants.COMPRESSION_VALUE_GZIP);

			SourceIdentifier globalSourceIdentifier = new SourceIdentifier("log", null);
			LoggingFileBufferFactory loggingFileBufferFactory = new LoggingFileBufferFactory(loggingFileFactory, loggingMetaData);
			FileDumpEventHandler<LoggingEvent> loggingFileDump = new FileDumpEventHandler<LoggingEvent>(globalSourceIdentifier, loggingFileBufferFactory);
			loggingFileDump.setEnabled(true);

			SourceManagerImpl<LoggingEvent> lsm = new SourceManagerImpl<LoggingEvent>(loggingEventQueue);
			EventSource<LoggingEvent> globalLoggingEventSource = new EventSourceImpl<LoggingEvent>(globalSourceIdentifier, loggingFileDump.getBuffer(), true);
			lsm.addSource(globalLoggingEventSource);
			*/
			
    }
    
    @Deactivate
    protected void deactivate() {
    		if (logFileTailer != null) logFileTailer.interrupt();
    }
    
    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
    throws ServletException, IOException {

    		final PrintWriter pw = response.getWriter();

    		// If the logfile tailer is not started, we start it
    		if (!logFileTailer.isAlive()) logFileTailer.start();
    		
        String  prefix = StringUtils.substringBefore(request.getRequestURI(), "/logviewer");
                
        String relName = StringUtils.substringAfter(request.getRequestURI(), "/logviewer");
        
        String urlPart = request.getRequestURL().toString();
		if (urlPart.endsWith("json.log")) {
	        response.setContentType("application/javascript");

	        int lastId = logFileTailer.getLastId();
	        LogEntry[] entries = logFileTailer.getEntriesFromId(getLastIdFromRequest(request));
	        putLastIdToResponse(response, logFileTailer.getLastId());

	        String uriPrefix = StringUtils.substringBefore(request.getRequestURI(), "/logviewer/json.log");

	        Gson gson = new Gson();
	        String json = gson.toJson(entries);
	        gson.toJson(entries, pw);
	        pw.println("");
			
		} else  if (urlPart.endsWith(".js")) {
    	        response.setContentType("application/javascript");
    	        InputStream is =  LogWebConsolePlugin.class.getResource(relName).openStream();
    	        IOUtils.copy(is, pw, "UTF-8");
    	        pw.println("");
    		} else if (urlPart.endsWith(".css")) {
    	        response.setContentType("text/css");
    	        InputStream is =  LogWebConsolePlugin.class.getResource(relName).openStream();
    	        IOUtils.copy(is, pw, "UTF-8");
    	        pw.println("");
    		} else if (urlPart.endsWith(".png")) {
    	        response.setContentType("image/png");
    	        InputStream is =  LogWebConsolePlugin.class.getResource(relName).openStream();
    	        IOUtils.copy(is, response.getOutputStream());
    	        pw.println("");
    		} else if (urlPart.endsWith("tail.log")) {
    		        response.setContentType("text/html; charset=utf-8");
    		        int lastId = logFileTailer.getLastId();
    		        LogEntry[] entries = logFileTailer.getEntriesFromId(getLastIdFromRequest(request));
        			putLastIdToResponse(response, lastId);
    		        putLastIdToResponse(response, logFileTailer.getLastId());
    		        printDatasHTML(entries, pw);
    		        pw.println("");
    		} else {
/*
    			String uriPrefix = StringUtils.substringBefore(request.getRequestURI(), "/logviewer");
    			pw.write("<iframe id='logEntries' style='width:100%;' src='"+uriPrefix+"/logviewer/entries.list'/>");
    	        pw.println(""); */
		    response.setContentType("text/html; charset=utf-8");

    			String uriPrefix = StringUtils.substringBefore(request.getRequestURI(), "/logviewer");
			    	        
    			response.setContentType("text/html");
    			pw.write("<link rel='stylesheet' href='"+uriPrefix+"/logviewer/livesenselog.css' type='text/css' />");
    			pw.write("<div id='logArea'>");
    			pw.write("<table id='logtable' class='nicetable ui-widget resizable'>");
    				pw.write("<thead><tr>");
    						pw.write("<th width='10%'>Time</th>");
       					pw.write("<th width='5%'>X</th>");
    						pw.write("<th width='10%'>Level</th>");
    						pw.write("<th width='75%'>Logger</th>");
//    						printDatas(entries, pw);
    						pw.write("</tr></thead><tbody id=\"logentryarea\" class=\"ui-widget-content\">");

       		pw.write("</tbody></table>");
			pw.write("</div>");
			pw.println("");
			pw.write("<script type='text/javascript' src='"+uriPrefix+"/logviewer/livesenselog.js'></script>");
    		}
    }

    public void printDatasHTML(LogEntry[] entries, PrintWriter pw) {
		SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");

		if (entries != null) for (LogEntry entry : entries) {
	    		UUID uid = UUID.randomUUID();
	    		if (entry.exception != null && !entry.exception.equals("")) {
	    			pw.write("<tr class=\"loglevel_"+entry.getLevel().toLowerCase()+"\" onclick=\"toggle_visibility('"+uid+"')\">");
	    		} else {
	    			pw.write("<tr class=\"loglevel_"+entry.getLevel().toLowerCase()+"\" >");
	    		}
			pw.write("<td class='logdate'>"+format.format(entry.getDate())+"</td>");
			if (entry.exception != null && !entry.exception.equals("")) {
				pw.write("<td class='exceptionbutton_available'>X</td>");
			} else {
				pw.write("<td class='exceptionbutton_notavailable'></td>");					
			}
			pw.write("<td class='loglevel'>"+entry.getLevel()+"</td>");
			pw.write("<td  class='logger'>"+entry.getLogger()+"<br/>"+entry.getClassName()+"."+entry.getMethod()+"("+entry.getLine()+")"+"</td>");
		
			pw.write("</tr>");
				if (entry.exception != null && !entry.exception.equals("")) {
					pw.write("<tr class=\"loglevel_"+entry.getLevel().toLowerCase()+"\" onclick=\"toggle_visibility('"+uid+"')\">");
				} else {
					pw.write("<tr class=\"loglevel_"+entry.getLevel().toLowerCase()+"\" >");
				}
		
			pw.write("<td></td><td  class='logmessage' colspan=\"3\"'>"+entry.getMessage()+"</td>");
			
			pw.write("</tr>");
			if (entry.exception != null && !entry.exception.equals("")) {
				pw.write("<tr id='"+uid.toString()+"' class='logexception_row' style='display:none'><td colspan=\"4\">"+entry.getException().replaceAll("(\r\n|\n)", "<br />")+"</td></tr>");
			}
		}	
    }
    
    
    private int getLastIdFromRequest(HttpServletRequest req) {
        // Check if user have a last counter 
        for (Cookie cookie : req.getCookies()) {
        		if (cookie.getName().equals("livesenselogviewerlasid")) {
        			try {
        				return Integer.parseInt(cookie.getValue());
        			} catch (Throwable e) {
        			}
        		}
        }
        return -1;
    }
    
    private void putLastIdToResponse(HttpServletResponse resp, int lastId) {
    		Cookie cookie = new Cookie("livesenselogviewerlasid", Integer.toString(lastId));
    		cookie.setMaxAge(timeOut);
    		resp.addCookie(cookie);
    }

    /*
	private void setLoggingEventSourceManager(SourceManager<LoggingEvent> loggingEventSourceManager)
	{
		if(lsm != null)
		{
			lsm.removeEventSourceListener(loggingSourceListener);
		}
		lsm = loggingEventSourceManager;
		if(lsm != null)
		{
			lsm.addEventSourceListener(loggingSourceListener);

			List<EventSource<LoggingEvent>> sources = lsm.getSources();
			for(EventSource<LoggingEvent> source : sources)
			{
				//loggingEventViewManager.retrieveViewContainer(source);
			}
		}
	}

*/
    
    
public static void main(String[] args) throws IOException {
	// Initialize a circular file logger buffer
	LogFileFactory loggingFileFactory = new LogFileFactoryImpl(new File("target"));
	BlockingCircularBuffer<EventWrapper<LoggingEvent>> loggingEventQueue = new BlockingCircularBuffer<EventWrapper<LoggingEvent>>(1000);
	
	Map<String, String> loggingMetaData = new HashMap<String, String>();
	loggingMetaData.put(FileConstants.CONTENT_TYPE_KEY, FileConstants.CONTENT_TYPE_VALUE_LOGGING);
	loggingMetaData.put(FileConstants.CONTENT_FORMAT_KEY, FileConstants.CONTENT_FORMAT_VALUE_PROTOBUF);
	loggingMetaData.put(FileConstants.COMPRESSION_KEY, FileConstants.COMPRESSION_VALUE_GZIP);

	SourceIdentifier globalSourceIdentifier = new SourceIdentifier("log", null);
	LoggingFileBufferFactory loggingFileBufferFactory = new LoggingFileBufferFactory(loggingFileFactory, loggingMetaData);

	FileDumpEventHandler<LoggingEvent> loggingFileDump = new FileDumpEventHandler<LoggingEvent>(globalSourceIdentifier, loggingFileBufferFactory);
	FileBuffer<EventWrapper<LoggingEvent>> loggingFileBuffer = (FileBuffer<EventWrapper<LoggingEvent>>) loggingFileDump.getBuffer();
	loggingFileBuffer.reset();

	SourceManagerImpl<LoggingEvent> lsm = new SourceManagerImpl<LoggingEvent>(loggingEventQueue);
	EventSource<LoggingEvent> globalLoggingEventSource = new EventSourceImpl<LoggingEvent>(globalSourceIdentifier, loggingFileDump.getBuffer(), true);

	List<EventHandler<LoggingEvent>> loggingHandlers = new ArrayList<EventHandler<LoggingEvent>>();

	UserFileSplitterEventHandler<LoggingEvent> fileSplitterLoggingEventHandler =
			new UserFileSplitterEventHandler<LoggingEvent>(loggingFileBufferFactory, lsm);

	loggingHandlers.add(loggingFileDump);
	loggingHandlers.add(fileSplitterLoggingEventHandler);
	lsm.addSource(globalLoggingEventSource);
	lsm.setEventHandlers(loggingHandlers);

	LoggingEventProtobufServerSocketEventSourceProducer producer = new LoggingEventProtobufServerSocketEventSourceProducer (ClassicMultiplexSocketAppender.COMPRESSED_DEFAULT_PORT, true);
	lsm.addEventSourceProducer(producer);
	lsm.start();
	
	new Thread() {
		Logger log = LoggerFactory.getLogger("TEST");
		public void run() {
			ArrayList<EventWrapper<LoggingEvent>> entries = new ArrayList<EventWrapper<LoggingEvent>>();
			for (int i = 0; i<10; i++) {
				MDC.put("userId", "USER-"+Integer.toString(i%100));
				log.error("Teszt "+i, new Exception("Heeeeee"));
				MDC.remove("userId");
			}
			
			try {
				Thread.sleep(4000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			for (int i = 0; i<10; i++) {
				MDC.put("userId", "USER-"+Integer.toString(i%100));
				log.error("Teszt-2- "+i, new Exception("Heeeeee 2"));
				MDC.remove("userId");
			}

		};
	}.start();

	new Thread() {
		public void run() {
			final Logger log = LoggerFactory.getLogger("TEST2");
			
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				log.error("",e);
			}
			log.info("INITING THREAD 2");

				LogFileFactory loggingFileFactory = new LogFileFactoryImpl(new File("target"));
				BlockingCircularBuffer<EventWrapper<LoggingEvent>> loggingEventQueue = new BlockingCircularBuffer<EventWrapper<LoggingEvent>>(1000);
				
				Map<String, String> loggingMetaData = new HashMap<String, String>();
				loggingMetaData.put(FileConstants.CONTENT_TYPE_KEY, FileConstants.CONTENT_TYPE_VALUE_LOGGING);
				loggingMetaData.put(FileConstants.CONTENT_FORMAT_KEY, FileConstants.CONTENT_FORMAT_VALUE_PROTOBUF);
				loggingMetaData.put(FileConstants.COMPRESSION_KEY, FileConstants.COMPRESSION_VALUE_GZIP);
	
				SourceIdentifier globalSourceIdentifier = new SourceIdentifier("log", null);
				LoggingFileBufferFactory loggingFileBufferFactory = new LoggingFileBufferFactory(loggingFileFactory, loggingMetaData);
	
				FileDumpEventHandler<LoggingEvent> loggingFileDump = new FileDumpEventHandler<LoggingEvent>(globalSourceIdentifier, loggingFileBufferFactory);
				loggingFileDump.setEnabled(true);
//				FileBuffer<EventWrapper<LoggingEvent>> loggingFileBuffer = (FileBuffer<EventWrapper<LoggingEvent>>) loggingFileDump.getBuffer();

				SourceManagerImpl<LoggingEvent> lsm = new SourceManagerImpl<LoggingEvent>(loggingEventQueue);
				EventSource<LoggingEvent> globalLoggingEventSource = new EventSourceImpl<LoggingEvent>(globalSourceIdentifier, loggingFileDump.getBuffer(), true);

				List<EventHandler<LoggingEvent>> loggingHandlers = new ArrayList<EventHandler<LoggingEvent>>();
				loggingHandlers.add(loggingFileDump);
				loggingHandlers.add(new EventHandler<LoggingEvent>() {

					public void handle(List<EventWrapper<LoggingEvent>> events) {
						log.debug("Event handler run");
						for (EventWrapper<LoggingEvent> ev : events) {
							log.debug(" >> RECEIVE: ------> "+ev.toString());
						}
					}
				});
				lsm.addSource(globalLoggingEventSource);
				lsm.setEventHandlers(loggingHandlers);
				lsm.start();
				
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				for (EventWrapper<LoggingEvent> ev : loggingEventQueue) {
					log.info(ev.toString());
				}
			}			
		}.start();
	};
}

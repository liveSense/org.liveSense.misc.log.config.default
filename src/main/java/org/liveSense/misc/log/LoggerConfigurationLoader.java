package org.liveSense.misc.log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.vfs.FileChangeEvent;
import org.apache.commons.vfs.FileListener;
import org.apache.commons.vfs.FileMonitor;
import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.FileSystemManager;
import org.apache.commons.vfs.VFS;
import org.apache.commons.vfs.impl.DefaultFileMonitor;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Service;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.util.StatusPrinter;

@Component(immediate=true, metatype=false, policy=ConfigurationPolicy.OPTIONAL)
public class LoggerConfigurationLoader {

	static Logger log = LoggerFactory.getLogger(LoggerConfigurationLoader.class);
    LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
    JoranConfigurator configurator = new JoranConfigurator();

	FileSystemManager fsManager = null;
	DefaultFileMonitor logMonitor;
	

	@Activate
	protected void activate(BundleContext context) {
		// assume SLF4J is bound to logback in the current environment
	    
	    try {
	      configurator.setContext(lc);

	      
	      // determinate sling.home
			String slingHomePath = context.getProperty("sling.home");
			if (StringUtils.isEmpty(slingHomePath)) {
				slingHomePath = System.getProperty("sling.home");
			}
			if (StringUtils.isEmpty(slingHomePath)) {
				slingHomePath = System.getProperty("user.dir")+"/sling";
			}

			String logHomePath = context.getProperty("logs.home");
			if (StringUtils.isEmpty(logHomePath)) {
				logHomePath = System.getProperty("logs.home");
			}
			if (StringUtils.isEmpty(logHomePath)) {
				logHomePath = slingHomePath+"/logs";
			}
			log.info("Sling home: "+slingHomePath);
			log.info("Log home:"+logHomePath);

			File logsHome = new File(logHomePath);
			if (!logsHome.isDirectory()) {
				if (!logsHome.mkdirs()) {
					log.error(
							"verifyConfiguration: Cannot create logs home {}, failed creating default configuration ",
							logsHome.getAbsolutePath());
				}
			}
			final File logFile = new File(slingHomePath+"/logback.xml");

			
			    // the context was probably already configured by default configuration 
			    // rules
				fsManager = VFS.getManager();
				FileObject listendir = fsManager.resolveFile(logFile.getParentFile().getAbsolutePath());
				logMonitor = new DefaultFileMonitor(new FileListener() {
					
					public void fileDeleted(FileChangeEvent arg0) throws Exception {
						// The logger config have to reset to default - recreate file
						if (arg0.getFile().getName().getPath().equals(logFile.getAbsolutePath())) {
							log.info("Log configuration deleted, restore default - "+arg0.getFile().getName());
							IOUtils.copyLarge(LoggerConfigurationLoader.class.getClassLoader().getResourceAsStream("logback.xml"), new FileOutputStream(logFile));
							lc.reset();
							configurator.doConfigure(new FileInputStream(logFile));
							StatusPrinter.printInCaseOfErrorsOrWarnings(lc);
						}
					}
					
					public void fileCreated(FileChangeEvent arg0) throws Exception {
						if (arg0.getFile().getName().getPath().equals(logFile.getAbsolutePath())) {
							log.info("Log configuration created, loading - "+arg0.getFile().getName());
							lc.reset(); 
							configurator.doConfigure(new FileInputStream(logFile));
							StatusPrinter.printInCaseOfErrorsOrWarnings(lc);
						}
					}
					
					public void fileChanged(FileChangeEvent arg0) throws Exception {
						if (arg0.getFile().getName().getPath().equals(logFile.getAbsolutePath())) {
							log.info("Log configuration changed, loading - "+arg0.getFile().getName());
							lc.reset(); 
							configurator.doConfigure(new FileInputStream(logFile));
							StatusPrinter.printInCaseOfErrorsOrWarnings(lc);
						}
					}
				});
				logMonitor.addFile(listendir);
				logMonitor.setRecursive(false);
				logMonitor.start();
				 
				// If config does not exists we create it
				if (!logFile.exists()) {
					IOUtils.copyLarge(LoggerConfigurationLoader.class.getClassLoader().getResourceAsStream("logback.xml"), new FileOutputStream(logFile));
				} else {
					log.info("Log configuration - "+logFile.getAbsolutePath());
					lc.reset(); 
					configurator.doConfigure(new FileInputStream(logFile));
					StatusPrinter.printInCaseOfErrorsOrWarnings(lc);
				}

	    } catch (Throwable e) {
	    		log.error("Coluld not load config",e);
	    }
	}
	
	@Deactivate
	protected void deactivate() {
		if (logMonitor != null) logMonitor.stop();
		logMonitor = null;
	}
}

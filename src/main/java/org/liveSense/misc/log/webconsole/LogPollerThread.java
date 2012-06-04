package org.liveSense.misc.log.webconsole;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LogPollerThread extends Thread {
	Logger log = LoggerFactory.getLogger(LogPollerThread.class);
    BufferedReader input = null;
    XMLLogReader logReader = null;
	LineNumberReader reader;
	InputStream inputStream = null;
	CircularArrayList<LogEntry> logBuffer = new CircularArrayList<LogEntry>(LogEntry.class, 10000);
	String logPath = null;
	String encoding = "UTF-8";
	
	public LogPollerThread(String logPath, String encoding) {
		super();
		this.logPath = logPath;
		this.encoding = encoding;
	}

	public LogPollerThread(String logPath) {
		this(logPath, "UTF-8");
	}

    @Override
    public void interrupt() {
    		super.interrupt();
		if (input != null) {
			try {
				input.close();
			} catch (Exception e2) {
			}
		}
    }

    private void openLogFile() throws MalformedURLException, IOException {
		boolean isZipFile = logPath.toLowerCase().endsWith(".zip");

		if (isZipFile) {
			inputStream = new ZipInputStream(new FileInputStream(logPath));
			//move stream to next entry so we can read it
			((ZipInputStream) inputStream).getNextEntry();
		} else {
			inputStream = new FileInputStream(logPath);
		}
		reader = new LineNumberReader(new InputStreamReader(inputStream, encoding));

        input = new BufferedReader(reader);
        logReader = new XMLLogReader();
    }
    
    public void run() {
        while (!interrupted()) {
            try {
            		if (input == null) openLogFile();

            		String line = null;
				while (input != null && (line = input.readLine()) != null) {
					StringBuffer buffer = new StringBuffer(line);
					for (int i = 0; i < 1000; i++) {
						buffer.append(input.readLine()).append("\n");
					}
					CircularArrayList<LogEntry> events = logReader.decodeEvents(buffer.toString());
					if (events != null) {
						synchronized (logBuffer) {
							logBuffer.addAll(events);
						}
					}
				}
			} catch (FileNotFoundException e) {
				log.error("File not found: "+ logPath);
			} catch (MalformedURLException e) {
				log.error("Error", e);
			} catch (IOException e) {
				log.error("IOError", e);				
			}
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
                break;
			}
        }
    }
    
    public LogEntry[] getEntriesFromId(int id) {
    		synchronized (logBuffer) {
        		return logBuffer.getFromId(id);				
		}
    }
    
    public int getLastId() {
    		synchronized (logBuffer) {
        		return logBuffer.getLastId();			
		}
    }
}
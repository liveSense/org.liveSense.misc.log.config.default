package org.liveSense.misc.log.webconsole;

import java.io.StringReader;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class XMLLogReader {
	Logger log = LoggerFactory.getLogger(XMLLogReader.class);

	//NOTE: xml section is only handed on first delivery of events
	//on this first delivery of events, there is no end tag for the log element
	/**
	 * Document prolog.
	 */
	private static final String BEGIN_PART = "<log>";
	/**
	 * Document close.
	 */
	private static final String END_PART = "</log>";
	/**
	 * Document builder.
	 */
	private DocumentBuilder docBuilder;
	/**
	 * Partial event.
	 */
	private String partialEvent;
	/**
	 * Record end.
	 */
	private static final String RECORD_END = "</log4j:event>";

	private static final String ENCODING = "UTF-8";

	/**
	 * Create new instance.
	 */
	public XMLLogReader() {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setValidating(false);

		try {
			docBuilder = dbf.newDocumentBuilder();
			docBuilder.setErrorHandler(new ErrorHandler() {
				public void warning(SAXParseException arg0) throws SAXException {
					log.warn("Parse XML error", arg0);
				}

				public void fatalError(SAXParseException arg0) throws SAXException {
					log.error("Parse XML error", arg0);
				}

				public void error(SAXParseException arg0) throws SAXException {
					log.error("Parse XML error", arg0);
				}
			});
			docBuilder.setEntityResolver(new UtilLoggingEntityResolver());
		} catch (ParserConfigurationException pce) {
			System.err.println("Unable to get document builder");
		}
	}

	/**
	 * Converts the LoggingEvent data in XML string format into an actual
	 * XML Document class instance.
	 * @param data XML fragment
	 * @return  dom document
	 */
	private Document parse(final String data) {
		if (docBuilder == null || data == null) {
			return null;
		}

		Document document = null;

		try {
			/**
			 * resetting the length of the StringBuffer is dangerous, particularly
			 * on some JDK 1.4 impls, there's a known Bug that causes a memory leak
			 */
			StringBuffer buf = new StringBuffer(1024);

			if (!data.startsWith("<?xml")) {
				buf.append(BEGIN_PART);
			}

			buf.append(data);

			if (!data.endsWith(END_PART)) {
				buf.append(END_PART);
			}

			InputSource inputSource = new InputSource(new StringReader(buf.toString()));
			document = docBuilder.parse(inputSource);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return document;
	}

	/**
	 * Decodes a File into a CircularArrayList of LoggingEvents.
	 * @param url the url of a file containing events to decode
	 * @return CircularArrayList of LoggingEvents
	 * @throws IOException if IO error during processing.
	 */
	/*
	public CircularArrayList<LogEntry> decode(final URL url) throws IOException {
		LineNumberReader reader;
		boolean isZipFile = url.getPath().toLowerCase().endsWith(".zip");
		InputStream inputStream;
		if (isZipFile) {
			inputStream = new ZipInputStream(url.openStream());
			//move stream to next entry so we can read it
			((ZipInputStream) inputStream).getNextEntry();
		} else {
			inputStream = url.openStream();
		}
		reader = new LineNumberReader(new InputStreamReader(inputStream, ENCODING));
		CircularArrayList<LogEntry> v = new CircularArrayList<LogEntry>();

		String line;
		CircularArrayList<LogEntry> events;
		try {
			while ((line = reader.readLine()) != null) {
				StringBuffer buffer = new StringBuffer(line);
				for (int i = 0; i < 1000; i++) {
					buffer.append(reader.readLine()).append("\n");
				}
				events = decodeEvents(buffer.toString());
				if (events != null) {
					v.addAll(events);
				}
			}
		} finally {
			partialEvent = null;
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return v;
	}
	*/

	/**
	 * Decodes a String representing a number of events into a
	 * CircularArrayList of LoggingEvents.
	 * @param document to decode events from
	 * @return CircularArrayList of LoggingEvents
	 */
	public CircularArrayList<LogEntry> decodeEvents(final String document) {

		if (document != null) {

			if (document.trim().equals("")) {
				return null;
			}

			String newDoc;
			String newPartialEvent = null;
			//separate the string into the last portion ending with </record>
			// (which will be processed) and the partial event which
			// will be combined and processed in the next section

			//if the document does not contain a record end,
			// append it to the partial event string
			if (document.lastIndexOf(RECORD_END) == -1) {
				partialEvent = partialEvent + document;
				return null;
			}

			if (document.lastIndexOf(RECORD_END) + RECORD_END.length() < document.length()) {
				newDoc = document.substring(0, document.lastIndexOf(RECORD_END) + RECORD_END.length());
				newPartialEvent = document.substring(document.lastIndexOf(RECORD_END) + RECORD_END.length());
			} else {
				newDoc = document;
			}
			if (partialEvent != null) {
				newDoc = partialEvent + newDoc;
			}
			partialEvent = newPartialEvent;

			Document doc = parse(newDoc);
			if (doc == null) {
				return null;
			}
			return decodeEvents(doc);
		}
		return null;
	}

	/**
	 * Given a Document, converts the XML into a CircularArrayList of LoggingEvents.
	 * @param document XML document
	 * @return CircularArrayList of LoggingEvents
	 */
	private CircularArrayList<LogEntry> decodeEvents(final Document document) {
		CircularArrayList<LogEntry> events = new CircularArrayList<LogEntry>(LogEntry.class);

		NodeList eventList = document.getChildNodes(); //document.getElementsByTagName("record");

		for (int eventIndex = 0; eventIndex < eventList.getLength(); eventIndex++) {
			Node eventNode = eventList.item(eventIndex);

			NodeList list = eventNode.getChildNodes();
			int listLength = list.getLength();

			if (listLength == 0) {
				continue;
			}

			for (int y = 0; y < listLength; y++) {
				Node node = list.item(y);

				if (node instanceof Element && node.hasAttributes()) {
					LogEntry entry = new LogEntry();
					if (events.size() > 1000) events.remove(0);
					events.add(entry);

					NamedNodeMap attrs = node.getAttributes();
					for (int i = 0; i < attrs.getLength(); i++) {
						Attr attribute = (Attr) attrs.item(i);

						if ("logger".equals(attribute.getName())) {
							entry.setLogger(attribute.getValue());
						} else if ("timestamp".equals(attribute.getName())) {
							entry.setDate(new Date(Long.parseLong(attribute.getValue())));
						} else if ("level".equals(attribute.getName())) {
							entry.setLevel(attribute.getValue());
						} else if ("thread".equals(attribute.getName())) {
							entry.setThread(attribute.getValue());
						} else {
							log.error("Unknown attribute: " + attribute.getName());
						}
					}
					
					if (node.hasChildNodes()) {
						NodeList childList = node.getChildNodes();

						for (int z = 0; z < childList.getLength(); z++) {
							Node childNode = childList.item(z);
							if (childNode instanceof Element) {
								if ("log4j:message".equals(childNode.getNodeName())) {
									entry.setMessage(getCData(childNode));
								} else if ("log4j:throwable".equals(childNode.getNodeName())) {
									entry.setException(getCData(childNode));
								} else if ("log4j:locationInfo".equals(childNode.getNodeName())) {
									NamedNodeMap childAttrs = childNode.getAttributes();
									for (int w = 0; w < childAttrs.getLength(); w++) {
										Attr childAttribute = (Attr) childAttrs.item(w);

										if ("class".equals(childAttribute.getName())) {
											entry.setClassName(childAttribute.getValue());
										} else if ("line".equals(childAttribute.getName())) {
											entry.setLine(Long.parseLong(childAttribute.getValue()));
										} else if ("file".equals(childAttribute.getName())) {
											entry.setFileName(childAttribute.getValue());
										} else if ("method".equals(childAttribute.getName())) {
											entry.setMethod(childAttribute.getValue());
										} else {
											log.error("Unknown attribute: " + childNode.getNodeName() + " >" + childAttribute.getName());
										}
									}
		    				        } else {
						        		log.error("Unknown node: "+childNode.getNodeName());
		    				        }
							}
						}
					}
				}
			}
		}
		return events;
	}

	/**
	 * Get contents of CDATASection.
	 * @param n CDATASection
	 * @return text content of all text or CDATA children of node.
	 */
	private String getCData(final Node n) {
		StringBuffer buf = new StringBuffer();
		NodeList nl = n.getChildNodes();

		for (int x = 0; x < nl.getLength(); x++) {
			Node innerNode = nl.item(x);

			if ((innerNode.getNodeType() == Node.TEXT_NODE) || (innerNode.getNodeType() == Node.CDATA_SECTION_NODE)) {
				buf.append(innerNode.getNodeValue());
			}
		}

		return buf.toString();
	}
}
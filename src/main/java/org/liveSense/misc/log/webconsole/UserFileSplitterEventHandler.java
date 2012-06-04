/*
 * Lilith - a log event viewer.
 * Copyright (C) 2007-2011 Joern Huxhorn
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.liveSense.misc.log.webconsole;

import de.huxhorn.lilith.data.eventsource.EventWrapper;
import de.huxhorn.lilith.data.eventsource.SourceIdentifier;
import de.huxhorn.lilith.data.logging.LoggingEvent;
import de.huxhorn.lilith.engine.EventHandler;
import de.huxhorn.lilith.engine.FileBufferFactory;
import de.huxhorn.lilith.engine.SourceManager;
import de.huxhorn.lilith.engine.impl.EventSourceImpl;
import de.huxhorn.sulky.buffers.FileBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class UserFileSplitterEventHandler<T extends Serializable>
	implements EventHandler<T>
{
	private final Logger logger = LoggerFactory.getLogger(UserFileSplitterEventHandler.class);

	private FileBufferFactory<T> fileBufferFactory;
	private ConcurrentMap<SourceIdentifier, FileBuffer<EventWrapper<T>>> fileBuffers;
	private SourceManager<T> sourceManager;

	public UserFileSplitterEventHandler(FileBufferFactory<T> fileBufferFactory, SourceManager<T> sourceManager)
	{
		this.fileBufferFactory = fileBufferFactory;
		fileBuffers = new ConcurrentHashMap<SourceIdentifier, FileBuffer<EventWrapper<T>>>();
		this.sourceManager = sourceManager;
	}

	public void handle(List<EventWrapper<T>> events)
	{
		if(events != null && events.size() > 0)
		{
			Map<SourceIdentifier, List<EventWrapper<T>>> splittedEvents = new HashMap<SourceIdentifier, List<EventWrapper<T>>>();
			for(EventWrapper<T> wrapper : events)
			{
				SourceIdentifier si = wrapper.getSourceIdentifier();
				//SourceIdentifier si = new SourceIdentifier("TESZT", wrapper.getSourceIdentifier().getSecondaryIdentifier());
				if (wrapper.getEvent() instanceof LoggingEvent) {
					LoggingEvent ev = (LoggingEvent) wrapper.getEvent();
					String appId = ev.getLoggerContext().getProperties().get("applicationIdentifier");
					if (appId != null) {
						si.setIdentifier(appId);
					}
					if (ev.getMdc() != null && ev.getMdc().get("userId") != null) {
						si.setIdentifier(ev.getMdc().get("userId"));
					}
				}
				List<EventWrapper<T>> sourceList = splittedEvents.get(si);
				if(sourceList == null)
				{
					sourceList = new ArrayList<EventWrapper<T>>();
					splittedEvents.put(si, sourceList);
				}
				sourceList.add(wrapper);
			}
			if(logger.isInfoEnabled())
			{
				logger.info("Split {} events to {} sources.", events.size(), splittedEvents.size());
			}
			for(Map.Entry<SourceIdentifier, List<EventWrapper<T>>> entry : splittedEvents.entrySet())
			{
				SourceIdentifier si = entry.getKey();
				List<EventWrapper<T>> value = entry.getValue();
				int valueCount = value.size();
				// we know that valueCount is > 0 because otherwise it wouldn't exist.
				EventWrapper<T> lastEvent = value.get(valueCount - 1);
				boolean close=false;
				boolean dontOpen=false;
				if(lastEvent.getEvent() == null)
				{
					close=true;
					if(valueCount == 1)
					{
						dontOpen=true;
					}
				}
				// only create view/add if valid
				if(!dontOpen)
				{
					// resolveBuffer is also creating the view
					FileBuffer<EventWrapper<T>> buffer = resolveBuffer(si);
					buffer.addAll(value);
					if(logger.isInfoEnabled()) logger.info("Wrote {} events for source '{}'.", valueCount, si);
				}

				if(close)
				{
					if(sourceManager != null)
					{
						sourceManager.removeSource(si);
					}

					File activeFile = fileBufferFactory.getLogFileFactory().getActiveFile(si);
					activeFile.delete();
					fileBuffers.remove(si);
				}
			}
		}
	}

	private FileBuffer<EventWrapper<T>> resolveBuffer(SourceIdentifier si)
	{
		FileBuffer<EventWrapper<T>> result = fileBuffers.get(si);
		if(result == null)
		{
			result = fileBufferFactory.createActiveBuffer(si);
			FileBuffer<EventWrapper<T>> contained = fileBuffers.putIfAbsent(si, result);
			if(contained != null)
			{
				result = contained;
			}
			else if(sourceManager != null)
			{
				sourceManager.addSource(new EventSourceImpl<T>(si, result, false));
			}
		}
		return result;
	}
}

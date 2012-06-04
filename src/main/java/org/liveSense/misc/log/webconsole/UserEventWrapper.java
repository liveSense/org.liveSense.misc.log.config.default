package org.liveSense.misc.log.webconsole;

import java.io.Serializable;

import de.huxhorn.lilith.data.eventsource.EventWrapper;
import de.huxhorn.lilith.data.eventsource.SourceIdentifier;
import de.huxhorn.lilith.data.logging.LoggingEvent;

@SuppressWarnings("serial")
public class UserEventWrapper<T extends Serializable> extends EventWrapper<T> implements Serializable {
	@Override
	public SourceIdentifier getSourceIdentifier() {
		return new SourceIdentifier("TESZT", super.getSourceIdentifier().getSecondaryIdentifier());
	}
}

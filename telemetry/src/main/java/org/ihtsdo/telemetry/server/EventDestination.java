package org.ihtsdo.telemetry.server;

import javax.jms.TextMessage;

public interface EventDestination {

	void receiveMessage(TextMessage message) throws DestinationException;

}

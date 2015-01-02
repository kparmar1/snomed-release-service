package org.ihtsdo.telemetry.server;

import org.ihtsdo.telemetry.core.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.listener.SessionAwareMessageListener;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

public class TelemetryProcessor implements SessionAwareMessageListener {

	private final Map<String, BufferedWriter> streamWriters;
	private Logger logger = LoggerFactory.getLogger(TelemetryProcessor.class);
	private final StreamFactory streamFactory;

	private List<EventDestination> eventDestinations;

	@Autowired
	public TelemetryProcessor(final StreamFactory streamFactory, final List<EventDestination> eventDestinations) throws JMSException {
		this.streamFactory = streamFactory;
		this.eventDestinations = eventDestinations;
		streamWriters = new HashMap<>();
	}

	@Override
	public void onMessage(Message messageIn, Session session) throws JMSException {
		if (messageIn instanceof TextMessage) {
			TextMessage message = (TextMessage) messageIn;
			try {
				logger.debug("Got message '{}', correlationID {}", message.getText(), message.getJMSCorrelationID());
				String text = message.getText();
				String correlationID = message.getJMSCorrelationID();
				if (correlationID != null) {
					if (Constants.START_STREAM.equals(text)) {
						// Start new stream
						String streamUri = message.getStringProperty(Constants.STREAM_URI);
						BufferedWriter streamWriter = streamFactory.createStreamWriter(correlationID, streamUri);
						streamWriters.put(correlationID, streamWriter);
					} else if (Constants.FINISH_STREAM.equals(text)) {
						BufferedWriter writer = streamWriters.get(correlationID);
						if (writer != null) {
							writer.close();
							streamWriters.remove(correlationID);
						} else {
							logger.error("Attempting to close stream but no open stream for correlationID {}", correlationID);
						}
					} else {
						BufferedWriter writer = streamWriters.get(correlationID);
						if (writer != null) {
							writer.write(text);
							String exception = message.getStringProperty(Constants.EXCEPTION);
							if (exception != null) {
								writer.write(exception);
								writer.write(Constants.LINE_BREAK);
							}
						} else {
							logger.error("Attempting to write to stream but no open stream for correlationID {}", correlationID);
						}
					}
				}
			} catch (IOException e) {
				logger.error("Failed to handle message.", e);
			}

			for (EventDestination eventDestination : eventDestinations) {
				if (eventDestination != null) {
					try {
						eventDestination.receiveMessage(message);
					} catch (DestinationException e) {
						logger.error("EventDestination failed.", e);
					}
				}
			}
		} else {
			throw new IllegalArgumentException("Message must be of type TextMessage");
		}
	}

//	public void startup() {
//
//		Thread messageConsumerThread = new Thread(new Runnable() {
//			@Override
//			public void run() {
//				boolean printedWaiting = false;
//				logger.info("Telemetry server starting up.");
//				while (!shutdown) {
//					try {
//						if (!printedWaiting) {
//							logger.debug("Waiting for message");
//							printedWaiting = true;
//						}
//						TextMessage message = (TextMessage) consumer.receive(ONE_SECOND);
//
//						if (message != null) {
//							consumeMessage(message);
//							printedWaiting = false;
//						}
//					} catch (IllegalStateException e) {
//						logger.info("Connection closed. Shutting down telemetry consumer.");
//						shutdown = true;
//					} catch (JMSException e) {
//						Exception linkedException = e.getLinkedException();
//						if (linkedException != null && linkedException.getClass().equals(TransportDisposedIOException.class)) {
//							logger.info("Transport disposed. Shutting down telemetry consumer.");
//							shutdown = true;
//						} else {
//							logger.error("JMSException", e);
//						}
//					} catch (IOException e) {
//						logger.error("Problem with output writer.", e);
//					}
//				}
//			}
//
//		});
//		messageConsumerThread.start();
//	}


//	public void shutdown() throws InterruptedException, JMSException {
//		this.shutdown = true;
//		consumer.close();
//		this.jmsSession.close();
//	}
}

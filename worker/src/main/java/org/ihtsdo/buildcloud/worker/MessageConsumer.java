package org.ihtsdo.buildcloud.worker;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectReader;
import org.ihtsdo.buildcloud.entity.Build;
import org.ihtsdo.buildcloud.service.BuildService;
import org.ihtsdo.buildcloud.service.exception.BusinessServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.listener.SessionAwareMessageListener;

import java.io.IOException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

public class MessageConsumer implements SessionAwareMessageListener {

	@Autowired
	private BuildService buildService;

	private final ObjectReader buildReader;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public MessageConsumer() {
		buildReader = new ObjectMapper().reader(Build.class);
	}

	@Override
	public void onMessage(Message message, Session session) throws JMSException {
		if (message instanceof TextMessage) {
			TextMessage textMessage = (TextMessage) message;
			try {
				Build build = buildReader.readValue(textMessage.getText());
				try {
					triggerQueuedBuild(build);
				} catch (BusinessServiceException e) {
					logger.error("Build failure in worker. BuildID: " + build.getId(), e);
				}
			} catch (IOException e) {
				logger.error("Worker failed to deserialize queued build message.", e);
			}
		} else {
			throw new IllegalArgumentException("Message must be of type TextMessage, received " + message.getClass());
		}
	}

	public void triggerQueuedBuild(Build build) throws BusinessServiceException {
		buildService.triggerBuild(build);
	}

}

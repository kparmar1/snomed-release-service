package org.ihtsdo.telemetry.server;

import org.apache.commons.mail.EmailException;
import org.ihtsdo.commons.email.EmailRequest;
import org.ihtsdo.commons.email.EmailSender;
import org.ihtsdo.telemetry.core.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import javax.jms.JMSException;
import javax.jms.TextMessage;

public class EmailEventDestination implements EventDestination {

	private String defaultEmailToAddr;
	private String emailFromAddr;
	private EmailSender emailSender;

	private Logger logger = LoggerFactory.getLogger(EmailEventDestination.class);

	public EmailEventDestination(String defaultEmailToAddr, String emailFromAddr, EmailSender emailSender) {
		this.defaultEmailToAddr = defaultEmailToAddr;
		this.emailFromAddr = emailFromAddr;
		this.emailSender = emailSender;
	}

	@Override
	public void receiveMessage(TextMessage message) throws DestinationException {
		// As well as logging the message and even if we're outside of an event stream, if an exception is detected then
		// route it to email.. this will be done via configuration in the future.
		try {
			String level = message.getStringProperty("level");
			if (level != null && (level.equals("ERROR") || level.equals("FATAL"))) {
				// Do we have an EmailSender configured?
				if (emailSender == null || defaultEmailToAddr == null
						|| defaultEmailToAddr.isEmpty()) {
					logger.info("EmailSender not configured.  Unable to report error message: " + message.getText());
					return;
				}
				EmailRequest emailRequest = new EmailRequest();
				emailRequest.setToEmail(defaultEmailToAddr);
				emailRequest.setFromEmail(emailFromAddr);
				// TODO Add this string via config.
				String subject = String.format("IHTSDO Telemetry - %s service error detected in %s.",
						message.getStringProperty(Constants.SERVICE), message.getStringProperty(Constants.ENVIRONMENT));
				emailRequest.setSubject(subject);
				String msg = message.getText();
				if (message.propertyExists(Constants.EXCEPTION)) {
					msg += "\n" + message.getStringProperty(Constants.EXCEPTION);
				}
				emailRequest.setTextBody("IHTSO Telemetry Server has received the following error message: " + msg);
				emailSender.send(emailRequest);
			}
		} catch (JMSException e) {
			throw new DestinationException("Failed to process message.", e);
		} catch (MalformedURLException | EmailException e) {
			throw new DestinationException("Failed to send email.", e);
		}
	}

}

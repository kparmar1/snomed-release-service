package org.ihtsdo.telemetry.server;

import org.ihtsdo.commons.email.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailEventDestinationFactory {

	private static Logger logger = LoggerFactory.getLogger(EmailEventDestinationFactory.class);

	public static EmailEventDestination create(String defaultEmailToAddr, String emailFromAddr, String smtpHost, Integer smtpPort,
			String smtpUsername, String smtpPassword, Boolean smtpSsl) {

		EmailEventDestination emailEventDestination;
		if (smtpHost != null && smtpUsername != null && defaultEmailToAddr != null) {
			EmailSender emailSender = new EmailSender(smtpHost, smtpPort, smtpUsername, smtpPassword, smtpSsl);
			emailEventDestination = new EmailEventDestination(defaultEmailToAddr, emailFromAddr, emailSender);
		} else {
			logger.info("Telemetry server has not been given SMTP connection details.  Email connectivity disabled.");
			emailEventDestination = null;
		}

		return emailEventDestination;
	}

}

package org.ihtsdo.authoring.scheduler.api.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ihtsdo.authoring.scheduler.api.service.ReportingService;
import org.snomed.otf.scheduler.domain.JobRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.ihtsdo.otf.exception.TermServerScriptException;

import jakarta.jms.TextMessage;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JobReceiver {

	private static final Logger LOGGER = LoggerFactory.getLogger(JobReceiver.class);

	private static final Pattern ID_PATTERN       = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\"");
	private static final Pattern JOB_NAME_PATTERN = Pattern.compile("\"jobName\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern USER_PATTERN     = Pattern.compile("\"user\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern STATUS_PATTERN   = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]+)\"");

	private static final int RAW_MESSAGE_EXCERPT_LENGTH = 500;

	@Autowired
	ReportingService service;

	@Autowired
	ObjectMapper objectMapper;

	@JmsListener(destination = "${reporting.service.queue.response}")
	public void receiveMessage(TextMessage rawMessage) throws TermServerScriptException {
		String messageText;
		try {
			messageText = rawMessage.getText();
		} catch (jakarta.jms.JMSException e) {
			throw new TermServerScriptException("RP-973 Failed to read JMS message text", e);
		}
		try {
			JobRun jobRun = objectMapper.readValue(messageText, JobRun.class);
			service.processResponse(jobRun);
		} catch (Exception parseException) {
			String diagnosticInfo = buildDiagnosticInfo(messageText, parseException);
			LOGGER.warn("RP-973 Failed to parse JobRun message. {}", diagnosticInfo);

			UUID id = extractUuid(messageText);
			if (id != null) {
				service.markJobRunFailed(id, diagnosticInfo);
			} else {
				// Cannot identify the job - send to DLQ so the raw message is preserved
				LOGGER.error("RP-973 Unable to extract job run ID - message will be sent to DLQ. Raw message: {}", messageText);
				throw new TermServerScriptException("RP-973 Unable to process malformed JobRun message and could not extract job ID", parseException);
			}
		}
	}

	private UUID extractUuid(String messageText) {
		try {
			Matcher matcher = ID_PATTERN.matcher(messageText);
			return matcher.find() ? UUID.fromString(matcher.group(1)) : null;
		} catch (Exception e) {
			LOGGER.error("RP-973 Regex UUID extraction failed unexpectedly", e);
			return null;
		}
	}

	private String buildDiagnosticInfo(String messageText, Exception parseException) {
		StringBuilder sb = new StringBuilder("MALFORMED RESPONSE: ");
		sb.append("Parse error: ").append(parseException.getMessage()).append(". ");
		appendExtracted(sb, "jobName", JOB_NAME_PATTERN, messageText);
		appendExtracted(sb, "user",    USER_PATTERN,     messageText);
		appendExtracted(sb, "status",  STATUS_PATTERN,   messageText);
		sb.append("Raw message excerpt: ");
		if (messageText != null && messageText.length() > RAW_MESSAGE_EXCERPT_LENGTH) {
			sb.append(messageText, 0, RAW_MESSAGE_EXCERPT_LENGTH).append("...[truncated]");
		} else {
			sb.append(messageText);
		}
		return sb.toString();
	}

	private void appendExtracted(StringBuilder sb, String fieldName, Pattern pattern, String text) {
		if (text == null) return;
		Matcher m = pattern.matcher(text);
		if (m.find()) {
			sb.append(fieldName).append("=").append(m.group(1)).append(", ");
		}
	}
}

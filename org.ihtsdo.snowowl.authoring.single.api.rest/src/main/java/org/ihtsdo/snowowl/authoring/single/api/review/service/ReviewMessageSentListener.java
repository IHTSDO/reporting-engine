package org.ihtsdo.snowowl.authoring.single.api.review.service;

import org.ihtsdo.snowowl.authoring.single.api.review.domain.ReviewMessage;

/**
 * Receive event notifications by declaring a bean which implements this method.
 */
public interface ReviewMessageSentListener  {
	void messageSent(ReviewMessage message);
}

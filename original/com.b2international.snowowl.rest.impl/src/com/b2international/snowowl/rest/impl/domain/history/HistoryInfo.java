/**
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowl.rest.impl.domain.history;

import java.util.List;

import com.b2international.snowowl.rest.domain.history.IHistoryInfo;
import com.b2international.snowowl.rest.domain.history.IHistoryInfoDetails;
import com.b2international.snowowl.rest.domain.history.IHistoryVersion;

/**
 * @author akitta
 *
 */
public class HistoryInfo implements IHistoryInfo {

	private final IHistoryVersion version;
	private final long timestamp;
	private final String author;
	private final String comments;
	private final List<IHistoryInfoDetails> details;

	/**
	 * @param version
	 * @param timestamp
	 * @param author
	 * @param comments
	 * @param details
	 */
	public HistoryInfo(final IHistoryVersion version, final long timestamp, final String author, final String comments, final List<IHistoryInfoDetails> details) {
		this.version = version;
		this.timestamp = timestamp;
		this.author = author;
		this.comments = comments;
		this.details = details;
	}

	/* (non-Javadoc)
	 * @see com.b2international.snowowl.rest.api.domain.IHistoryInfo#getVersion()
	 */
	@Override
	public IHistoryVersion getVersion() {
		return version;
	}

	/* (non-Javadoc)
	 * @see com.b2international.snowowl.rest.api.domain.IHistoryInfo#getTimestamp()
	 */
	@Override
	public long getTimestamp() {
		return timestamp;
	}

	/* (non-Javadoc)
	 * @see com.b2international.snowowl.rest.api.domain.IHistoryInfo#getAuthor()
	 */
	@Override
	public String getAuthor() {
		return author;
	}

	/* (non-Javadoc)
	 * @see com.b2international.snowowl.rest.api.domain.IHistoryInfo#getComments()
	 */
	@Override
	public String getComments() {
		return comments;
	}

	/* (non-Javadoc)
	 * @see com.b2international.snowowl.rest.api.domain.IHistoryInfo#getDetails()
	 */
	@Override
	public List<IHistoryInfoDetails> getDetails() {
		return details;
	}
}

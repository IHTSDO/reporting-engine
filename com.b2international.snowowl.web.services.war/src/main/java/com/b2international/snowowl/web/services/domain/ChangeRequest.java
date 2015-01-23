/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * @author apeteri
 */
public class ChangeRequest<T> {

	private T change;
	private String commitComment;

	@JsonUnwrapped
	public T getChange() {
		return change;
	}

	public String getCommitComment() {
		return commitComment;
	}

	public void setChange(final T change) {
		this.change = change;
	}

	public void setCommitComment(final String commitComment) {
		this.commitComment = commitComment;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("ChangeRequest [change=");
		builder.append(change);
		builder.append(", commitComment=");
		builder.append(commitComment);
		builder.append("]");
		return builder.toString();
	}
}

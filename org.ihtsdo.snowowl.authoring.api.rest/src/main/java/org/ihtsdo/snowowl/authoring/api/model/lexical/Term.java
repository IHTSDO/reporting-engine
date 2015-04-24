package org.ihtsdo.snowowl.authoring.api.model.lexical;

public class Term {

	private String prefix;
	private String postfix;
	private boolean firstLetterCaseSensitive;

	public Term() {
	}

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public String getPostfix() {
		return postfix;
	}

	public void setPostfix(String postfix) {
		this.postfix = postfix;
	}

	public boolean isFirstLetterCaseSensitive() {
		return firstLetterCaseSensitive;
	}

	public void setFirstLetterCaseSensitive(boolean firstLetterCaseSensitive) {
		this.firstLetterCaseSensitive = firstLetterCaseSensitive;
	}
}

package org.ihtsdo.snowowl.authoring.single.api.model.lexical;

public class Term {

	private String prefix;
	private String postfix;
	private boolean firstLetterCaseSensitive;

	public Term() {
	}

	public Term(String prefix, String postfix, boolean firstLetterCaseSensitive) {
		this.prefix = prefix;
		this.postfix = postfix;
		this.firstLetterCaseSensitive = firstLetterCaseSensitive;
	}

	public String buildTerm(String term) {
		return (prefix != null ? prefix : "") + term + (postfix != null ? postfix : "");
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

	@Override
	public String toString() {
		return "Term{" +
				"prefix='" + prefix + '\'' +
				", postfix='" + postfix + '\'' +
				", firstLetterCaseSensitive=" + firstLetterCaseSensitive +
				'}';
	}
}

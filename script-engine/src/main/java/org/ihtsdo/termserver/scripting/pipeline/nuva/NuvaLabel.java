package org.ihtsdo.termserver.scripting.pipeline.nuva;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Statement;

public class NuvaLabel {
	String value;
	String langCode;

	public NuvaLabel(Object value, String langCode) {
		this.value = value.toString();
		this.langCode = langCode;
	}

	public static NuvaLabel fromLiteral(Literal l) {
		return new NuvaLabel(l.getValue(), l.getLanguage());
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getLangCode() {
		return langCode;
	}

	public void setLangCode(String langCode) {
		this.langCode = langCode;
	}

	public boolean hasLanguage(String langCode) {
		return this.langCode.equals(langCode);
	}
}

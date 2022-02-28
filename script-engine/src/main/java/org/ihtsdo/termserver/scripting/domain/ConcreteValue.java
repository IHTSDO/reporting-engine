package org.ihtsdo.termserver.scripting.domain;

public class ConcreteValue {

	public enum ConcreteValueType { INTEGER, DECIMAL, STRING }
	
	private ConcreteValueType dataType;
	
	private String value;
	
	private String valueWithPrefix;

	public ConcreteValue(ConcreteValueType cvType, String value) {
		this.dataType = cvType;
		setValue(value);
	}

	public ConcreteValue(String decoratedStr) {
		this.valueWithPrefix = decoratedStr;
		if (valueWithPrefix.startsWith("#")) {
			value = valueWithPrefix.replace("#", "");
			dataType = ConcreteValueType.DECIMAL;
		} else if (valueWithPrefix.startsWith("\"")) {
			value = valueWithPrefix.replaceAll("\"", "");
			dataType = ConcreteValueType.STRING;
		} else {
			throw new IllegalArgumentException("Cannot determine datatype of Concrete Value '" + decoratedStr + "'");
		}
	}

	public ConcreteValueType getDataType() {
		return dataType;
	}

	public void setDataType(ConcreteValueType dataType) {
		this.dataType = dataType;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
		switch (dataType) {
			case STRING : valueWithPrefix = "\"" + value + "\"";
			default : valueWithPrefix =  "#" + value;
		}
	}

	public String getValueWithPrefix() {
		return valueWithPrefix;
	}

	public void setValueWithPrefix(String valueWithPrefix) {
		this.valueWithPrefix = valueWithPrefix;
	}
	
	public String valueAsRF2() {
		return valueWithPrefix;
	}
	
	public String toString() {
		return valueWithPrefix;
	}
}

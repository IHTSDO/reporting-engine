package org.ihtsdo.termserver.scripting.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.ihtsdo.termserver.scripting.domain.mrcm.MRCMAttributeRange;

public class ConcreteValue {

	public enum ConcreteValueType {
		@SerializedName("INTEGER") INTEGER,
		@SerializedName("DECIMAL") DECIMAL,
		@SerializedName("STRING") STRING,
		@SerializedName("NUMERIC") NUMERIC;

		public static ConcreteValueType getTypeForRange(MRCMAttributeRange range) {
			ConcreteValueType type = ConcreteValueType.DECIMAL;
			if (range != null && range.getField("rangeConstraint").contains("int")) {
				type = ConcreteValueType.INTEGER;
			}
			return type;
		}
	}

	@SerializedName("dataType")
	@Expose
	private ConcreteValueType dataType;

	@SerializedName("value")
	@Expose
	private String value;

	@SerializedName("valueWithPrefix")
	@Expose
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
			value = valueWithPrefix.replace("\"", "");
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
		if (dataType == ConcreteValueType.STRING) {
			valueWithPrefix = "\"" + value + "\"";
		} else {
			valueWithPrefix = "#" + value;
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
	
	@Override
	public boolean equals(Object other) {
		if (other instanceof ConcreteValue otherCV) {
			return this.valueWithPrefix.equals(otherCV.valueWithPrefix);
		}
		return false;
	}
}

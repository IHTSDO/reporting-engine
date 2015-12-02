package org.ihtsdo.snowowl.api.rest.common.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;

/**
/**
 * Carries Jackson annotations for object mapping.
 */
public interface ISnomedComponentMixin {

	@JsonIgnore
	String getId();

	@JsonFormat(shape=Shape.STRING, pattern="yyyyMMdd")
	Date getEffectiveTime();
}

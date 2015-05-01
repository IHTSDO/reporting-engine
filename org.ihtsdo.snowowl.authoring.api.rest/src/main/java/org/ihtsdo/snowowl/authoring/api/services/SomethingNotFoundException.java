package org.ihtsdo.snowowl.authoring.api.services;

import com.b2international.snowowl.api.exception.NotFoundException;

public class SomethingNotFoundException extends NotFoundException {
	public SomethingNotFoundException(String type, String key) {
		super(type, key);
	}
}

package org.ihtsdo.snowowl.api.rest.common;

import com.b2international.commons.http.AcceptHeader;
import com.b2international.commons.http.ExtendedLocale;
import com.b2international.snowowl.core.exceptions.BadRequestException;
import com.b2international.snowowl.eventbus.IEventBus;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

/**
 * Abstract SNOMED CT REST service base class.
 */
public class AbstractSnomedRestService extends AbstractRestService {

	IEventBus iEventBus;

	protected List<ExtendedLocale> getExtendedLocales(final String acceptLanguage) {
		try {
			return AcceptHeader.parseExtendedLocales(new StringReader(acceptLanguage));
		} catch (IOException e) {
			throw new BadRequestException(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw new BadRequestException(e.getMessage());
		}
	}

}

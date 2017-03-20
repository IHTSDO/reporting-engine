package org.ihtsdo.snowowl.authoring.batchimport.api.client;

import java.net.URLConnection;

import us.monoid.web.Resty;

public class RestyOverrideAccept extends Resty.Option {
	private final String accept;
	
	public RestyOverrideAccept(String accept) {
		this.accept = accept;
	}

	@Override public void apply(URLConnection connection) {
		connection.setRequestProperty("Accept", accept);
	}
}

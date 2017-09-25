package org.ihtsdo.termserver.scripting.client;

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

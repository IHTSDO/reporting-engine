package org.ihtsdo.termserver.scripting.client;

import org.jspecify.annotations.Nullable;
import org.springframework.web.util.DefaultUriBuilderFactory;

/**
 * Builds the UriTemplateHandler that replaces RestTemplateBuilder.rootUri(String), which Spring
 * Boot 4.1 deprecated for removal.
 * <p>
 * The encoding mode matters. RestTemplate's own default handler uses URI_COMPONENT, and rootUri()
 * wrapped that handler rather than replacing it, so it inherited the mode. A bare
 * DefaultUriBuilderFactory defaults to TEMPLATE_AND_VALUES instead, which percent-encodes
 * characters URI_COMPONENT leaves alone - including * ( ) , : + / ! @ $ ' ; - all of which carry
 * meaning in the ECL expressions these clients pass as URI variables. Setting the mode explicitly
 * keeps the generated URLs byte-identical to what rootUri() produced.
 */
final class ClientUriFactory {

	private ClientUriFactory() {
	}

	static DefaultUriBuilderFactory forRootUri(@Nullable String rootUri) {
		// rootUri() accepted null, meaning "no root URI", and AuthoringAcceptanceGatewayClient
		// relies on that. DefaultUriBuilderFactory rejects a null base, so map it to the no-arg
		// form, which is the same handler RestTemplate would have used on its own.
		DefaultUriBuilderFactory uriFactory = (rootUri == null)
				? new DefaultUriBuilderFactory()
				: new DefaultUriBuilderFactory(rootUri);
		uriFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT);
		return uriFactory;
	}
}

package org.ihtsdo.snowowl.authoring.api.terminology;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class DomainService {

	@Autowired
	private ObjectMapper objectMapper;

	private final TypeReference<List<Domain>> DOMAIN_LIST_TYPE_REF = new TypeReference<List<Domain>>() {};
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public Domain findDomainByName(final String domainName) {
		List<Domain> domains = loadDomains();
		for (Domain domain : domains) {
			if (domain.getName().equals(domainName)){
				return domain;
			}
		}
		return null;
	}

	private List<Domain> loadDomains() {
		try (InputStream inputStream = getClass().getResourceAsStream("/WEB-INF/snomed-domains.json")) {
			return objectMapper.readValue(inputStream, DOMAIN_LIST_TYPE_REF);
		} catch (IOException e) {
			logger.error("Failed to close stream.", e);
		}
		return null;
	}
}

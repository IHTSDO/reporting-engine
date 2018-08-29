package org.ihtsdo.termserver.scripting.client;

import java.io.IOException;

import org.snomed.authoringtemplate.domain.*;
import org.snomed.authoringtemplate.domain.logical.*;
import org.snomed.authoringtemplate.service.LogicalTemplateParserService;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The intention here is to call the template service on the server we're currently 
 * working with to obtain a template, but for now we'll pick it up as a resource
 * @author Peter
 *
 */
public class TemplateServiceClient {
	
	LogicalTemplateParserService service  = new LogicalTemplateParserService();
	ObjectMapper mapper = new ObjectMapper();
	
	public LogicalTemplate loadLogicalTemplate (String templateName) throws JsonParseException, JsonMappingException, IOException {
		
		ClassLoader classLoader = getClass().getClassLoader();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		ConceptTemplate cTemplate = mapper.readValue(classLoader.getResourceAsStream(templateName), ConceptTemplate.class );
		LogicalTemplate lTemplate = service.parseTemplate(cTemplate.getLogicalTemplate());
		return lTemplate;
	}

}

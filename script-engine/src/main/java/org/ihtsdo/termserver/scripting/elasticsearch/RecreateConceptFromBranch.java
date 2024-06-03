package org.ihtsdo.termserver.scripting.elasticsearch;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.AxiomUtils;
import org.ihtsdo.termserver.scripting.domain.Axiom;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipSerializer;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.owltoolkit.conversion.AxiomRelationshipConversionService;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

public class RecreateConceptFromBranch implements RF2Constants {
	
	protected static Gson gson;
	static {
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.setPrettyPrinting();
		gsonBuilder.registerTypeAdapter(Relationship.class, new RelationshipSerializer());
		gsonBuilder.excludeFieldsWithoutExposeAnnotation();
		gson = gsonBuilder.create();
	}
	
	boolean retainSCTIDs = false;
	String branch = "MAIN/SNOMEDCT-NL/NL/NL-7";
	String conceptId = "160161000146108";
	Concept concept = new Concept(conceptId);
	
	protected ElasticSearchClient es;
	private static final Logger LOGGER = LoggerFactory.getLogger(RecreateConceptFromBranch.class);
	private AxiomRelationshipConversionService axiomService;
	
	public static void main(String[] args) throws TermServerScriptException {
		RecreateConceptFromBranch app = new RecreateConceptFromBranch();
		if (args.length < 1) {
			LOGGER.info("Usage:  -e <elasticsearch url>");
			System.exit(-1);
		}
		
		app.init(args);
		app.recreateConcept();
		app.outputConcept();
	}

	protected void init(String[] args) {
		axiomService = new AxiomRelationshipConversionService (NEVER_GROUPED_ATTRIBUTES);
		for (int i=0; i < args.length ; i++) {
			switch (args[i]) {
				case "-e" : es = new ElasticSearchClient(args[i+1]);
			}
		}
	}
	
	@SuppressWarnings({ "unchecked" })
	private void recreateConcept() throws TermServerScriptException {
		Map<String,Map<?,?>> response = es.getAllDocumentsRelatedToConcept(conceptId, branch);
		List<Map<String,Object>> allDocs = (List<Map<String,Object>>)response.get("hits").get("hits");
		System.out.println(allDocs);
		//TODO check the total returned that it's not > 100 or we'll be missing something
		for (Map<String,Object> doc : allDocs) {
			LOGGER.info("Processing document {}", doc);
			Map<String, Object> component = (Map<String, Object>)doc.get("_source");
			JsonElement jsonElement = gson.toJsonTree(component);
			String type = (String)doc.get("_type");
			switch (type) {
				case ("concept") : processConcept(jsonElement);
					break;
				case ("description") : processDescription(jsonElement);
					break;
				case ("referencesetmember") : processRefsetMember(jsonElement);
					break;
				default: throw new IllegalArgumentException("Unrecognised component type " + type);
			}
		}
		
		if (!retainSCTIDs) {
			//We needed to leave these in for a time to line up the acceptabilities to the descs
			//but they should come out now
			concept.setId(null);
			concept.getDescriptions().forEach(d -> d.setId(null));
			concept.getClassAxioms().forEach(a -> a.setId(null));
		}
	}

	private void processConcept(JsonElement jsonElement) {
		LOGGER.info("Processing component {}", jsonElement);
		Concept c = gson.fromJson(jsonElement, Concept.class);
		populateStandardFields(c, concept);
		DefinitionStatus defStat = c.getDefinitionStatus();
		concept.setDefinitionStatus(defStat);
	}

	private void processDescription(JsonElement jsonElement) throws TermServerScriptException {
		Description d = gson.fromJson(jsonElement, Description.class);
		//If we've already put some acceptability on this description we can swipe that
		Description existing = concept.getDescription(d.getId());
		if (existing != null) {
			d.setAcceptabilityMap(existing.getAcceptabilityMap());
		}
		//Gson only works with fields, so we'll call this setter explicitly
		d.setTypeId(jsonElement.getAsJsonObject().get("typeId").getAsString());
		d.setLang(jsonElement.getAsJsonObject().get("languageCode").getAsString());
		concept.addDescription(d);
	}

	private void processRefsetMember(JsonElement jsonElement) throws TermServerScriptException {
		LOGGER.info("Processing component {}", jsonElement);
		RefsetMember rm = gson.fromJson(jsonElement, RefsetMember.class);
		if (rm.getRefsetId().equals("900000000000456007")) {
			LOGGER.info("Skipping RefsetDescriptor {}",rm.getId());
			return;
		}
		switch (rm.getOnlyAdditionalFieldName()) {
			case "owlExpression" : addOWLAxioms(rm);
				break;
			case "acceptabilityId" : addAcceptability(rm);
			break;
		}
	}

	private void addOWLAxioms(RefsetMember rm) {
		try {
			AxiomRepresentation axiomRepresentation = axiomService.convertAxiomToRelationships(rm.getField("owlExpression"));
			Axiom axiom = new Axiom(concept);
			Set<Relationship> rels = AxiomUtils.getRHSRelationships(concept, axiomRepresentation);
			axiom.getRelationships().addAll(rels);
			populateStandardFields(rm, axiom);
			concept.getClassAxioms().add(axiom);
		} catch (ConversionException | TermServerScriptException e) {
			LOGGER.error("Failed to recover axioms " + rm);
		}
	}

	private void addAcceptability(RefsetMember rm) throws TermServerScriptException {
		Description d = concept.getDescription(rm.getReferencedComponentId());
		//Have we not seen this description yet?
		if (d == null) {
			d = new Description(rm.getReferencedComponentId());
			d.setType(DescriptionType.SYNONYM);  //We can change to FSN later if it is
			concept.addDescription(d);
		}
		String acceptabilityId = rm.getField("acceptabilityId");
		Acceptability acceptability = SnomedUtils.translateAcceptability(acceptabilityId);
		d.getAcceptabilityMap().put(rm.getRefsetId(), acceptability);
	}

	private void populateStandardFields(Component source, Component target) {
		target.setModuleId(source.getModuleId());
		target.setId(source.getId());
	}
	

	private void outputConcept() {
		String json = gson.toJson(concept);
		System.out.println(json);
	}

}

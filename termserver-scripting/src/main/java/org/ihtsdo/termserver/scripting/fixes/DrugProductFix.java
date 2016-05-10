package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.List;

import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import us.monoid.json.JSONException;
import us.monoid.web.JSONResource;

/*
All concepts in the module must be primitive.
All concepts in the module must have one and only one stated |Is a| relationship.
 - The parent concept for all concepts in the module must be 373873005| Pharmaceutical / biologic product (product).
All concepts in the module must have one or more Has active ingredient attributes.
 - The attribute values must be a descendant of 105590001|Substance (substance).
All concepts in the module must have one and only one Has dose form attribute.
 - The attribute value must be a descendant of 105904009| Type of drug preparation (qualifier value).
Any plus symbol in the name must be surrounded by single space
Ingredients in name should be in alpha order.  Change amounts order to match.
Remove the word "preparation"
Change m/r to modified-release
2nd ingredient should be lower case
 */
public class DrugProductFix extends BatchFix implements RF2Constants{
	
	private Gson gson;
	
	private static String PHARM_BIO_PRODUCT = "373873005"; //Pharmaceutical / biologic product (product)
	private static String IS_A = "116680003";  // | Is a (attribute) |
	
	public static void main(String[] args) throws TermServerFixException, IOException {
		BatchFix batchFix = new DrugProductFix();
		batchFix.init(args);
		batchFix.processFile();
	}

	@Override
	public void doFix(String conceptId, String branchPath)
			throws TermServerFixException, SnowOwlClientException {
		debug ("Examining: " + conceptId);
		Concept c = null;
		try {
			JSONResource response = tsClient.getConcept(conceptId, branchPath);
			String json = response.toObject().toString();
			c = gson.fromJson(json, Concept.class);
		} catch (JSONException | IOException e) {
			report(conceptId, REPORT_ACTION_TYPE.API_ERROR, "Failed to recover concept from termserver: " + e.getMessage());
		}
		
		ensurePrimative(c);
		ensureAcceptableParent(c);
		
	}

	private void ensurePrimative(Concept c) {
		if (!c.getDefinitionStatus().equals(Concept.DEFINITION_STATUS.PRIMITIVE.toString())) {
			report (c.getConceptId(), REPORT_ACTION_TYPE.CONCEPT_CHANGE_MADE, "Definition status changed to primitive");
			c.setDefinitionStatus(Concept.DEFINITION_STATUS.PRIMITIVE.toString());
		}
	}
	

	private void ensureAcceptableParent(Concept c) {
		List<Relationship> statedParents = c.getRelationships(CHARACTERISTIC_TYPE.STATED_RELATIONSHIP, IS_A);
		for (Relationship thisParent : statedParents) {
			if (thisParent.isActive() && !thisParent.getTarget().equals(PHARM_BIO_PRODUCT)) {
				report(c.getConceptId(), REPORT_ACTION_TYPE.RELATIONSHIP_CHANGE_MADE, "Inactivated unwanted parent: " + thisParent);
				thisParent.setActive(false);
			}
		}
	}


	@Override
	public void init() {
		super.init();
		GsonBuilder gsonBuilder = new GsonBuilder();
		//gsonBuilder.registerTypeAdapter(Concept.class, new ConceptDeserializer());
		gson = gsonBuilder.create();
	}

}

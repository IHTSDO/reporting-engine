package org.ihtsdo.termserver.scripting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;

public class GraphLoader implements RF2Constants {

	private static GraphLoader singletonGraphLoader = null;
	private Map<String, Concept> concepts = new HashMap<String, Concept>();

	public static GraphLoader getGraphLoader() {
		if (singletonGraphLoader == null) {
			singletonGraphLoader = new GraphLoader();
		}
		return singletonGraphLoader;
	}
	
	public Collection <Concept> getAllConcepts() {
		return concepts.values();
	}

	public Map<String, Relationship> loadRelationshipFile (CHARACTERISTIC_TYPE characteristicType, InputStream relStream) throws IOException, TermServerScriptException {
		Map<String, Relationship> loadedRelationships = new HashMap<String, Relationship>();
		BufferedReader br = new BufferedReader(new InputStreamReader(relStream, StandardCharsets.UTF_8));
		String line;
		while ((line = br.readLine()) != null) {
			String[] lineItems = line.split(FIELD_DELIMITER);
			// Only store active relationships.  Check for active filters out header row.
			if (lineItems[REL_IDX_ACTIVE].equals(ACTIVE_FLAG)
					&& !lineItems[REL_IDX_CHARACTERISTICTYPEID].equals(ADDITIONAL_RELATIONSHIP)) {
				addRelationshipToConcept(characteristicType, lineItems);
			}
		}
		return loadedRelationships;
	}
	
	private Concept addRelationshipToConcept(CHARACTERISTIC_TYPE characteristicType, String[] lineItems) throws TermServerScriptException {
		Concept source = getConcept(lineItems[REL_IDX_SOURCEID]);
		Concept type = getConcept(lineItems[REL_IDX_TYPEID]);
		Concept destination = getConcept(lineItems[REL_IDX_DESTINATIONID]);
		int groupNum = Integer.parseInt(lineItems[REL_IDX_RELATIONSHIPGROUP]);
		
		Relationship r = new Relationship(source, type, destination, groupNum);
		r.setCharacteristicType(characteristicType);
		r.setActive(lineItems[REL_IDX_ACTIVE].equals("1"));
		r.setEffectiveTime(lineItems[REL_IDX_EFFECTIVETIME]);
		source.addRelationship(r);
		
		//Only if the relationship is inferred, consider adding it as a parent
		if (type.equals(IS_A) && r.getCharacteristicType().equals(CHARACTERISTIC_TYPE.INFERRED_RELATIONSHIP)) {
			source.addParent(destination);
			destination.addChild(source);
		}
		return source;
	}

	public Concept getConcept(String sctId) throws TermServerScriptException {
		return getConcept(sctId, true);
	}
	
	public Concept getConcept(String sctId, boolean createIfRequired) throws TermServerScriptException {
		if (!concepts.containsKey(sctId)) {
			if (createIfRequired) {
				Concept c = new Concept(sctId);
				concepts.put(sctId, c);
			} else {
				throw new TermServerScriptException("Expected Concept " + sctId + " has not been loaded from archive");
			}
		}
		return concepts.get(sctId);
	}
	
	public void loadDescriptionFile(InputStream descStream) throws IOException, TermServerScriptException {
		//Not putting this in a try resource block otherwise it will close the stream on completion and we've got more to read!
		BufferedReader br = new BufferedReader(new InputStreamReader(descStream, StandardCharsets.UTF_8));
		String line;
		while ((line = br.readLine()) != null) {
			String[] lineItems = line.split(FIELD_DELIMITER);
			// Only store active relationships.  Need for active flag will skip header line
			if (lineItems[DES_IDX_ACTIVE].equals(ACTIVE_FLAG) && lineItems[DES_IDX_TYPEID].equals(FULLY_SPECIFIED_NAME)) {
				Concept c = getConcept(lineItems[DES_IDX_CONCEPTID]);
				c.setFsn(lineItems[DES_IDX_TERM]);
			}
		}
	}
	
	public void loadConceptFile(InputStream is) throws IOException, TermServerScriptException {
		//Not putting this in a try resource block otherwise it will close the stream on completion and we've got more to read!
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		String line;
		boolean isHeaderLine = true;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				Concept c = getConcept(lineItems[CON_IDX_ID]);
				c.setActive(lineItems[CON_IDX_ACTIVE].equals("1"));
				c.setEffectiveTime(lineItems[CON_IDX_EFFECTIVETIME]);
			} else {
				isHeaderLine = false;
			}
		}
	}

	public Set<Concept> loadRelationshipDelta(CHARACTERISTIC_TYPE characteristicType, InputStream relStream) throws IOException, TermServerScriptException {
		Set<Concept> concepts = new HashSet<Concept>();
		BufferedReader br = new BufferedReader(new InputStreamReader(relStream, StandardCharsets.UTF_8));
		String line;
		boolean isHeaderLine = true;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				concepts.add(addRelationshipToConcept(characteristicType, lineItems));
			} else {
				isHeaderLine = false;
			}
		}
		return concepts;
	}

}

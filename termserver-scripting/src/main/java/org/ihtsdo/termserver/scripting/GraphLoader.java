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
import java.util.zip.ZipInputStream;

import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class GraphLoader implements RF2Constants {

	private static GraphLoader singletonGraphLoader = null;
	private Map<String, Concept> concepts = new HashMap<String, Concept>();
	private Map<String, Description> descriptions = new HashMap<String, Description>();
	public StringBuffer log = new StringBuffer();
	
	public static GraphLoader getGraphLoader() {
		if (singletonGraphLoader == null) {
			singletonGraphLoader = new GraphLoader();
		}
		return singletonGraphLoader;
	}
	
	public Collection <Concept> getAllConcepts() {
		return concepts.values();
	}
	
	public Set<Concept> loadRelationships(CharacteristicType characteristicType, InputStream relStream, boolean addRelationshipsToConcepts) 
			throws IOException, TermServerScriptException {
		Set<Concept> concepts = new HashSet<Concept>();
		BufferedReader br = new BufferedReader(new InputStreamReader(relStream, StandardCharsets.UTF_8));
		String line;
		boolean isHeaderLine = true;
		long relationshipsLoaded = 0;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				Concept thisConcept;
				if (addRelationshipsToConcepts) {
					thisConcept = addRelationshipToConcept(characteristicType, lineItems);
				} else {
					thisConcept = getConcept(lineItems[REL_IDX_SOURCEID]);
				}
				concepts.add(thisConcept);
			} else {
				isHeaderLine = false;
			}
			relationshipsLoaded++;
		}
		log.append("\tLoaded " + relationshipsLoaded + " relationships of type " + characteristicType + " which were " + (addRelationshipsToConcepts?"":"not ") + "added to concepts\n");
		return concepts;
	}
	
	private Concept addRelationshipToConcept(CharacteristicType characteristicType, String[] lineItems) throws TermServerScriptException {
		Concept source = getConcept(lineItems[REL_IDX_SOURCEID]);
		Concept type = getConcept(lineItems[REL_IDX_TYPEID]);
		Concept destination = getConcept(lineItems[REL_IDX_DESTINATIONID]);
		int groupNum = Integer.parseInt(lineItems[REL_IDX_RELATIONSHIPGROUP]);
		
		Relationship r = new Relationship(source, type, destination, groupNum);
		r.setRelationshipId(lineItems[REL_IDX_ID]);
		r.setCharacteristicType(characteristicType);
		r.setActive(lineItems[REL_IDX_ACTIVE].equals("1"));
		r.setEffectiveTime(lineItems[REL_IDX_EFFECTIVETIME]);
		r.setModifier(SnomedUtils.translateModifier(lineItems[REL_IDX_MODIFIERID]));
		r.setModuleId(lineItems[REL_IDX_MODULEID]);
		
		//Only if the relationship is inferred, consider adding it as a parent
		if (r.isActive() && type.equals(IS_A)) {
			source.addParent(r.getCharacteristicType(),destination);
			destination.addChild(r.getCharacteristicType(),source);
		} 
		//Only store actual attributes.  Parents/Children and counted separately
		if (!type.equals(IS_A)) {
			source.addRelationship(r);
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
				throw new TermServerScriptException("Expected Concept '" + sctId + "' has not been loaded from archive");
			}
		}
		return concepts.get(sctId);
	}
	
	public Description getDescription(String sctId) throws TermServerScriptException {
		return getDescription(sctId, true);
	}
	
	public Description getDescription(String sctId, boolean createIfRequired) throws TermServerScriptException {
		if (!descriptions.containsKey(sctId)) {
			if (createIfRequired) {
				Description d = new Description(sctId);
				descriptions.put(sctId, d);
			} else {
				throw new TermServerScriptException("Expected Description " + sctId + " has not been loaded from archive");
			}
		}
		return descriptions.get(sctId);
	}
	
	public void loadDescriptionFile(InputStream descStream, boolean fsnOnly) throws IOException, TermServerScriptException {
		//Not putting this in a try resource block otherwise it will close the stream on completion and we've got more to read!
		BufferedReader br = new BufferedReader(new InputStreamReader(descStream, StandardCharsets.UTF_8));
		String line;
		boolean isHeader = true;
		while ((line = br.readLine()) != null) {
			if (!isHeader) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				if (fsnOnly) {
					// Only store active descriptions.  
					if (lineItems[DES_IDX_ACTIVE].equals(ACTIVE_FLAG) && lineItems[DES_IDX_TYPEID].equals(FULLY_SPECIFIED_NAME)) {
						Concept c = getConcept(lineItems[DES_IDX_CONCEPTID]);
						c.setFsn(lineItems[DES_IDX_TERM]);
					}
				} else {
					Concept c = getConcept(lineItems[DES_IDX_CONCEPTID]);
					Description d = loadDescriptionLine(lineItems);
					c.addDescription(d);
					if (d.isActive() && d.getType().equals(DescriptionType.FSN)) {
						c.setFsn(lineItems[DES_IDX_TERM]);
					}
				}
			} else {
				isHeader = false;
			}
		}
	}
	
	private Description loadDescriptionLine(String[] lineItems) throws TermServerScriptException {
		Description d = getDescription(lineItems[DES_IDX_ID]);
		d.setDescriptionId(lineItems[DES_IDX_ID]);
		d.setActive(lineItems[DES_IDX_ACTIVE].equals("1"));
		d.setModuleId(lineItems[DES_IDX_MODULID]);
		d.setCaseSignificance(lineItems[DES_IDX_CASESIGNIFICANCEID]);
		d.setConceptId(lineItems[DES_IDX_CONCEPTID]);
		d.setLang(lineItems[DES_IDX_LANGUAGECODE]);
		d.setTerm(lineItems[DES_IDX_TERM]);
		d.setType(SnomedUtils.translateDescType(lineItems[DES_IDX_TYPEID]));
		return d;
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
				c.setDefinitionStatus(SnomedUtils.translateDefnStatus(lineItems[CON_IDX_DEFINITIONSTATUSID]));
			} else {
				isHeaderLine = false;
			}
		}
	}

	public Set<Concept> loadRelationshipDelta(CharacteristicType characteristicType, InputStream relStream) throws IOException, TermServerScriptException {
		return loadRelationships(characteristicType, relStream, true);
	}

	public Set<Concept> getModifiedConcepts(
			CharacteristicType characteristicType, ZipInputStream relStream) throws IOException, TermServerScriptException {
		return loadRelationships(characteristicType, relStream, false);
	}

	public void loadLanguageFile(InputStream is) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				Description d = getDescription(lineItems[LANG_IDX_REFCOMPID]);
				LangRefsetEntry langRefsetEntry = loadLanguageLine(lineItems);
				d.getLangRefsetEntries().add(langRefsetEntry);
				if (lineItems[LANG_IDX_ACTIVE].equals("1")) {
					Acceptability a = SnomedUtils.getAcceptability(lineItems[LANG_IDX_ACCEPTABILITY_ID]);
					d.setAcceptablity(lineItems[LANG_IDX_REFSETID], a);
				}
			} else {
				isHeaderLine = false;
			}
		}
	}

	private LangRefsetEntry loadLanguageLine(String[] lineItems) {
		LangRefsetEntry l = new LangRefsetEntry();
		l.setId(lineItems[LANG_IDX_ID]);
		l.setEffectiveTime(lineItems[LANG_IDX_EFFECTIVETIME]);
		l.setActive(lineItems[LANG_IDX_ACTIVE].equals("1"));
		l.setModuleId(lineItems[LANG_IDX_MODULID]);
		l.setRefsetId(lineItems[LANG_IDX_REFSETID]);
		l.setReferencedComponentId(lineItems[LANG_IDX_REFCOMPID]);
		l.setAcceptabilityId(lineItems[LANG_IDX_ACCEPTABILITY_ID]);
		return l;
	}

}

package org.ihtsdo.termserver.scripting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.HistoricalAssociation;
import org.ihtsdo.termserver.scripting.domain.InactivationIndicatorEntry;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class GraphLoader implements RF2Constants {

	private static GraphLoader singletonGraphLoader = null;
	private Map<String, Concept> concepts = new HashMap<String, Concept>();
	private Map<String, Description> descriptions = new HashMap<String, Description>();
	
	//Watch that this map is of the TARGET of the association, ie all concepts used in a historical association
	private Map<Concept, List<HistoricalAssociation>> historicalAssociations =  new HashMap<Concept, List<HistoricalAssociation>>();
	
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
				Concept thisConcept = getConcept(lineItems[REL_IDX_SOURCEID]);
				if (addRelationshipsToConcepts) {
					addRelationshipToConcept(thisConcept, characteristicType, lineItems);
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
	
	public void addRelationshipToConcept(Concept source, CharacteristicType characteristicType, String[] lineItems) throws TermServerScriptException {
		
		String sourceId = lineItems[REL_IDX_SOURCEID];
		String destId = lineItems[REL_IDX_DESTINATIONID];
		String typeId = lineItems[REL_IDX_TYPEID];
		
		if (sourceId.length() < 4 || destId.length() < 4 || typeId.length() < 4 ) {
			System.out.println("*** Invalid SCTID encountered in relationship " + lineItems[REL_IDX_ID] + ": s" + sourceId + " d" + destId + " t" + typeId);
		}
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
		source.addRelationship(r);
	}

	public Concept getConcept(String sctId) throws TermServerScriptException {
		return getConcept(sctId, true, true);
	}
	
	public Concept getConcept(Long sctId) throws TermServerScriptException {
		return getConcept(sctId.toString(), true, true);
	}
	
	public boolean conceptKnown(String sctId) {
		return concepts.containsKey(sctId);
	}
	
	public Concept getConcept(String sctId, boolean createIfRequired, boolean validateExists) throws TermServerScriptException {
		Concept c = concepts.get(sctId);
		if (c == null) {
			if (createIfRequired) {
				c = new Concept(sctId);
				concepts.put(sctId, c);
			} else if (validateExists) {
				throw new TermServerScriptException("Expected Concept '" + sctId + "' has not been loaded from archive");
			}
		}
		return c;
	}
	
	public Description getDescription(String sctId) throws TermServerScriptException {
		return getDescription(sctId, true, true);
	}
	
	public Description getDescription(Long sctId) throws TermServerScriptException {
		return getDescription(sctId.toString(), true, true);
	}
	
	public Description getDescription(String sctId, boolean createIfRequired, boolean validateExists) throws TermServerScriptException {
		Description d = descriptions.get(sctId);
		if (d == null) {
			if (createIfRequired) {
				d = new Description(sctId);
				descriptions.put(sctId, d);
			} else if (validateExists) {
				throw new TermServerScriptException("Expected Description " + sctId + " has not been loaded from archive");
			}
		}
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
				Concept c = Concept.fromRf2(lineItems);
				concepts.put(c.getConceptId(), c);
			} else {
				isHeaderLine = false;
			}
		}
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
					Description d = Description.fromRf2(lineItems);
					descriptions.put(lineItems[DES_IDX_ID], d);
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
	
	/**
	 * Recurse hierarchy and set shortest path depth for all concepts
	 * @throws TermServerScriptException 
	 */
	public void populateHierarchyDepth(Concept startingPoint, int currentDepth) throws TermServerScriptException {
		startingPoint.setDepth(currentDepth);
		for (Concept child : startingPoint.getDescendents(IMMEDIATE_CHILD, CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			populateHierarchyDepth(child, currentDepth + 1);
		}
	}

	public void loadInactivationIndicatorFile(ZipInputStream zis, boolean conceptIndicators) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				if (conceptIndicators) {
					Concept c = getConcept(lineItems[INACT_IDX_REFCOMPID]);
					InactivationIndicatorEntry inactivation = loadInactivationLine(lineItems);
					c.getInactivationIndicatorEntries().add(inactivation);
				} else {
					//Description inactivation indicators.  We'll only load the current active one, and warn if there is more than one.
					if (lineItems[INACT_IDX_ACTIVE].equals(ACTIVE_FLAG)) {
						Description d = getDescription(lineItems[INACT_IDX_REFCOMPID]);
						InactivationIndicator indicator = SnomedUtils.translateInactivationIndicator(lineItems[INACT_IDX_REASON_ID]);
						if (d.getInactivationIndicator() != null) {
							System.out.println ("Warning, description " + d + " changing inactivation indicator from " + d.getInactivationIndicator() + " to " + indicator);
						}
						d.setInactivationIndicator(indicator);
					}
				}
			} else {
				isHeaderLine = false;
			}
		}
	}
	
	private InactivationIndicatorEntry loadInactivationLine(String[] lineItems) {
		InactivationIndicatorEntry i = new InactivationIndicatorEntry();
		i.setId(lineItems[INACT_IDX_ID]);
		i.setEffectiveTime(lineItems[INACT_IDX_EFFECTIVETIME]);
		i.setActive(lineItems[INACT_IDX_ACTIVE].equals("1"));
		i.setModuleId(lineItems[INACT_IDX_MODULID]);
		i.setRefsetId(lineItems[INACT_IDX_REFSETID]);
		i.setReferencedComponentId(lineItems[INACT_IDX_REFCOMPID]);
		i.setInactivationReasonId(lineItems[INACT_IDX_REASON_ID]);
		return i;
	}
	
	public void loadHistoricalAssociationFile(ZipInputStream zis) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				Concept c = getConcept(lineItems[INACT_IDX_REFCOMPID]);
				HistoricalAssociation historicalAssociation = loadHistoricalAssociationLine(lineItems);
				c.getHistorialAssociations().add(historicalAssociation);
				if (historicalAssociation.isActive()) {
					recordHistoricalAssociation(historicalAssociation);
				}
			} else {
				isHeaderLine = false;
			}
		}
	}
	
	private void recordHistoricalAssociation(HistoricalAssociation h) throws TermServerScriptException {
		//Remember we're using the target of the association as the map key here
		Concept target = getConcept(h.getTargetComponentId());
		//Have we seen this concept before?
		List<HistoricalAssociation> associations;
		if (historicalAssociations.containsKey(target)) {
			associations = historicalAssociations.get(target);
		} else {
			associations = new ArrayList<HistoricalAssociation>();
			historicalAssociations.put(target, associations);
		}
		associations.add(h);
	}
	
	public List<HistoricalAssociation> usedAsHistoricalAssociationTarget (Concept c) {
		return historicalAssociations.get(c);
	}

	private HistoricalAssociation loadHistoricalAssociationLine(String[] lineItems) {
		HistoricalAssociation h = new HistoricalAssociation();
		h.setId(lineItems[ASSOC_IDX_ID]);
		h.setEffectiveTime(lineItems[ASSOC_IDX_EFFECTIVETIME]);
		h.setActive(lineItems[ASSOC_IDX_ACTIVE].equals("1"));
		h.setModuleId(lineItems[ASSOC_IDX_MODULID]);
		h.setRefsetId(lineItems[ASSOC_IDX_REFSETID]);
		h.setReferencedComponentId(lineItems[ASSOC_IDX_REFCOMPID]);
		h.setTargetComponentId(lineItems[ASSOC_IDX_TARGET]);
		return h;
	}


}

package org.ihtsdo.termserver.scripting;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.owltoolkit.conversion.AxiomRelationshipConversionService;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;
import org.springframework.util.StringUtils;

public class GraphLoader implements RF2Constants {

	private static GraphLoader singleton = null;
	private Map<String, Concept> concepts = new HashMap<String, Concept>();
	private Map<String, Description> descriptions = new HashMap<String, Description>();
	private Map<String, Component> allComponents = null;
	private Map<Component, Concept> componentOwnerMap = null;
	private Map<String, Concept> fsnMap = null;
	private String excludeModule = SCTID_LOINC_MODULE;
	public static int MAX_DEPTH = 1000;
	private Set<Concept> orphanetConcepts;
	private AxiomRelationshipConversionService axiomService;
	
	private DescendentsCache descendantsCache = DescendentsCache.getDescendentsCache();
	private AncestorsCache ancestorsCache = AncestorsCache.getAncestorsCache();
	private AncestorsCache statedAncestorsCache = AncestorsCache.getStatedAncestorsCache();
	
	//Watch that this map is of the TARGET of the association, ie all concepts used in a historical association
	private Map<Concept, List<AssociationEntry>> historicalAssociations =  new HashMap<Concept, List<AssociationEntry>>();
	
	public StringBuffer log = new StringBuffer();
	
	public static GraphLoader getGraphLoader() {
		if (singleton == null) {
			singleton = new GraphLoader();
			singleton.axiomService = new AxiomRelationshipConversionService (null);
			populateKnownConcepts();
		}
		
		return singleton;
	}
	
	private static void populateKnownConcepts() {
		//Pre populate known concepts to ensure we only ever refer to one object
		//Reset concept each time, to avoid contamination from previous runs
		ROOT_CONCEPT.reset();
		singleton.concepts.put(SCTID_ROOT_CONCEPT.toString(), ROOT_CONCEPT);
		
		IS_A.reset();
		singleton.concepts.put(SCTID_IS_A_CONCEPT.toString(), IS_A);

		PHARM_BIO_PRODUCT.reset();
		singleton.concepts.put(PHARM_BIO_PRODUCT.getConceptId(), PHARM_BIO_PRODUCT);
		
		MEDICINAL_PRODUCT.reset();
		singleton.concepts.put(MEDICINAL_PRODUCT.getConceptId(), MEDICINAL_PRODUCT);

		PHARM_DOSE_FORM.reset();
		singleton.concepts.put(PHARM_DOSE_FORM.getConceptId(), PHARM_DOSE_FORM);
		
		SUBSTANCE.reset();
		singleton.concepts.put(SUBSTANCE.getConceptId(), SUBSTANCE);

		CLINICAL_FINDING.reset();
		singleton.concepts.put(CLINICAL_FINDING.getConceptId(), CLINICAL_FINDING);
		
		BODY_STRUCTURE.reset();
		singleton.concepts.put(BODY_STRUCTURE.getConceptId(), BODY_STRUCTURE);

		PROCEDURE.reset();
		singleton.concepts.put(PROCEDURE.getConceptId(), PROCEDURE);
		
		SITN_WITH_EXP_CONTXT.reset();
		singleton.concepts.put(SITN_WITH_EXP_CONTXT.getConceptId(), SITN_WITH_EXP_CONTXT);

		SPECIMEN.reset();
		singleton.concepts.put(SPECIMEN.getConceptId(), SPECIMEN);
		
		OBSERVABLE_ENTITY.reset();
		singleton.concepts.put(OBSERVABLE_ENTITY.getConceptId(),OBSERVABLE_ENTITY);

		EVENT.reset();
		singleton.concepts.put(EVENT.getConceptId(),EVENT);
		
		DISEASE.reset();
		singleton.concepts.put(DISEASE.getConceptId(), DISEASE);
	}

	public Collection <Concept> getAllConcepts() {
		return concepts.values();
	}
	
	public void reset() {
		TermServerScript.info("Resetting Graph Loader");
		concepts = new HashMap<String, Concept>();
		descriptions = new HashMap<String, Description>();
		allComponents = null;
		componentOwnerMap = null;
		fsnMap = null;
		orphanetConcepts = null;
		descendantsCache.reset();
		ancestorsCache.reset();
		statedAncestorsCache.reset();
		historicalAssociations =  new HashMap<Concept, List<AssociationEntry>>();
		//We'll reset the ECL cache during TS Init
		populateKnownConcepts();
	}
	
	public Set<Concept> loadRelationships(CharacteristicType characteristicType, InputStream relStream, boolean addRelationshipsToConcepts, boolean isDelta) 
			throws IOException, TermServerScriptException, TermServerClientException {
		Set<Concept> concepts = new HashSet<Concept>();
		BufferedReader br = new BufferedReader(new InputStreamReader(relStream, StandardCharsets.UTF_8));
		String line;
		boolean isHeaderLine = true;
		long relationshipsLoaded = 0;
		int ignoredRelationships = 0;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				
				//Exclude LOINC
				if (lineItems[IDX_MODULEID].equals(excludeModule)) {
					continue;
				}
				
				/*if (lineItems[REL_IDX_SOURCEID].equals("300097006") && lineItems[REL_IDX_TYPEID].equals("116680003")) {
					System.out.println ("Debug Here");
				}*/
				
				if (!isConcept(lineItems[REL_IDX_SOURCEID])) {
					System.out.println (characteristicType + " relationship " + lineItems[REL_IDX_ID] + " referenced a non concept identifier: " + lineItems[REL_IDX_SOURCEID]);
				}
				Concept thisConcept = getConcept(lineItems[REL_IDX_SOURCEID]);
				if (addRelationshipsToConcepts) {
					ignoredRelationships += addRelationshipToConcept(characteristicType, lineItems, isDelta);
				}
				concepts.add(thisConcept);
				relationshipsLoaded++;
			} else {
				isHeaderLine = false;
			}
		}
		log.append("\tLoaded " + relationshipsLoaded + " relationships of type " + characteristicType + " which were " + (addRelationshipsToConcepts?"":"not ") + "added to concepts\n");
		if (isDelta) {
			TermServerScript.info (ignoredRelationships + " inactivating relationships were ignored as activating ones received in same delta");
		}
		return concepts;
	}
	
	public void loadAxioms(InputStream axiomStream, boolean isDelta) 
			throws IOException, TermServerScriptException, TermServerClientException {
		BufferedReader br = new BufferedReader(new InputStreamReader(axiomStream, StandardCharsets.UTF_8));
		String line;
		boolean isHeaderLine = true;
		int axiomsLoaded = 0;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				
				/*if (lineItems[REF_IDX_ID].equals("76a3311f-e003-4ec9-8070-82d5581bdcadv")) {
					System.out.println ("Debug Here");
				}
				
				if (lineItems[REF_IDX_ID].equals("8016bcd2-83e7-47c1-a998-8f9c6d3a97b4")) {
					System.out.println ("Debug Here");
				}*/
				
				//Only load OWL Expressions
				if (!lineItems[REF_IDX_REFSETID].equals(SCTID_OWL_AXIOM_REFSET)) {
					continue;
				}
				

				/*if (lineItems[REF_IDX_REFCOMPID].equals("204889008")) {
					System.out.println ("Debug Here");
				}*/
				Long conceptId = Long.parseLong(lineItems[REF_IDX_REFCOMPID]);
				if (!isConcept(lineItems[REF_IDX_REFCOMPID])) {
					System.out.println ("Axiom " + lineItems[REL_IDX_ID] + " referenced a non concept identifier: " + lineItems[REF_IDX_REFCOMPID]);
				}
				
				//Also save data in RF2 form so we can build Snapshot
				AxiomEntry axiomEntry = AxiomEntry.fromRf2(lineItems);
				//Remove first in case we're replacing
				getConcept(conceptId).getAxiomEntries().remove(axiomEntry);
				getConcept(conceptId).getAxiomEntries().add(axiomEntry);
				
				try {
					boolean isActive = lineItems[REF_IDX_ACTIVE].equals(ACTIVE_FLAG);
					AxiomRepresentation axiom = axiomService.convertAxiomToRelationships(conceptId, lineItems[REF_IDX_AXIOM_STR]);
					//Filter out any additional statements such as TransitiveObjectProperty(:123005000)]
					if (axiom != null) {
						Long LHS = axiom.getLeftHandSideNamedConcept();
						if (LHS != null && !conceptId.equals(LHS)) {
							throw new IllegalArgumentException("Axiom LHS != RefCompId: " + line);
						}
						
						for (Relationship r :  AxiomUtils.getRHSRelationships(getConcept(conceptId), axiom)) {
							r.setActive(isActive);
							addRelationshipToConcept(CharacteristicType.STATED_RELATIONSHIP, r, isDelta);
						}
					}
				} catch (ConversionException e) {
					throw new TermServerScriptException("Failed to load axiom: " + line, e);
				}
			} else {
				isHeaderLine = false;
			}
		}
		log.append("\tLoaded " + axiomsLoaded + " axioms");
	}
	
	private boolean isConcept(String sctId) {
		return sctId.charAt(sctId.length()-2) == '0';
	}
	
	public Concept findConcept (String fsn) {
		//Populate the fsn map if required
		if (fsnMap == null) {
			fsnMap = new HashMap<>();
			for (Concept c : concepts.values()) {
				fsnMap.put(c.getFsn(), c);
			}
		}
		return fsnMap.get(fsn);
	}
	
	private Relationship createRelationshipFromRF2(CharacteristicType charType, String[] lineItems) throws TermServerScriptException {
		String sourceId = lineItems[REL_IDX_SOURCEID];
		Concept source = getConcept(sourceId);
		String destId = lineItems[REL_IDX_DESTINATIONID];
		String typeId = lineItems[REL_IDX_TYPEID];
		
		if (sourceId.length() < 4 || destId.length() < 4 || typeId.length() < 4 ) {
			System.out.println("*** Invalid SCTID encountered in relationship " + lineItems[REL_IDX_ID] + ": s" + sourceId + " d" + destId + " t" + typeId);
		}
		Concept type = getConcept(lineItems[REL_IDX_TYPEID]);
		Concept destination = getConcept(lineItems[REL_IDX_DESTINATIONID]);
		int groupNum = Integer.parseInt(lineItems[REL_IDX_RELATIONSHIPGROUP]);
		
		Relationship r = new Relationship(source, type, destination, groupNum);
		r.setRelationshipId(lineItems[REL_IDX_ID].isEmpty()?null:lineItems[REL_IDX_ID]);
		r.setCharacteristicType(charType);
		r.setActive(lineItems[REL_IDX_ACTIVE].equals("1"));
		r.setEffectiveTime(lineItems[REL_IDX_EFFECTIVETIME].isEmpty()?null:lineItems[REL_IDX_EFFECTIVETIME]);
		r.setModifier(SnomedUtils.translateModifier(lineItems[REL_IDX_MODIFIERID]));
		r.setModuleId(lineItems[REL_IDX_MODULEID]);
		//Changing those values after the defaults were set in the constructor will incorrectly mark dirty
		r.setClean();
		return r;
	}

	/**
	 * @return ignored count (ie 1 if relationship addition was ignored)
	 * @throws TermServerScriptException
	 */
	public int addRelationshipToConcept(CharacteristicType charType, String[] lineItems, boolean isDelta) throws TermServerScriptException {
		Relationship r = createRelationshipFromRF2(charType, lineItems);
		return addRelationshipToConcept(charType, r, isDelta);
	}
	
	public int addRelationshipToConcept(CharacteristicType charType, Relationship r, boolean isDelta) throws TermServerScriptException {
		//Consider adding or removing parents if the relationship is ISA
		//But only remove items if we're processing a delta
		if (r.getType().equals(IS_A)) {
			if (r.isActive()) {
				r.getSource().addParent(r.getCharacteristicType(),r.getTarget());
				r.getTarget().addChild(r.getCharacteristicType(),r.getSource());
			} else if (isDelta) {
				r.getSource().removeParent(r.getCharacteristicType(),r.getTarget());
				r.getTarget().removeChild(r.getCharacteristicType(),r.getSource());
			}
		} 
		
		//In the case of importing an Inferred Delta, we could end up adding a relationship instead of replacing
		//if it has a different SCTID.  We need to check for equality using triple, not SCTID in that case.
		boolean successfullyAdded = r.getSource().addRelationship(r, isDelta);
		return successfullyAdded ? 0 : 1;	
	}
	public Concept getConcept(String identifier) throws TermServerScriptException {
		return getConcept(identifier.trim(), true, true);
	}
	
	public Concept getConceptSafely (String identifier) {
		try {
			return getConcept(identifier, true, true);
		} catch (TermServerScriptException e) {
			return null;
		}
	}
	
	public Concept getConcept(Long sctId) throws TermServerScriptException {
		return getConcept(sctId.toString(), true, true);
	}
	
	public boolean conceptKnown(String sctId) {
		return concepts.containsKey(sctId);
	}
	
	public Concept getConcept(String identifier, boolean createIfRequired, boolean validateExists) throws TermServerScriptException {
		//Have we been passed a full identifier for the concept eg SCTID |FSN| ?
		if (StringUtils.isEmpty(identifier)) {
			throw new IllegalArgumentException("Empty SCTID encountered");
		}
		if (identifier.contains(PIPE)) {
			identifier = identifier.split(ESCAPED_PIPE)[0].trim();
		}
		String sctId = identifier;
		
		//Make sure we're actually being asked for a concept
		if (sctId.length() < 6 || !isConcept(sctId)) {
			throw new IllegalArgumentException("Request made for non concept sctid: '" + sctId + "'");
		}
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
				//Exclude LOINC
				if (lineItems[IDX_MODULEID].equals(excludeModule)) {
					continue;
				}
				//We might already have received some details about this concept
				Concept c = getConcept(lineItems[IDX_ID]);
				Concept.fillFromRf2(c, lineItems);
				if (c.getDefinitionStatus() == null) {
					throw new TermServerScriptException("Concept " + c + " did not define definition status");
				}
			} else {
				isHeaderLine = false;
			}
		}
	}
	
	public void loadDescriptionFile(InputStream descStream, boolean fsnOnly) throws IOException, TermServerScriptException, TermServerClientException {
		//Not putting this in a try resource block otherwise it will close the stream on completion and we've got more to read!
		BufferedReader br = new BufferedReader(new InputStreamReader(descStream, StandardCharsets.UTF_8));
		String line;
		boolean isHeader = true;
		while ((line = br.readLine()) != null) {
			if (!isHeader) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				//Exclude LOINC
				if (lineItems[IDX_MODULEID].equals(excludeModule)) {
					continue;
				}
				
				/*if (lineItems[DES_IDX_ID].equals("3727472012")) {
					System.out.println ("Debug Here");
				}*/
				
				Concept c = getConcept(lineItems[DES_IDX_CONCEPTID]);
				if (lineItems[DES_IDX_ACTIVE].equals(ACTIVE_FLAG) && lineItems[DES_IDX_TYPEID].equals(FULLY_SPECIFIED_NAME)) {
					c.setFsn(lineItems[DES_IDX_TERM]);
				}
				
				if (!fsnOnly) {
					//We might already have information about this description, eg langrefset entries
					Description d = getDescription (lineItems[DES_IDX_ID]);
					Description.fillFromRf2(d,lineItems);
					c.addDescription(d);
				}
			} else {
				isHeader = false;
			}
		}
	}

	public Set<Concept> loadRelationshipDelta(CharacteristicType characteristicType, InputStream relStream) throws IOException, TermServerScriptException, TermServerClientException {
		return loadRelationships(characteristicType, relStream, true, true);
	}

	public Set<Concept> getModifiedConcepts(
			CharacteristicType characteristicType, ZipInputStream relStream) throws IOException, TermServerScriptException, TermServerClientException {
		return loadRelationships(characteristicType, relStream, false, false);
	}

	public void loadLanguageFile(InputStream is) throws IOException, TermServerScriptException, TermServerClientException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				//Exclude LOINC
				if (lineItems[IDX_MODULEID].equals(excludeModule)) {
					continue;
				}
				Description d = getDescription(lineItems[LANG_IDX_REFCOMPID]);
				LangRefsetEntry langRefsetEntry = LangRefsetEntry.fromRf2(lineItems);
				
				//Are we adding or replacing this entry?
				if (d.getLangRefsetEntries().contains(langRefsetEntry)) {
					d.getLangRefsetEntries().remove(langRefsetEntry);
				}
				
				//Complexity here that we've historically had language refset entries
				//for the same description which attempt to cancel each other out using
				//different UUIDs.  Therefore if we get a later entry inactivating a given
				//dialect, then allow that to overwrite an earlier value with a different UUUID
				
				//Do we have an existing entry for this description & dialect that is later and inactive?
				boolean clearToAdd = true;
				String issue = "";
				List<LangRefsetEntry> allExisting = d.getLangRefsetEntries(ActiveState.BOTH, langRefsetEntry.getRefsetId());
				for (LangRefsetEntry existing : allExisting) {
					if (existing.getEffectiveTime().compareTo(langRefsetEntry.getEffectiveTime()) > 1) {
						clearToAdd = false;
						issue = "Existing " + (existing.isActive()? "active":"inactive") +  " langrefset entry taking priority as later : " + existing;
					} else {
						//New entry is later than one we already know about
						d.getLangRefsetEntries().remove(existing);
						issue = "Existing langrefset entry being overwritten by subsequent value " + existing;
					}
				}
				
				if (!issue.isEmpty()) {
					System.out.println("**Warning: " + issue);
				}
				
				if (clearToAdd) {
					d.getLangRefsetEntries().add(langRefsetEntry);
					if (lineItems[LANG_IDX_ACTIVE].equals("1")) {
						Acceptability a = SnomedUtils.translateAcceptability(lineItems[LANG_IDX_ACCEPTABILITY_ID]);
						d.setAcceptablity(lineItems[LANG_IDX_REFSETID], a);
					} else {
						d.removeAcceptability(lineItems[LANG_IDX_REFSETID]);
					}
				}
			} else {
				isHeaderLine = false;
			}
		}
	}

	
	/**
	 * Recurse hierarchy and set shortest path depth for all concepts
	 * @throws TermServerScriptException 
	 */
	public void populateHierarchyDepth(Concept startingPoint, int currentDepth) throws TermServerScriptException {
		startingPoint.setDepth(currentDepth);
		/*if (startingPoint.getConceptId().equals("210431006")) {
			//TermServerScript.debug ("Checkpoint");
		}*/
		
		for (Concept child : startingPoint.getDescendents(IMMEDIATE_CHILD, CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (currentDepth >= MAX_DEPTH) {
				throw new TermServerScriptException("Maximum depth exceeded from " + startingPoint + " and inferred child " + child);
			}
			try {
				populateHierarchyDepth(child, currentDepth + 1);
			} catch (TermServerScriptException e) {
				TermServerScript.debug ("Exception path: " + startingPoint + " -> " + child);
				throw (e);
			}
		}
	}

	public void loadInactivationIndicatorFile(InputStream is) throws IOException, TermServerScriptException, TermServerClientException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				//Exclude LOINC
				if (lineItems[IDX_MODULEID].equals(excludeModule)) {
					continue;
				}
				InactivationIndicatorEntry inactivation = InactivationIndicatorEntry.fromRf2(lineItems);
				if (inactivation.getRefsetId().equals(SCTID_CON_INACT_IND_REFSET)) {
					Concept c = getConcept(lineItems[INACT_IDX_REFCOMPID]);
					/*if (c.getConceptId().equals("198308002")) {
						System.out.println("Check Here");
					}*/
					c.addInactivationIndicator(inactivation);
				} else if (inactivation.getRefsetId().equals(SCTID_DESC_INACT_IND_REFSET)) {
					Description d = getDescription(lineItems[INACT_IDX_REFCOMPID]);
					/*if (d.getDescriptionId().equals("1221136011")) {
						System.out.println("Check here");
					}*/
					d.addInactivationIndicator(inactivation);
				}
			} else {
				isHeaderLine = false;
			}
		}
	}
	
	public void loadHistoricalAssociationFile(InputStream is) throws IOException, TermServerScriptException, TermServerClientException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				//Exclude LOINC
				if (lineItems[IDX_MODULEID].equals(excludeModule)) {
					continue;
				}
				String referencedComponent = lineItems[INACT_IDX_REFCOMPID];
				if (isConcept(referencedComponent)) {
					Concept c = getConcept(referencedComponent);
					AssociationEntry historicalAssociation = loadHistoricalAssociationLine(lineItems);
					//Remove first in case we're replacing
					c.getAssociations().remove(historicalAssociation);
					c.getAssociations().add(historicalAssociation);
					if (historicalAssociation.isActive()) {
						addHistoricalAssociationInTsForm(c, historicalAssociation);
						recordHistoricalAssociation(historicalAssociation);
					}
				}
			} else {
				isHeaderLine = false;
			}
		}
	}
	
	/*
	 * Adds a historical association using the string based map format as per the Terminology Server's API
	 */
	private void addHistoricalAssociationInTsForm(Concept c, AssociationEntry historicalAssociation) {
		AssociationTargets targets = c.getAssociationTargets();
		if (targets == null) {
			targets = new AssociationTargets();
			c.setAssociationTargets(targets);
		}
		String target = historicalAssociation.getTargetComponentId();
		switch (historicalAssociation.getRefsetId()) {
			case (SCTID_ASSOC_REPLACED_BY_REFSETID) : targets.getReplacedBy().add(target);
													break;
			case (SCTID_ASSOC_SAME_AS_REFSETID) : targets.getSameAs().add(target);
													break;	
			case (SCTID_ASSOC_POSS_EQUIV_REFSETID) : targets.getPossEquivTo().add(target);
													break;	
		}
		
	}

	private void recordHistoricalAssociation(AssociationEntry h) throws TermServerScriptException {
		//Remember we're using the target of the association as the map key here
		Concept target = getConcept(h.getTargetComponentId());
		//Have we seen this concept before?
		List<AssociationEntry> associations;
		if (historicalAssociations.containsKey(target)) {
			associations = historicalAssociations.get(target);
		} else {
			associations = new ArrayList<AssociationEntry>();
			historicalAssociations.put(target, associations);
		}
		associations.add(h);
	}
	
	public List<AssociationEntry> usedAsHistoricalAssociationTarget (Concept c) {
		if (historicalAssociations.containsKey(c)) {
			return historicalAssociations.get(c);
		}
		return new ArrayList<AssociationEntry>();
	}

	private AssociationEntry loadHistoricalAssociationLine(String[] lineItems) {
		AssociationEntry h = new AssociationEntry();
		h.setId(lineItems[ASSOC_IDX_ID]);
		h.setEffectiveTime(lineItems[ASSOC_IDX_EFFECTIVETIME]);
		h.setActive(lineItems[ASSOC_IDX_ACTIVE].equals("1"));
		h.setModuleId(lineItems[ASSOC_IDX_MODULID]);
		h.setRefsetId(lineItems[ASSOC_IDX_REFSETID]);
		h.setReferencedComponentId(lineItems[ASSOC_IDX_REFCOMPID]);
		h.setTargetComponentId(lineItems[ASSOC_IDX_TARGET]);
		return h;
	}
	
	public Component getComponent(String id) {
		if (allComponents == null) {
			populateAllComponents();
		}
		return allComponents.get(id);
	}
	
	public Concept getComponentOwner(String id) {
		Component component = getComponent(id);
		return componentOwnerMap.get(component);
	}

	private void populateAllComponents() {
		System.out.print("Populating map of all components...");
		allComponents = new HashMap<String, Component>();
		componentOwnerMap = new HashMap<Component, Concept>();
		
		for (Concept c : getAllConcepts()) {
			
			/*if (c.getId().equals("773986009") || c.getId().equals("762566005")) {
				System.out.println("here");
			}*/
			allComponents.put(c.getId(), c);
			componentOwnerMap.put(c,  c);
			for (Description d : c.getDescriptions()) {
				populateDescriptionComponents(c, d);
			}
			
			for (Relationship r : c.getRelationships()) {
				allComponents.put(r.getRelationshipId(), r);
			}

			for (InactivationIndicatorEntry i : c.getInactivationIndicatorEntries()) {
				allComponents.put(i.getId(), i);
				componentOwnerMap.put(i,  c);
			}
			
			for (AssociationEntry h : c.getAssociations()) {
				allComponents.put(h.getId(), h);
				componentOwnerMap.put(h,  c);
			}
		}
		System.out.println("complete.");
	}

	private void populateDescriptionComponents(Concept c, Description d) {
		allComponents.put(d.getDescriptionId(), d);
		componentOwnerMap.put(d, c);
		
		for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries()) {
			allComponents.put(i.getId(), i);
			componentOwnerMap.put(i, c);
		}
		
		for (LangRefsetEntry l : d.getLangRefsetEntries()) {
			allComponents.put(l.getId(), l);
			componentOwnerMap.put(l, c);
		}
	}

	public String listAssociationParticipation(Concept c) {
		return historicalAssociations.get(c).stream()
				.map(assoc -> assoc.toVerboseString())
				.collect (Collectors.joining(", "));
	}

	public void registerConcept(Concept concept) {
		concepts.put(concept.getConceptId(), concept);
	}

	public DescendentsCache getDescendantsCache() {
		return descendantsCache;
	}

	public AncestorsCache getAncestorsCache() {
		return ancestorsCache;
	}
	
	public AncestorsCache getStatedAncestorsCache() {
		return statedAncestorsCache;
	}

	public Concept getComponentOwner(ComponentType componentType, Component c) throws TermServerScriptException {
		if (c==null) {
			return null;
		}
		//Work out the owning component given the known type of this component
		switch (componentType) {
			case CONCEPT : //Concepts own themselves
							return (Concept)c;
			case DESCRIPTION :
			case TEXT_DEFINITION : return getConcept(((Description)c).getConceptId());
			case RELATIONSHIP:
			case STATED_RELATIONSHIP : return getConcept(((Relationship)c).getSourceId());
			case HISTORICAL_ASSOCIATION : String referencedComponentId = ((AssociationEntry)c).getReferencedComponentId();
											ComponentType referencedType = SnomedUtils.getComponentType(referencedComponentId);
											return getComponentOwner(referencedType, getComponent(referencedComponentId));
			case ATTRIBUTE_VALUE : referencedComponentId = ((InactivationIndicatorEntry)c).getReferencedComponentId();
											referencedType = SnomedUtils.getComponentType(referencedComponentId);
											return getComponentOwner(referencedType, getComponent(referencedComponentId));
			case LANGREFSET : referencedComponentId = ((LangRefsetEntry)c).getReferencedComponentId();
											referencedType = SnomedUtils.getComponentType(referencedComponentId);
											return getComponentOwner(referencedType, getComponent(referencedComponentId));
			default: throw new TermServerScriptException("Unknown component Type: " + componentType);
		}
	}
	
	public Component createComponent(ComponentType componentType, String[] lineItems) throws TermServerScriptException {
		//Work out the owning component given the known type of this component
		switch (componentType) {
			case CONCEPT :	Concept c = getConcept(lineItems[IDX_ID]);
							Concept.fillFromRf2(c, lineItems);
							return c;
			case DESCRIPTION :
			case TEXT_DEFINITION :	Description d = getDescription (lineItems[DES_IDX_ID]);
									Description.fillFromRf2(d,lineItems);
									return d;
			case RELATIONSHIP: return createRelationshipFromRF2(CharacteristicType.INFERRED_RELATIONSHIP, lineItems);
			case STATED_RELATIONSHIP : return createRelationshipFromRF2(CharacteristicType.STATED_RELATIONSHIP, lineItems);
			case HISTORICAL_ASSOCIATION : return loadHistoricalAssociationLine(lineItems);
			case ATTRIBUTE_VALUE : return  InactivationIndicatorEntry.fromRf2(lineItems);
			case LANGREFSET : return LangRefsetEntry.fromRf2(lineItems);
		default: throw new TermServerScriptException("Unknown component Type: " + componentType);
		}
	}
	
	public Collection<Concept> getOrphanetConcepts() {
		if (orphanetConcepts == null) {
			TermServerScript.print("Loading list of Orphanet Concepts...");
			try {
				InputStream is = GraphLoader.class.getResourceAsStream("/data/orphanet_concepts.txt");
				if (is == null) {
					throw new RuntimeException ("Failed to load Orphanet data file - not found.");
				}
				orphanetConcepts = IOUtils.readLines(is, "UTF-8").stream()
						.map( s -> getConceptSafely(s))
						.collect(Collectors.toSet());
			} catch (Exception e) {
				throw new RuntimeException ("Failed to load list of Orphanet Concepts",e);
			}
			TermServerScript.println("complete.");
		}
		return Collections.unmodifiableCollection(orphanetConcepts);
	}

	public boolean isOrphanetConcept (Concept c) {
		return getOrphanetConcepts().contains(c);
	}

	public void makeReady() {
		for (Concept c : concepts.values()) {
			c.setIssue(null);
			for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)) {
				g.setIssues(null);
				g.resetIndicators();
			}
		}
	}
	
	public AxiomRelationshipConversionService getAxiomService() {
		return axiomService;
	}
}

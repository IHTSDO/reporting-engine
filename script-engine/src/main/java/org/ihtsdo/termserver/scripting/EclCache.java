package org.ihtsdo.termserver.scripting;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClient;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ConceptCollection;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang.StringUtils;

import static org.springframework.util.StringUtils.countOccurrencesOf;

public class EclCache implements ScriptConstants {
	private static final Logger LOGGER = LoggerFactory.getLogger(EclCache.class);
	private static final Map <String, EclCache> branchCaches = new HashMap<>();
	private static final  int PAGING_LIMIT = 1000;

	private final TermServerClient tsClient;
	private GraphLoader gl;
	private boolean quiet = false;
	private CharacteristicType charType;
	private final String branch;
	
	private final Map <String, Collection<Concept>> expansionCache = new HashMap<>();

	public static EclCache getCache(String branch, TermServerClient tsClient, GraphLoader gl, boolean quiet) {
		return getCache(branch, tsClient, gl, quiet, CharacteristicType.INFERRED_RELATIONSHIP);
	}
	
	static EclCache getCache(String branch, TermServerClient tsClient, GraphLoader gl, boolean quiet, CharacteristicType charType) {
		String cacheKey = branch + "_" + charType;

		EclCache branchCache = branchCaches.computeIfAbsent(cacheKey,
				key -> new EclCache(tsClient, branch));
		branchCache.gl = gl;
		branchCache.quiet = quiet;
		branchCache.charType = charType;
		return branchCache;
	}
	
	EclCache (TermServerClient tsClient, String branch) {
		this.tsClient = tsClient;
		this.branch = branch;
	}
	
	public static void reset() {
		LOGGER.info("Resetting ECL Cache - all branches wipe");
		branchCaches.clear();
	}
	
	public Collection<Concept> findConcepts(String ecl) throws TermServerScriptException {
		return findConcepts(ecl, true);
	}
	
	protected boolean isCached(String ecl) {
		return expansionCache.containsKey(ecl);
	}
	
	protected Collection<Concept> findConcepts(String ecl, boolean useLocalStoreIfSimple) throws TermServerScriptException {
		return findConcepts(ecl, useLocalStoreIfSimple, charType);
	}
	
	protected Collection<Concept> findConcepts(String ecl, boolean useLocalStoreIfSimple, CharacteristicType charType) throws TermServerScriptException {
		if (StringUtils.isEmpty(ecl)) {
			LOGGER.warn("EclCache asked to find concepts but no ecl specified.  Returning empty set");
			return new ArrayList<>();
		}
		
		if (!this.charType.equals(charType)) {
			throw new IllegalArgumentException("Characteristic Type " + charType + " used with " + this.charType + " cache");
		}
		
		ecl = ecl.trim();
		String machineEcl = SnomedUtils.makeMachineReadable(ecl);

		//Have we been passed some partial ecl that begins and ends with a bracket?
		//However, if we contain 'OR 'or 'MINUS' then this is not safe to do
		if ( (ecl.startsWith("(") && ecl.endsWith(")")) && 
				!(ecl.contains("AND") || ecl.contains("OR") || ecl.contains("MINUS") )) {
			ecl = ecl.substring(1, ecl.length() -1).trim();
		}

		Collection<Concept> allConcepts = findConcepts(ecl, machineEcl, useLocalStoreIfSimple);

		if (allConcepts.isEmpty()) {
			LOGGER.warn("ECL {} recovered 0 concepts.  Check?", ecl);
			expansionCache.remove(ecl);
		} else {
			//Seeing a transient issue where we're getting 0 concepts back on the first call, and the concepts back on a 
			//subsequent call.  So for now, don't cache a null response, and we'll sleep / retry
			//Cache this result
			expansionCache.put(ecl, allConcepts);
		}
		return allConcepts;
	}

	private Collection<Concept> findConcepts(String ecl, String machineEcl, boolean useLocalStoreIfSimple) throws TermServerScriptException {
		//Have we already recovered this ECL?
		if (expansionCache.containsKey(ecl)) {
			return getCachedConcepts(ecl);
		} else if (machineEcl.contains(" OR ") && !machineEcl.contains("(")) {
			//TODO Create class that holds these collections and can
			//iterate through them without copying the objects
			Collection<Concept> combinedSet = new HashSet<>();
			for (String eclFragment : machineEcl.split(" OR ")) {
				LOGGER.debug("Combining request for: {}", eclFragment);
				combinedSet.addAll(findConcepts(eclFragment, useLocalStoreIfSimple));
			}
			expansionCache.put(ecl, combinedSet);
			return combinedSet;
		} else {
			return recoverConceptsLocallyOrFromTS(ecl, useLocalStoreIfSimple);
		}
	}

	private Collection<Concept> recoverConceptsLocallyOrFromTS(String ecl, boolean useLocalStoreIfSimple) throws TermServerScriptException {
		boolean localStorePopulated = gl.getAllConcepts().size() > 100;
		if (useLocalStoreIfSimple && localStorePopulated && ecl.equals("*")) {
			return gl.getAllConcepts();
		} else if (useLocalStoreIfSimple && localStorePopulated && isSimple(ecl)){
			return recoverConceptsLocally(ecl);
		} else {
			return recoverConceptsFromTS(ecl, charType);
		}
	}

	private Collection<Concept> recoverConceptsLocally(String ecl) throws TermServerScriptException {
		DescendantsCache descendantsCache = charType.equals(CharacteristicType.INFERRED_RELATIONSHIP) ? gl.getDescendantsCache() : gl.getStatedDescendantsCache();
		//We might want to modify these sets, so request mutable copies
		Collection<Concept> allConcepts;
		if (ecl.startsWith("<<")) {
			Concept subHierarchy = gl.getConcept(ecl.substring(2).trim());
			if (subHierarchy.equals(ROOT_CONCEPT)) {
				allConcepts = gl.getAllConcepts();
			} else {
				allConcepts = descendantsCache.getDescendantsOrSelf(subHierarchy, true);
			}
		} else if (ecl.startsWith("<")) {
			Concept subHierarchy = gl.getConcept(ecl.substring(1).trim());
			allConcepts = descendantsCache.getDescendants(subHierarchy, true);
		} else {
			//Could this be a single concept?
			try {
				//Try to recover from Graph.  Do not create, validate it exists
				allConcepts = Collections.singleton(gl.getConcept(ecl, false, true));
				LOGGER.warn("Possible single concept used where set expected in template slot? {}", gl.getConcept(ecl));
			}catch (Exception e) {
				throw new IllegalStateException("ECL is not simple: " + ecl);
			}
		}
		LOGGER.debug("Recovered {} concepts for simple ecl from local memory: {}", allConcepts.size(), ecl);
		return allConcepts;
	}

	private Collection<Concept> getCachedConcepts(String ecl) {
		Collection<Concept> cached = expansionCache.get(ecl);
		//Have we reset the GL? Recover full local cached objects if so
		if (!cached.isEmpty() && StringUtils.isEmpty(cached.iterator().next().getFsn())) {
			cached = cached.stream()
					.map(c -> gl.getConceptSafely(c.getId()))
					.collect(Collectors.toSet());
			expansionCache.put(ecl, cached);
		}
		if (!quiet) {
			LOGGER.debug("Recovering cached {} concepts matching '{}'", cached.size(), ecl);
		}
		return cached;
	}

	public static boolean isSimple(String ecl) {
		//Any braces, commas, more than two pipes, hats, colons mark this ecl as not being simple
		boolean isSimple = true;
		if (StringUtils.isEmpty(ecl) || ecl.equals("*")) {
			return true;
		}
		
		if (countOccurrencesOf(ecl, "|") > 2) {
			isSimple = false;
		} else {
			//Need to strip out all FSNs that might contain odd characters
			ecl = SnomedUtils.makeMachineReadable(ecl);
			if (ecl.contains("{") || ecl.contains(",") || ecl.contains("^") 
					|| ecl.contains("(") ||  ecl.contains("!") ||
					ecl.contains(" MINUS ") || ecl.contains(" AND ") || ecl.contains(":")) {
				isSimple = false;
			}
		}
		return isSimple;
	}

	private Set<Concept> recoverConceptsFromTS(String ecl, CharacteristicType charType) throws TermServerScriptException {
		Set<Concept> allConcepts = new HashSet<>();
		boolean allRecovered = false;
		String searchAfter = null;
		int totalRecovered = 0;
		while (!allRecovered) {
			try {
					ConceptCollection collection = tsClient.getConcepts(ecl, branch, charType, searchAfter, PAGING_LIMIT);
					totalRecovered += collection.getItems().size();
					if (searchAfter == null) {
						//First time round, report how many we're receiving.
						LOGGER.debug("Recovering {} concepts matching '{}'", collection.getTotal(), ecl);
					}
					//Populate our local collection with either our locally loaded concepts if available, or
				    //newly created Concept objects if not.
					collectRecoveredConcepts(collection, allConcepts);
					
					//If we've counted more concepts than we currently have, then some duplicates have been lost in the 
					//add to the set
					if (totalRecovered > allConcepts.size()) {
						LOGGER.warn ("Duplicates detected");
					}
					
					//Did we get all the concepts that there are?
					if (totalRecovered < collection.getTotal()) {
						searchAfter = collection.getSearchAfter();
						if (StringUtils.isEmpty(searchAfter)) {
							throw new TermServerScriptException("SearchAfter field required but not populated.");
						}
					} else {
						allRecovered = true;
					}
			} catch (Exception e) {
				throw new TermServerScriptException("Failed to recover concepts using ECL '" + ecl + "' due to " + e.getMessage(),e);
			}
		}
		return allConcepts;
	}

	private void collectRecoveredConcepts(ConceptCollection collection, Set<Concept> allConcepts) {
			//Recover our locally held copy of these concepts (if we have them) so that we have the full hierarchy populated
			allConcepts.addAll(collection.getItems().stream()
					.map(this::createOrRecoverConcept)
					.toList());
	}

	private Concept createOrRecoverConcept(Concept c) {
		//Do we know about this concept?  Use the local copy if so
		Concept localConcept = gl.getConceptSafely(c.getId(), false, false);
		return localConcept == null ? c : localConcept;
	}

	public String getBranch() {
		return branch;
	}

	public String getServer() {
		return tsClient.getServerUrl();
	}
}

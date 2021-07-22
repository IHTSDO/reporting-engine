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
import org.springframework.util.StringUtils;

import com.google.gson.Gson;

public class EclCache implements ScriptConstants {
	
	static Logger logger = LoggerFactory.getLogger(EclCache.class);
	
	private static Map <String, EclCache> branchCaches;
	private static int PAGING_LIMIT = 1000;
	private TermServerClient tsClient;
	private GraphLoader gl;
	boolean quiet = false;
	
	Map <String, Collection<Concept>> expansionCache = new HashMap<>();
	
	static EclCache getCache(String branch, TermServerClient tsClient, Gson gson, GraphLoader gl, boolean quiet) {
		if (branchCaches == null) {
			branchCaches  = new HashMap<>();
		}
		
		EclCache branchCache = branchCaches.get(branch);
		if (branchCache == null) {
			branchCache = new EclCache(tsClient, gson);
			branchCaches.put(branch, branchCache);
		}
		branchCache.gl = gl;
		branchCache.quiet = quiet;
		return branchCache;
	}
	
	EclCache (TermServerClient tsClient,Gson gson) {
		this.tsClient = tsClient;
	}
	
	public static void reset() {
		TermServerScript.info("Resetting ECL Cache - all branches wipe");
		branchCaches = new HashMap<>();
	}
	
	protected Collection<Concept> findConcepts(String branch, String ecl) throws TermServerScriptException {
		return findConcepts(branch, ecl, true);
	}
	
	protected boolean isCached(String ecl) {
		return expansionCache.containsKey(ecl);
	}
	
	protected Collection<Concept> findConcepts(String branch, String ecl, boolean useLocalStoreIfSimple) throws TermServerScriptException {
		if (StringUtils.isEmpty(ecl)) {
			TermServerScript.warn("EclCache asked to find concepts but not ecl specified.  Returning empty set");
			return new ArrayList<>();
		}
		
		ecl = ecl.trim();
		String machineEcl = SnomedUtils.makeMachineReadable(ecl);
		Collection<Concept> allConcepts;
		
		//Have we been passed some partial ecl that begins and ends with a bracket?
		//However if we contain OR or MINUS then this is not safe to do
		if ( (ecl.startsWith("(") && ecl.endsWith(")")) && 
				!(ecl.contains("AND") || ecl.contains("OR") || ecl.contains("MINUS") )) {
			ecl = ecl.substring(1, ecl.length() -1).trim();
		}
		
		//Have we already recovered this ECL?
		if (expansionCache.containsKey(ecl)) {
			Collection<Concept> cached = expansionCache.get(ecl);
			//Have we reset the GL? Recover full local cached objects if so
			if (cached.size() > 0 && StringUtils.isEmpty(cached.iterator().next().getFsn())) {
				Set<Concept> localCopies = cached.stream()
						.map(c -> gl.getConceptSafely(c.getId()))
						.collect(Collectors.toSet());
				cached = localCopies;
				expansionCache.put(ecl, cached);
			}
			if (!quiet) {
				TermServerScript.debug ("Recovering cached " + cached.size() + " concepts matching '" + ecl +"'");
			}
			return cached;
		} else if (machineEcl.contains(" OR ") && !machineEcl.contains("(")) {
			//TODO Create class that holds these collections and can 
			//iterate through them without copying the objects
			Collection<Concept> combinedSet = new HashSet<>();
			for (String eclFragment : machineEcl.split(" OR ")) {
				TermServerScript.debug("Combining request for: " + eclFragment);
				combinedSet.addAll(findConcepts(branch, eclFragment, useLocalStoreIfSimple));
			}
			allConcepts = combinedSet;
			expansionCache.put(ecl, combinedSet);
		} else {
			if (useLocalStoreIfSimple && ecl.equals("*")) {
				allConcepts = gl.getAllConcepts();
			} else if (useLocalStoreIfSimple && isSimple(ecl)){
				//We might want to modify these sets, so request mutable copies
				if (ecl.startsWith("<<")) {
					Concept subhierarchy = gl.getConcept(ecl.substring(2).trim());
					if (subhierarchy.equals(ROOT_CONCEPT)) {
						allConcepts = gl.getAllConcepts();
					} else {
						allConcepts = gl.getDescendantsCache().getDescendentsOrSelf(subhierarchy, true);
					}
				} else if (ecl.startsWith("<")) {
					Concept subhierarchy = gl.getConcept(ecl.substring(1).trim());
					allConcepts = gl.getDescendantsCache().getDescendents(subhierarchy, true);
				} else {
					//Could this be a single concept?
					try {
						//Try to recover from Graph.  Do not create, validate it exists
						allConcepts = Collections.singleton(gl.getConcept(ecl, false, true));
						logger.warn("Possible single concept used where set expected in template slot? {}", gl.getConcept(ecl));
					}catch (Exception e) {
						throw new IllegalStateException("ECL is not simple: " + ecl);
					}
				}
				TermServerScript.debug("Recovered " + allConcepts.size() + " concepts for simple ecl from local memory: " + ecl);
			} else {
				allConcepts = recoverConceptsFromTS(branch, ecl);
			}
		}
		
		if (allConcepts.size() == 0) {
			TermServerScript.warn ("ECL " + ecl + " recovered 0 concepts.  Check?");
			expansionCache.remove(ecl);
		} else {
			//Seeing a transient issue where we're getting 0 concepts back on the first call, and the concepts back on a 
			//subsequent call.  So for now, don't cache a null response and we'll sleep / retry
			//Cache this result
			expansionCache.put(ecl, allConcepts);
		}
		return allConcepts;
	}
	
	private boolean isSimple(String ecl) {
		//Any braces, commas, more than two pipes, hats, colons mark this ecl as not being simple
		boolean isSimple = true;
		if (StringUtils.countOccurrencesOf(ecl, "|") > 2) {
			isSimple = false;
		} else {
			//Need to strip out all FSNs that might contain odd characters
			ecl = SnomedUtils.makeMachineReadable(ecl);
			if (ecl.contains("{") || ecl.contains(",") || ecl.contains("^") || ecl.contains("(") ||
					ecl.contains(" MINUS ") || ecl.contains(" AND ") || ecl.contains(":")) {
				isSimple = false;
			}
		}
		return isSimple;
	}

	private Set<Concept> recoverConceptsFromTS(String branch, String ecl) throws TermServerScriptException {
		Set<Concept> allConcepts = new HashSet<>();
		boolean allRecovered = false;
		String searchAfter = null;
		int totalRecovered = 0;
		while (!allRecovered) {
			try {
					ConceptCollection collection = tsClient.getConcepts(ecl, branch, searchAfter, PAGING_LIMIT);
					totalRecovered += collection.getItems().size();
					if (searchAfter == null) {
						//First time round, report how many we're receiving.
						TermServerScript.debug ("Recovering " + collection.getTotal() + " concepts matching '" + ecl +"'");
					}
					
					//Recover our locally held copy of these concepts so that we have the full hierarchy populated
					List<Concept> localCopies = collection.getItems().stream()
							.map(c -> gl.getConceptSafely(c.getId()))
							.collect(Collectors.toList());
					allConcepts.addAll(localCopies);
					
					//If we've counted more concepts than we currently have, then some duplicates have been lost in the 
					//add to the set
					if (totalRecovered > allConcepts.size()) {
						TermServerScript.warn ("Duplicates detected");
					}
					
					//Did we get all the concepts that there are?
					if (totalRecovered < collection.getTotal()) {
						searchAfter = collection.getSearchAfter();
					} else {
						allRecovered = true;
					}
			} catch (Exception e) {
				throw new TermServerScriptException("Failed to recover concepts using ECL '" + ecl + "' due to " + e.getMessage(),e);
			}
		}
		return allConcepts;
	}
}

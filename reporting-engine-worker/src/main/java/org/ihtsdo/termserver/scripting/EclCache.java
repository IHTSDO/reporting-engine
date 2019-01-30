package org.ihtsdo.termserver.scripting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.client.SnowOwlClient;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ConceptCollection;
import org.springframework.util.StringUtils;

import com.google.gson.Gson;

import us.monoid.web.JSONResource;

public class EclCache {
	private static Map <String, EclCache> branchCaches;
	private static int PAGING_LIMIT = 500;
	private static int MAX_RESULTS = 9999;
	private SnowOwlClient tsClient;
	private Gson gson;
	private GraphLoader gl;
	
	Map <String, List<Concept>> expansionCache = new HashMap<>();
	
	static EclCache getCache(String branch, SnowOwlClient tsClient, Gson gson, GraphLoader gl) {
		if (branchCaches == null) {
			branchCaches  = new HashMap<>();
		}
		
		EclCache branchCache = branchCaches.get(branch);
		if (branchCache == null) {
			branchCache = new EclCache(tsClient, gson);
			branchCaches.put(branch, branchCache);
		}
		branchCache.gl = gl;
		return branchCache;
	}
	
	EclCache (SnowOwlClient tsClient,Gson gson) {
		this.tsClient = tsClient;
		this.gson = gson;
	}
	
	public static void reset() {
		branchCaches  = new HashMap<>();
	}
	
	protected List<Concept> findConcepts(String branch, String ecl) throws TermServerScriptException {
		//Have we already recovered this ECL?
		if (expansionCache.containsKey(ecl)) {
			List<Concept> cached = expansionCache.get(ecl);
			//Have we reset the GL? Recover full local cached objects if so
			if (cached.size() > 0 && StringUtils.isEmpty(cached.get(0).getFsn())) {
				List<Concept> localCopies = cached.stream()
						.map(c -> gl.getConceptSafely(c.getId()))
						.collect(Collectors.toList());
				cached = localCopies;
				expansionCache.put(ecl, cached);
			}
			TermServerScript.debug ("Recovering cached " + cached.size() + " concepts matching " + ecl);
			return cached;
		}
		
		List<Concept> allConcepts = new ArrayList<>();
		boolean allRecovered = false;
		int offset = 0;
		String searchAfter = null;
		while (!allRecovered) {
			try {
					JSONResource response = tsClient.getConcepts(ecl, branch, offset, searchAfter, PAGING_LIMIT);
					String json = response.toObject().toString();
					ConceptCollection collection = gson.fromJson(json, ConceptCollection.class);
					if (offset == 0) {
						//First time round, report how many we're receiving.
						TermServerScript.debug ("Recovering " + collection.getTotal() + " concepts matching " + ecl);
						if (collection.getTotal() > 2000) {
							TermServerScript.info ("...which seems rather large, don't you think?");
						}
						
						if (collection.getTotal() > MAX_RESULTS) {
							throw new TermServerScriptException("ECL returned " + collection.getTotal() + " concepts, exceeding limit of " + MAX_RESULTS);
						}
					}
					
					//Recover our locally held copy of these concepts so that we have the full hierarchy populated
					List<Concept> localCopies = collection.getItems().stream()
							.map(c -> gl.getConceptSafely(c.getId()))
							.collect(Collectors.toList());
					allConcepts.addAll(localCopies);
					//Did we get all the concepts that there are?
					if (allConcepts.size() < collection.getTotal()) {
						offset = allConcepts.size();
						searchAfter = collection.getSearchAfter();
					} else {
						allRecovered = true;
					}
			} catch (Exception e) {
				throw new TermServerScriptException("Failed to recover concepts using ECL '" + ecl + "' due to " + e.getMessage(),e);
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
}

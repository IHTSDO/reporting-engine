/**
* Copyright 2014 IHTSDO
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.ihtsdo.snowowl.api.rest.impl.refset;

import static com.b2international.snowowl.datastore.BranchPathUtils.createVersionPath;

import java.util.Collection;

import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.datastore.editor.service.ComponentNotFoundException;
import com.b2international.snowowl.api.domain.ComponentCategory;
import com.b2international.snowowl.snomed.datastore.SnomedConceptIndexEntry;
import com.b2international.snowowl.snomed.datastore.SnomedRefSetBrowser;
import com.b2international.snowowl.snomed.datastore.SnomedTerminologyBrowser;
import com.b2international.snowowl.snomed.datastore.index.refset.SnomedRefSetIndexEntry;
import com.b2international.snowowl.snomed.datastore.services.SnomedBranchRefSetMembershipLookupService;
import org.ihtsdo.snowowl.api.rest.domain.RefSet;
import org.ihtsdo.snowowl.api.rest.domain.RefSetComponent;
import com.google.common.collect.Collections2;

/**Service to retrieve {@link RefSet} header details and Concept details.
 * Used as a end point for Refset service
 *
 */
public class SnomedRefsetServiceImpl {

	
	/**
	 * @param id
	 * @return
	 */
	public RefSet getRefsetHeader(final String version, final String id) {
		
		 SnomedRefSetIndexEntry entry = getRefsetBrowser().getRefSet(createVersionPath(version), id);
		 
		 if (entry == null) {
			
			 throw new ComponentNotFoundException(ComponentCategory.SET.name(), id);
		}
		 return getRefsetConverter().apply(entry);

	}
	
	/**
	 * @param branchPath
	 * @return
	 */
	private ReferenceSetComponentConverter getRefsetComponentConverter(final IBranchPath branchPath) {
		
		return new ReferenceSetComponentConverter(new SnomedBranchRefSetMembershipLookupService(branchPath));
	}
	
	/**
	 * @return
	 */
	private ReferenceSetConverter getRefsetConverter() {
		
		return new ReferenceSetConverter();
	}
	
	
	
	/**Gets concepts as collection of {@link ReferenceSetComponent}. If no concept details found for any given concept 
	 * id then result simply exclude that specific concept id from returned collection instead of returning null or empty
	 * @param ids
	 * @return
	 */
	public Collection<RefSetComponent> getConcepts(final String version, final Iterable<String> ids) {
				
		Collection<SnomedConceptIndexEntry> conceptIndexEntries = getTerminologyBrowser().getConcepts(createVersionPath(version), ids);
		
		final Collection<RefSetComponent> concepts = Collections2.transform(conceptIndexEntries, 
				getRefsetComponentConverter(createVersionPath(version)));

		
		return concepts;
	}

	/**
	 * @return
	 */
	private static SnomedTerminologyBrowser getTerminologyBrowser() {
		return ApplicationContext.getServiceForClass(SnomedTerminologyBrowser.class);
	}
	
	/**
	 * @return
	 */
	private static SnomedRefSetBrowser getRefsetBrowser() {
		return ApplicationContext.getServiceForClass(SnomedRefSetBrowser.class);
	}
	

}
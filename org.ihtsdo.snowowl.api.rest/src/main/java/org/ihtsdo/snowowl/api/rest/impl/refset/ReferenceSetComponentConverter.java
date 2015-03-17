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

import com.b2international.snowowl.snomed.api.domain.ISnomedConcept;
import com.b2international.snowowl.snomed.api.impl.AbstractSnomedComponentConverter;
import com.b2international.snowowl.snomed.api.impl.SnomedConceptConverter;
import com.b2international.snowowl.snomed.datastore.SnomedConceptIndexEntry;
import com.b2international.snowowl.snomed.datastore.services.AbstractSnomedRefSetMembershipLookupService;

import org.ihtsdo.snowowl.api.rest.domain.RefSetComponent;

/**
 *
 */
public class ReferenceSetComponentConverter extends AbstractSnomedComponentConverter<SnomedConceptIndexEntry, RefSetComponent>  {

	final SnomedConceptConverter conceptConvertor;
	
	/**
	 * @param snomedRefSetMembershipLookupService
	 */
	public ReferenceSetComponentConverter(
			AbstractSnomedRefSetMembershipLookupService snomedRefSetMembershipLookupService) {
		
		this.conceptConvertor = new SnomedConceptConverter(snomedRefSetMembershipLookupService);
		
	}
	
	@Override
	public RefSetComponent apply(final SnomedConceptIndexEntry input) {
		
		ISnomedConcept concept = conceptConvertor.apply(input);
		final RefSetComponent result = new RefSetComponent();
		result.setActive(concept.isActive());
		result.setDefinitionStatus(concept.getDefinitionStatus());
		result.setEffectiveTime(concept.getEffectiveTime());
		result.setId(concept.getId());
		result.setModuleId(concept.getModuleId());
		result.setReleased(concept.isReleased());
		result.setSubclassDefinitionStatus(concept.getSubclassDefinitionStatus());
		result.setInactivationIndicator(concept.getInactivationIndicator());
		result.setAssociationTargets(concept.getAssociationTargets());
		result.setTerm(input.getLabel());
		
		return result;
	}
	

}

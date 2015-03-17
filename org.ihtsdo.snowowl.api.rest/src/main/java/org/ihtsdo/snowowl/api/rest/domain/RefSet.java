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
package org.ihtsdo.snowowl.api.rest.domain;

import java.util.Date;

import com.b2international.snowowl.snomed.api.domain.ISnomedComponent;
import com.b2international.snowowl.snomed.snomedrefset.SnomedRefSetType;

/**
 *class represent a Refset with all details but no members
 */
public class RefSet implements ISnomedComponent {

	private String id;
	
	private boolean released;
	
	private String term;
	
	private boolean active;
	
	private Date effectiveTime;
	
	private String moduleId;
	
	private SnomedRefSetType type;

	/**
	 * @return the type
	 */
	public SnomedRefSetType getType() {
		return type;
	}

	/**
	 * @param type the type to set
	 */
	public void setType(SnomedRefSetType type) {
		this.type = type;
	}

	/**
	 * @return the moduleId
	 */
	public String getModuleId() {
		return moduleId;
	}

	/**
	 * @param moduleId the moduleId to set
	 */
	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	/**
	 * @return the active
	 */
	public boolean isActive() {
		return active;
	}

	/**
	 * @param active the active to set
	 */
	public void setActive(boolean active) {
		this.active = active;
	}

	/**
	 * @return the effectiveTime
	 */
	public Date getEffectiveTime() {
		return effectiveTime;
	}

	/**
	 * @param effectiveTime the effectiveTime to set
	 */
	public void setEffectiveTime(Date effectiveTime) {
		this.effectiveTime = effectiveTime;
	}

	/**
	 * @return the term
	 */
	public String getTerm() {
		return term;
	}

	/**
	 * @param term the term to set
	 */
	public void setTerm(String term) {
		this.term = term;
	}

	/* (non-Javadoc)
	 * @see com.b2international.snowowl.api.domain.IComponent#getId()
	 */
	@Override
	public String getId() {
		// TODO Auto-generated method stub
		return this.id;
	}

	/* (non-Javadoc)
	 * @see com.b2international.snowowl.api.domain.IComponent#isReleased()
	 */
	@Override
	public boolean isReleased() {
		// TODO Auto-generated method stub
		return this.released;
	}
	
	public void setId(String id) {
		// TODO Auto-generated method stub
		this.id = id;
	}
	
	public void setReleased(boolean isReleased) {
		// TODO Auto-generated method stub
		this.released = isReleased;
	}
	

}

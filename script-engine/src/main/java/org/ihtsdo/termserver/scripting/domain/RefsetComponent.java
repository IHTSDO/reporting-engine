package org.ihtsdo.termserver.scripting.domain;

import java.util.List;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;

public abstract class RefsetComponent extends Component {
	
	public abstract String getRefsetId();
	
	public abstract String getReferencedComponentId();
	
	protected String deletionEffectiveTime;
	
	protected void commonFieldComparison(RefsetComponent other, List<String> differences, boolean ignoreEffectiveTime) {
		super.commonFieldComparison(other, differences, ignoreEffectiveTime);
		String name = this.getClass().getSimpleName(); 
		
		if (!this.getRefsetId().equals(other.getRefsetId())) {
			differences.add("RefsetId different in " + name + ": " + this.getRefsetId() + " vs " + other.getRefsetId());
		}
		
		if (!this.getReferencedComponentId().equals(other.getReferencedComponentId())) {
			differences.add("ReferencedComponentId different in " + name + ": " + this.getReferencedComponentId() + " vs " + other.getReferencedComponentId());
		}
	}
}

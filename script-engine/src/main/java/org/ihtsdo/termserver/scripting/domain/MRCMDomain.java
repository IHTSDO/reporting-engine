package org.ihtsdo.termserver.scripting.domain;

import java.util.*;

//id,effectiveTime,active,moduleId,refsetId,referencedComponentId
//domainConstraint,parentDomain,proximalPrimitiveConstraint,proximalPrimitiveRefinement
//domainTemplateForPrecoordination,domainTemplateForPostcoordination,guideURL
public class MRCMDomain extends RefsetMember implements ScriptConstants {

	public static String DOMAIN_CONSTRAINT = "domainConstraint";
	public static String PARENT_DOMAIN = "parentDomain";
	public static String PROXIMAL_PRIMITIVE_CONSTRAINT = "proximalPrimitiveConstraint";
	public static String PROXIMAL_PRIMITIVE_REFINEMENT = "proximalPrimitiveRefinement";
	public static String DOMAIN_TEMPLATE_FOR_PRECOORDINATION = "domainTemplateForPrecoordination";
	public static String DOMAIN_TEMPLATE_FOR_POSTCOORDINATION = "domainTemplateForPostcoordination";
	public static String GUIDE_URL = "guideURL";
	
	public static String[] additionalFieldNames = new String[] {DOMAIN_CONSTRAINT, PARENT_DOMAIN, PROXIMAL_PRIMITIVE_CONSTRAINT, 
			PROXIMAL_PRIMITIVE_REFINEMENT, DOMAIN_TEMPLATE_FOR_PRECOORDINATION, DOMAIN_TEMPLATE_FOR_POSTCOORDINATION, GUIDE_URL};
	
	public MRCMDomain clone(String newComponentSctId) {
		MRCMDomain clone = new MRCMDomain();
		clone.id = UUID.randomUUID().toString();
		clone.effectiveTime = null;
		clone.moduleId = this.moduleId;
		clone.active = this.active;
		clone.refsetId = this.refsetId;
		clone.referencedComponentId = newComponentSctId;
		clone.setAdditionalFields(new HashMap<>(this.additionalFields));
		clone.isDirty = true; //New components need to be written to any delta
		return clone;
	}

	public static MRCMDomain fromRf2(String[] lineItems) {
		MRCMDomain r = new MRCMDomain();
		populatefromRf2(r, lineItems, additionalFieldNames);
		return r;
	}
	
}

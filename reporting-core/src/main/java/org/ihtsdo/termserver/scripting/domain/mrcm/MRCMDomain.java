package org.ihtsdo.termserver.scripting.domain.mrcm;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;

//id,effectiveTime,active,moduleId,refsetId,referencedComponentId
//domainConstraint,parentDomain,proximalPrimitiveConstraint,proximalPrimitiveRefinement
//domainTemplateForPrecoordination,domainTemplateForPostcoordination,guideURL
public class MRCMDomain extends RefsetMember implements ScriptConstants {

	public static final String DOMAIN_CONSTRAINT = "domainConstraint";
	public static final  String PARENT_DOMAIN = "parentDomain";
	public static final  String PROXIMAL_PRIMITIVE_CONSTRAINT = "proximalPrimitiveConstraint";
	public static final  String PROXIMAL_PRIMITIVE_REFINEMENT = "proximalPrimitiveRefinement";
	public static final  String DOMAIN_TEMPLATE_FOR_PRECOORDINATION = "domainTemplateForPrecoordination";
	public static final  String DOMAIN_TEMPLATE_FOR_POSTCOORDINATION = "domainTemplateForPostcoordination";
	public static final  String GUIDE_URL = "guideURL";
	
	protected static String[] additionalFieldNames = new String[] {DOMAIN_CONSTRAINT, PARENT_DOMAIN, PROXIMAL_PRIMITIVE_CONSTRAINT,
			PROXIMAL_PRIMITIVE_REFINEMENT, DOMAIN_TEMPLATE_FOR_PRECOORDINATION, DOMAIN_TEMPLATE_FOR_POSTCOORDINATION, GUIDE_URL};

	@Override
	public MRCMDomain clone(String newComponentSctId, boolean keepIds) {
		return (MRCMDomain) populateClone(new MRCMDomain(), newComponentSctId, keepIds);
	}

	public static MRCMDomain fromRf2(String[] lineItems) throws TermServerScriptException {
		MRCMDomain r = new MRCMDomain();
		populatefromRf2(r, lineItems, additionalFieldNames);
		return r;
	}
	
	//Note that because Java does not support polymorphism of variables, only methods,
	//we need to call this method to pick up the field names of descendant types.
	@Override
	public String[] getAdditionalFieldNames() {
		return additionalFieldNames;
	}
	
}

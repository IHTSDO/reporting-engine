package org.ihtsdo.termserver.scripting.fixes;

/*
 * 
All concepts in the module must be primitive.
All concepts in the module must have one and only one stated |Is a| relationship.
 - The parent concept for all concepts in the module must be 373873005| Pharmaceutical / biologic product (product).
All concepts in the module must have one or more Has active ingredient attributes.
 - The attribute values must be a descendant of 105590001|Substance (substance).
All concepts in the module must have one and only one Has dose form attribute.
 - The attribute value must be a descendant of 105904009| Type of drug preparation (qualifier value).
Any plus symbol in the name must be surrounded by single space
 */
public class DrugProductFix extends TermServerFix{

	@Override
	public void doFix(String conceptId, String branchPath)
			throws TermServerFixException {
		// TODO Auto-generated method stub
		
	}

}

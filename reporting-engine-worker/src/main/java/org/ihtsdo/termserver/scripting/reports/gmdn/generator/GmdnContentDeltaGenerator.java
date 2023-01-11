package org.ihtsdo.termserver.scripting.reports.gmdn.generator;

import static org.ihtsdo.termserver.scripting.reports.gmdn.generator.GMDNConstants.*;
import static org.ihtsdo.termserver.scripting.reports.gmdn.generator.GmdnFields.*;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;

import org.ihtsdo.termserver.scripting.reports.gmdn.utils.*;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;

public class GmdnContentDeltaGenerator {

	public void generateDeltaReport(String previousDataFileName, String currentDataFileName) throws Exception {
		if (previousDataFileName == null || currentDataFileName == null) {
			throw new RuntimeException("No GMDN terms xml files specified.");
		} 
		if ( !previousDataFileName.endsWith(XML) || !currentDataFileName.endsWith(XML)) {
			throw new RuntimeException("Specified file is not a XML file!");
		}

		Builder builder = new Builder();
		try {
			Document previous = builder.build(previousDataFileName);
			Elements previousTerms = previous.getRootElement().getChildElements(TERM);
			System.out.println("Previous total terms:" + previousTerms.size() );
			Map<String,Element> previousGmdnTermsMap = new HashMap<>();
			for (int i=0; i < previousTerms.size(); i++) {
				Element element = previousTerms.get(i);
				if (NON_IVD.equals(element.getFirstChildElement(TERM_IS_IVD.getName()).getValue())) {
					previousGmdnTermsMap.put(element.getFirstChildElement(TERM_CODE.getName()).getValue().trim(), element);
				}
			}
			System.out.println("Previous NON-IVD terms total:" + previousGmdnTermsMap.keySet().size());
			Document current = builder.build(currentDataFileName);
			Elements currentTerms = current.getRootElement().getChildElements(TERM);
			System.out.println("Current total terms:" + currentTerms.size() );
			List<Element> activeNodes = new ArrayList<>();
			List<Element> obsoleteNodes = new ArrayList<>();
			List<Element> termChanged = new ArrayList<>();
			List<Element> termDefinitionsChangedOnly = new ArrayList<>();
			List<String> oldTerms = new ArrayList<>();
			List<String> oldTermDefinitions = new ArrayList<>();
			int currentNonIvdTotal=0;
			int termChangedButNotModifiedDate = 0;
			List<Element> modifiedDateNotUpdated = new ArrayList<>();
			int defintionChangedWithDate = 0;
			for (int i=0; i < currentTerms.size(); i++) {
				Element element = currentTerms.get(i);
				if (NON_IVD.equals(element.getFirstChildElement(TERM_IS_IVD.getName()).getValue())) {
					currentNonIvdTotal++;
					String termCode= element.getFirstChildElement(TERM_CODE.getName().trim()).getValue();
					Element previousNode = previousGmdnTermsMap.get(termCode);
					if (previousNode == null) {
						// check the status and report any non active status due to error made in last release by GMDN
						String status = element.getFirstChildElement(TERM_STATUS.getName()).getValue();
						if (!ACTIVE.equals(status)) {
							System.out.println("Found new element with non active status");
							System.out.println(element.getFirstChildElement(TERM_NAME.getName()).getValue());
						}
						// new term
						activeNodes.add(element);
					} else {
						//checking changes from last release
						if (OBSOLETE.equals(element.getFirstChildElement(TERM_STATUS.getName()).getValue()) && ACTIVE.equals(previousNode.getFirstChildElement(TERM_STATUS.getName()).getValue())) {
							obsoleteNodes.add(element);
						} else if (!(previousNode.getFirstChildElement(TERM_NAME.getName()).getValue().trim().equals(element.getFirstChildElement(TERM_NAME.getName()).getValue().trim()))) {
							//term changed
							termChanged.add(element);
							oldTerms.add(previousNode.getFirstChildElement(TERM_NAME.getName()).getValue());
							if (reportModifiedDateNotUpdated(previousNode, element)) {
								modifiedDateNotUpdated.add(element);
								termChangedButNotModifiedDate++;
							}
						} else if (!(previousNode.getFirstChildElement(TERM_DEFINITION.getName()).getValue().equals(element.getFirstChildElement(TERM_DEFINITION.getName()).getValue()))) {
							//term definition changed
							termDefinitionsChangedOnly.add(element);
							oldTermDefinitions.add(previousNode.getFirstChildElement(TERM_DEFINITION.getName()).getValue());
							if (reportModifiedDateNotUpdated(previousNode, element)) {
								modifiedDateNotUpdated.add(element);
							} else {
								defintionChangedWithDate++;
							} 
						} 
					}
				}
			}

			String fileExtension = GMDNConstants.CSV;
			String outputDir = Paths.get(currentDataFileName).getParent().toString() + File.separator;
			System.out.println("Total current NON-IVD terms:" + currentNonIvdTotal);
			
			System.out.println("Total current new active NON-IVD terms:" + activeNodes.size());
			ArrayList<GmdnFields> outputFields = new ArrayList<>();
			for (GmdnFields field : GmdnFields.values()) {
				if (!field.equals(GmdnFields.TERM_IS_IVD)) {
					outputFields.add(field);
				}
			}
				
			GMDNUtils.outputElements(activeNodes, outputDir + "ActiveTermsDelta" + fileExtension,outputFields);

			System.out.println("Total current obsolete NON-IVD terms:" + obsoleteNodes.size());
			GMDNUtils.outputElements(obsoleteNodes, outputDir + "ObsoleteTermsDelta" + fileExtension,outputFields);
			
			System.out.println("Total current NON-IVD terms modified since last release:" + termChanged.size());
			GMDNUtils.outputElementsWithOldTerms(termChanged, outputDir + "ModifiedTermsDelta" + fileExtension, oldTerms);
			
			System.out.println("Total current NON-IVD term definitions modified since last release:" + termDefinitionsChangedOnly.size());
			GMDNUtils.outputElementsWithOldDefinitions(termDefinitionsChangedOnly, outputDir + "ModifiedTermDefinitionDelta" + fileExtension, oldTermDefinitions);
			
			System.out.println("Total terms or term definitions changed but with modified date not being updated:" + modifiedDateNotUpdated.size());
			System.out.println("Among them there are " + termChangedButNotModifiedDate + " for term changes");
			System.out.println("Total definition changed and modified date updated:" + defintionChangedWithDate);
			GMDNUtils.outputElements(modifiedDateNotUpdated, outputDir + "contentChangedButModifiedDateNotUpdated" + fileExtension,outputFields);
		}
		catch (Exception e) { 
			e.printStackTrace();
			throw e;
		}
	}

	private static boolean reportModifiedDateNotUpdated(Element previousNode, Element element) {
		Element modifiedDate = element.getFirstChildElement(MODIFIED_DATE.getName());
		if (modifiedDate == null) {
			System.out.println("No modified date set for element " + element.getFirstChildElement(TERM_NAME.getName()).getValue());
			return false;
		} 
		Element previousModified = previousNode.getFirstChildElement(MODIFIED_DATE.getName());
		if (previousModified!=null && (previousModified.getValue().
				equals(modifiedDate.getValue()))) {
			return true;
		}
		return false;
	}
}

package org.ihtsdo.termserver.scripting.domain;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class Rf2File implements ScriptConstants {

	static Map<ComponentType, Rf2File> snomedRf2Files = new HashMap<ComponentType,Rf2File>();
	static {
		String termDir = "TYPE/Terminology/";
		String refDir =  "TYPE/Refset/";
		snomedRf2Files.put(ComponentType.CONCEPT, new Rf2File("sct2_Concept_TYPE", 
				termDir + "sct2_Concept_TYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\tdefinitionStatusId"));
		snomedRf2Files.put(ComponentType.DESCRIPTION, new Rf2File("sct2_Description_TYPE",  
				termDir + "sct2_Description_TYPE-LNG_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\tconceptId\tlanguageCode\ttypeId\tterm\tcaseSignificanceId"));
		snomedRf2Files.put(ComponentType.TEXT_DEFINITION, new Rf2File("sct2_TextDefinition_TYPE", 
				termDir + "sct2_TextDefinition_Snapshot-LNG_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\tconceptId\tlanguageCode\ttypeId\tterm\tcaseSignificanceId"));
		snomedRf2Files.put(ComponentType.LANGREFSET, new Rf2File("der2_cRefset_LanguageTYPE", 
				refDir + "Language/der2_cRefset_LanguageTYPE-LNG_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tacceptabilityId")); 
		snomedRf2Files.put(ComponentType.INFERRED_RELATIONSHIP, new Rf2File("sct2_Relationship_TYPE",
				termDir + "sct2_Relationship_TYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\tsourceId\tdestinationId\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId"));
		snomedRf2Files.put(ComponentType.STATED_RELATIONSHIP, new Rf2File("sct2_StatedRelationship_TYPE", 
				termDir + "sct2_StatedRelationship_TYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\tsourceId\tdestinationId\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId"));
		/*snomedRf2Files.put(Rf2File.new SnomedRf2File("simplerefset","der2_Refset_SimpleTYPE", 
				refDir + "Content/der2_Refset_SimpleTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId"));*/
		snomedRf2Files.put(ComponentType.HISTORICAL_ASSOCIATION, new Rf2File("AssociationReferenceSetTYPE",  
				refDir + "Content/der2_cRefset_AssociationTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\ttargetComponentId"));
		snomedRf2Files.put(ComponentType.ATTRIBUTE_VALUE, new Rf2File("InactivationIndicatorReferenceSetTYPE", 
				refDir + "Content/der2_cRefset_AttributeValueTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tvalueId"));
		/*snomedRf2Files.put(Rf2File.new SnomedRf2File("extendedmaprefset","der2_iisssccRefset_ExtendedMapTYPE", 
				refDir + "Map/der2_iisssccRefset_ExtendedMapTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tmapGroup\tmapPriority\tmapRule\tmapAdvice\tmapTarget\tcorrelationId\tmapCategoryId"));
		snomedRf2Files.put(Rf2File.new SnomedRf2File("refsetDescriptor", "der2_cciRefset_RefsetDescriptorTYPE",
				refDir + "Metadata/der2_cciRefset_RefsetDescriptorTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tattributeDescription\tattributeType\tattributeOrder"));
		snomedRf2Files.put(Rf2File.new SnomedRf2File("descriptionType", "der2_ciRefset_DescriptionTypeTYPE",
				refDir + "Metadata/der2_ciRefset_DescriptionTypeTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tdescriptionFormat\tdescriptionLength"));
		snomedRf2Files.put(Rf2File.new SnomedRf2File("simplemaprefset","der2_sRefset_SimpleMapTYPE", 
				refDir + "Map/der2_sRefset_SimpleMapTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tmapTarget")); */
	}
	 
	private String filenamePart;
	private String filenameTemplate;
	private String header;

	
	public Rf2File (String filenamePart, String filenameTemplate, String header) {
		this.filenamePart = filenamePart;
		this.filenameTemplate = filenameTemplate;
		this.header = header;
	}

	public String getFilenamePart() {
		return filenamePart;
	}

	public String getFilenameTemplate() {
		return filenameTemplate;
	}

	public String getFilename(String edition, String languageCode, String targetEffectiveTime,
			FileType FileType) {
		return filenameTemplate.replace("EDITION", edition).
				replace("DATE", targetEffectiveTime).
				replace("LNG", languageCode).
				replaceAll(TYPE, getFileType(FileType));
	}
	
	public static String getFileType(FileType FileType) {
		switch (FileType) {
			case DELTA : return DELTA;
			case SNAPSHOT : return SNAPSHOT;
			case FULL : 
			default:return FULL;
		}
	}
	
	public String getHeaderField(int i) {
		return header.split("\t")[i];
	}
	
	public static Rf2File get(ComponentType file) {
		return snomedRf2Files.get(file);
	}
	
	public static Rf2File get(String fileName, FileType fileType) {
		ComponentType type = getComponentType(fileName, fileType);
		return snomedRf2Files.get(type);
	}
	
	public static ComponentType getComponentType(String fileName, FileType fileType) {
		String fileTypeStr = getFileType(fileType);
		for (Map.Entry<ComponentType, Rf2File> mapEntry : snomedRf2Files.entrySet()) {
			String fileNamePart = mapEntry.getValue().getFilenamePart().replace(TYPE, fileTypeStr);
			if (fileName.contains(fileNamePart)) {
				return mapEntry.getKey();
			}
		}
		return null;
	}

	public static String getOutputFile(File outputLocation, ComponentType rf2File, String edition, FileType fileType, String languageCode, String targetEffectiveTime) {
		Rf2File rf2FileObj = snomedRf2Files.get(rf2File);
		String fileName = rf2FileObj.getFilename(edition, languageCode, targetEffectiveTime, fileType);
		return outputLocation + File.separator +  fileName;
	}
	
	public static void outputHeaders(File outputLocation,
			Set<ComponentType> filesProcessed, String edition, FileType fileType,
			String languageCode, String targetEffectiveTime) throws TermServerScriptException, FileNotFoundException, IOException {
		//Loop through all the files known, check if we've already processed it, and if not, output headers
		for (Map.Entry<ComponentType, Rf2File> mapEntry : snomedRf2Files.entrySet()) {
			ComponentType rf2File = mapEntry.getKey();
			if (!filesProcessed.contains(rf2File)) {
				
				String outputFile = getOutputFile(outputLocation, rf2File, edition, fileType, languageCode, targetEffectiveTime);
				SnomedUtils.ensureFileExists(outputFile);
				try(	OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(outputFile, true), StandardCharsets.UTF_8);
						BufferedWriter bw = new BufferedWriter(osw);
						PrintWriter out = new PrintWriter(bw))  {
					out.print(mapEntry.getValue().header + LINE_DELIMITER);
				}
			}
		}
	}

	public String getHeader() {
		return header;
	}
}

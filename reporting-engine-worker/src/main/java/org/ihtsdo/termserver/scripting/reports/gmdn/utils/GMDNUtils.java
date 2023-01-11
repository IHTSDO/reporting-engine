package org.ihtsdo.termserver.scripting.reports.gmdn.utils;

import static org.ihtsdo.termserver.scripting.reports.gmdn.generator.GMDNConstants.*;
import static org.ihtsdo.termserver.scripting.reports.gmdn.generator.GmdnFields.*;


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.ihtsdo.termserver.scripting.reports.gmdn.generator.GMDNConstants;
import org.ihtsdo.termserver.scripting.reports.gmdn.generator.GmdnFields;

import nu.xom.Element;

public class GMDNUtils {
	
	 public static void outputElements( List<Element> nodes, String outputFilename, List<GmdnFields> fields) throws IOException {
		 StringBuilder headerBuilder = new StringBuilder();
		 for (GmdnFields field:fields) {
			 headerBuilder.append(field.getName());
			 headerBuilder.append(GMDNConstants.TAB);
		 }
			try ( BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilename))) {
					writer.write(headerBuilder.toString());
					writer.write(LINE_SEPARATOR);
					for (Element element : nodes) {
						for (GmdnFields field: fields) {
							Element elementField = element.getFirstChildElement(field.getName());
							String fieldValue = elementField == null ? "N/A" : elementField.getValue();
							writer.write(fieldValue);
							writer.write(TAB);
						}
						writer.write(LINE_SEPARATOR);
					}
			 }
		 }
	public static void outputElementsWithOldTerms(List<Element> termChanged,String outputFilename, List<String> oldTerms) throws IOException {
		try ( BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilename))) {
			final String header = "termCode\tprevious_termName\tcurrent_termName\ttermStatus\tcreatedDate\tmodifiedDate";
			writer.write(header);
			writer.write(LINE_SEPARATOR);
			int i=0;
			for (Element element : termChanged) {
				final String termCode = element.getFirstChildElement(TERM_CODE.getName()).getValue();
				writer.write(termCode);
				writer.write(TAB);
				
				writer.write(oldTerms.get(i++));
				writer.write(TAB);
				
				writer.write(element.getFirstChildElement(TERM_NAME.getName()).getValue());
				writer.write(TAB);
				
				final String termStatus = element.getFirstChildElement(TERM_STATUS.getName()).getValue();
				writer.write(termStatus);
				writer.write(TAB);

				final String createdDate = element.getFirstChildElement(CREATED_DATE.getName()).getValue();
				writer.write(createdDate);
				writer.write(TAB);
				
				final String modifiedDate = getModifiedDate(element);
				writer.write(modifiedDate);
				writer.write(TAB);
				writer.write(LINE_SEPARATOR);
			}
		}
	}
	
	
	private static String getModifiedDate(Element element) {
		//From 2018 June release this field is not set when the no modification made since the created date.
		Element modifiedDateElement = element.getFirstChildElement(MODIFIED_DATE.getName());
		return modifiedDateElement != null ? modifiedDateElement.getValue() : "N/A";
	}
	public static void outputElementsWithOldDefinitions(List<Element> termChanged,String outputFilename, List<String> oldDefinitions) throws IOException {
		try ( BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilename))) {
			final String header = "termCode\tprevious_termDefinition\tcurrent_termDefinition\ttermStatus\tcreatedDate\tmodifiedDate";
			writer.write(header);
			writer.write(LINE_SEPARATOR);
			int i=0;
			for (Element element : termChanged) {
				final String termCode = element.getFirstChildElement(TERM_CODE.getName()).getValue();
				writer.write(termCode);
				writer.write(TAB);
				
				writer.write(oldDefinitions.get(i++));
				writer.write(TAB);
				
				writer.write(element.getFirstChildElement(TERM_DEFINITION.getName()).getValue());
				writer.write(TAB);
				
				final String termStatus = element.getFirstChildElement(TERM_STATUS.getName()).getValue();
				writer.write(termStatus);
				writer.write(TAB);
				
				final String createdDate = element.getFirstChildElement(CREATED_DATE.getName()).getValue();
				writer.write(createdDate);
				
				final String modifiedDate = getModifiedDate(element);
				writer.write(modifiedDate);
				writer.write(TAB);
				writer.write(LINE_SEPARATOR);
			}
		}
	}
}

package org.ihtsdo.termserver.scripting.loadscripts;

import com.b2international.snowowl.dsl.SCGStandaloneSetup;
import com.b2international.snowowl.dsl.scg.Expression;
import org.ihtsdo.termserver.scripting.util.Charsets;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ExpressionLoadScript {

	public static void main(String[] args) throws IOException {
		final String refsetPath = "termserver-scripting/release/SnomedCT_LOINC_TechnologyPreview_INT_20150801/RF2Release/Snapshot/" +
				"Refset/Content/xder2_sscccRefset_LOINCExpressionAssociationSnapshot_INT_20150801.txt";
		new ExpressionLoadScript().createConceptsFromExpressionRefset(
				refsetPath);
	}

	private void createConceptsFromExpressionRefset(String refsetPath) throws IOException {
		try (final BufferedReader reader = Files.newBufferedReader(new File(refsetPath).toPath(), Charsets.UTF_8)) {
			String line = reader.readLine();
			for (int i = 0; (line = reader.readLine()) != null && i < 10; i++) {
				final String[] cols = line.split("\\t");
				final String expressionString = cols[7];
				System.out.println(expressionString);
				final Expression expression = (Expression) SCGStandaloneSetup.parse(expressionString);
				System.out.println(expression);
			}
		}
	}

}

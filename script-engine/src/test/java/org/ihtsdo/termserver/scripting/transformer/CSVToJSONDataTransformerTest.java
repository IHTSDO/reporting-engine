package org.ihtsdo.termserver.scripting.transformer;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class CSVToJSONDataTransformerTest {

    DataTransformer dataTransformer = new CSVToJSONDataTransformer();

    @Test
    public void testSingleValidTransformation () throws Exception {
        File input = new File(this.getClass().getClassLoader().getResource("testInputReport1.csv").getFile());
        File expectedOutput = new File(this.getClass().getClassLoader().getResource("testOutput-expected-single.json").getFile());

        File output = File.createTempFile("reportsOutput", ".json");

        dataTransformer.transform(input, output);

        String outputContent = new String(Files.readAllBytes(Paths.get(output.getAbsolutePath())));
        String expectedOutputContent = new String(Files.readAllBytes(Paths.get(expectedOutput.getAbsolutePath()))).replace("\n", "");

        assertEquals(expectedOutputContent, outputContent);
    }
    // TODO Add more tests
}

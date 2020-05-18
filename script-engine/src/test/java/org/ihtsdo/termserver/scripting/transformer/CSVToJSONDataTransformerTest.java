package org.ihtsdo.termserver.scripting.transformer;

import org.junit.Test;

import java.io.File;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class CSVToJSONDataTransformerTest {

    DataTransformer dataTransformer = new CSVToJSONDataTransformer();

    @Test
    public void testSingleValidTransformation () throws Exception {
        File input = new File(
                URLDecoder.decode(
                        this.getClass().getClassLoader().getResource("testInputReport1.csv").getFile(), "UTF-8"));
        File expectedOutput = new File(
                URLDecoder.decode(
                        this.getClass().getClassLoader().getResource("testOutput-expected-single.json").getFile(), "UTF-8"));

        File output = File.createTempFile("reportsOutput", ".json");

        dataTransformer.transform(input, output);

        String outputContent = new String(Files.readAllBytes(Paths.get(output.getAbsolutePath())));
        String expectedOutputContent = new String(Files.readAllBytes(Paths.get(expectedOutput.getAbsolutePath()))).replace("\n", "");

        assertEquals(expectedOutputContent, outputContent);
    }
    // TODO Add more tests
}

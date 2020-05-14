package org.ihtsdo.termserver.scripting.transformer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import org.ihtsdo.termserver.scripting.util.StringUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public class CSVToJSONDataTransformer implements DataTransformer {

    public static final String JSON_ARRAY_START = "[";
    public static final String JSON_ARRAY_END = "]";
    public static final String JSON_ARRAY_SEPARATOR = ",";
    public static final String FILE_EXTENSION = ".json";

    protected CsvMapper csvMapper;

    public CSVToJSONDataTransformer() {
        csvMapper = new CsvMapper();
        csvMapper.enable(CsvParser.Feature.WRAP_AS_ARRAY);
    }

    @Override
    public void transform(File input, File output) throws Exception {
        JsonGenerator jsonGenerator = null;
        try (BufferedWriter outputStream  = new BufferedWriter(new FileWriter(output, false))) {
            // create the Json mapper
            ObjectMapper mapper = new ObjectMapper();
            jsonGenerator = mapper.getFactory().createGenerator(outputStream);

            // start of the report array
            outputStream.write(JSON_ARRAY_START);

            // process all rows
            MappingIterator<String[]> mappingIterator = csvMapper.readerFor(String[].class).readValues(input);
            while (mappingIterator.hasNextValue()) {

                String[] rowOLD = mappingIterator.nextValue();
                String[] row = cleanRow(rowOLD); // clean row
                mapper.writerWithDefaultPrettyPrinter().writeValue(jsonGenerator, row);

                if (mappingIterator.hasNextValue()) {
                    outputStream.write(JSON_ARRAY_SEPARATOR);
                }
            }
            // end of the report array
            outputStream.write(JSON_ARRAY_END);

        } catch (IOException e) {
            throw e;
        } finally {
            // close the generator
            if ( jsonGenerator != null && !jsonGenerator.isClosed()) {
                jsonGenerator.close();
            }
        }
    }

    @Override
    public String getFileExtension() {
        return FILE_EXTENSION;
    }

    private String[] cleanRow(String[] row) {
        return Arrays.stream(row).map(aRow -> {
            if (!StringUtils.isEmpty(aRow)) {
                aRow = aRow.trim();
            }
            return aRow;
        }).toArray(String[]::new);
    }
}

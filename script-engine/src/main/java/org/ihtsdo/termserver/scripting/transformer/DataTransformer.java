package org.ihtsdo.termserver.scripting.transformer;

import java.io.File;

public interface DataTransformer {

    /**
     * Transforms a number file into a combined output file.
     * @param input The input file
     * @param output The output file
     * @throws Exception If there are any errors
     */
    void transform(File input, File output) throws Exception;

    /**
     * Gets the output file extension.
     * @return the file extension
     */
    String getFileExtension();
}

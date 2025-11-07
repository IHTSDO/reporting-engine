package org.ihtsdo.termserver.scripting.code;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

public class SortOutLogger {
	
	private static Logger LOGGER = LoggerFactory.getLogger(SortOutLogger.class);
	
	private static Set<String> loggerMethods = Sets.newHashSet("info", "warn", "debug", "error");
	private static Pattern firstWordPattern  = Pattern.compile("\\s*([\\w\\-]+)");
	private static int filesProcessed = 0;
	
	public static  void main(String[] args) throws IOException {
		if (args.length < 1) {
			LOGGER.warn("Usage:  SortOutLogger  <path to directory to process>");
			try { Thread.sleep(1000); } catch (InterruptedException e) {}
			System.exit(-1);
		}
		
		try (Stream<Path> stream = Files.walk(Paths.get(args[0]))) {
			stream.filter(Files::isRegularFile)
				.filter(f -> f.getFileName().toString().endsWith(".java"))
				.forEach(f -> {
					try {
						process(f);
						filesProcessed++;
					} catch (IOException e) {
						throw new IllegalStateException(e);
					}
				});
		}
		LOGGER.info("Processing of " + filesProcessed + " files complete.");
	}

	private static void process(Path file) throws IOException {
		LOGGER.info("Processing " + file);
		Path tempFile = Paths.get(file.toString() + ".tmp");
		String className = file.getFileName().toString().replace(".java", "");
		//Move the file out of the way - if we've not already been through here
		if (!Files.isReadable(tempFile)) {
			Files.move(file, tempFile);
		}
		
		Scanner input = new Scanner(tempFile);
		PrintWriter output = new PrintWriter(file.toFile()) {
			@Override
			public void println() {
				write('\n');
			}
		};
		FileFlags fileFlags = new FileFlags(className);
		while (input.hasNextLine()) {
			String line = input.nextLine();
			boolean chewNextLine = processLine(line, output, fileFlags);
			if (chewNextLine) {
				//TODO Check what's in this line before chewing this line
				//Sometimes there's code!
				input.nextLine();
			}
		}
		output.flush();
		output.close();
		input.close();
		Files.delete(tempFile);
	}

	private static boolean processLine(String line, PrintWriter output, FileFlags fileFlags) {
		//If we're already importing Logger, no need to add
		if (line.contains("import org.slf4j.Logger")) {
			fileFlags.loggerImported = true;
		}
		
		//If this line is the class definition, then add the import statements above it
		if ((line.contains("public class") || line.contains("public abstract class"))
				&& line.contains(fileFlags.className)
				&& !fileFlags.loggerImported
				&& !fileFlags.classDeclarationEncountered) {
			output.println("");
			output.println("import org.slf4j.Logger;");
			output.println("import org.slf4j.LoggerFactory;");
			output.println("");
			fileFlags.classDeclarationEncountered = true;
			
			output.println(line);
			if (!line.contains("{")) {
				output.println("{");
			}
			output.println("");
			output.println("\tprivate static Logger LOGGER = LoggerFactory.getLogger(" + fileFlags.className + ".class);");
			output.println("");
			return true;  //Chew the next line so we don't end up with 2 x {
		}
		
		//If the line starts with (once we ignore whitespace) one of our logging
		String firstWord = getFirstWord(line);
		if (loggerMethods.contains(firstWord) 
				&& ( line.contains(firstWord + " (") || line.contains(firstWord + "("))) {
			line = line.replace(firstWord, "LOGGER." + firstWord);
		}
		output.println(line);
		return false;
	}
	
	private static String getFirstWord(String line) {
		Matcher m = firstWordPattern.matcher(line);
		if (m.find()) {
			return m.group(1);
		}
		return null;
	}

	private static class FileFlags {
		String className;
		boolean loggerImported = false;
		boolean classDeclarationEncountered = false;
		
		FileFlags(String className) {
			this.className = className;
		}
	}
}

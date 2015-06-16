import org.springframework.util.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class OsgiManifestChecker {

	public static final String IMPORT_PACKAGE = "Import-Package: ";

	public static void main(String[] args) throws IOException {
		checkManifest("/Users/kaikewley/code/so-rest/org.ihtsdo.snowowl.authoring.single.api.rest/target/manifest/MANIFEST.MF");
	}

	private static void checkManifest(String manifestFile) throws IOException {
		File file = new File(manifestFile);
		Assert.isTrue(file.isFile(), "Existing file is required.");
		StringBuilder builder = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			boolean collect = false;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith(IMPORT_PACKAGE)) {
					collect = true;
					line = line.substring(IMPORT_PACKAGE.length());
				} else {
					if (collect && !line.startsWith(" ")) {
						collect = false;
					}
				}
				if (collect) {
					builder.append(line.trim());
				}
			}
		}

		String[] parts = builder.toString().split(",");
		Set<String> imports = new HashSet<>();
		for (String part : parts) {
			if (part.contains(";")) {
				part = part.substring(0, part.indexOf(";"));
			}
			if (!imports.add(part)) {
				System.out.println("Duplicate import " + part);
			}
		}

	}

}

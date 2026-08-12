package org.ihtsdo.termserver.scripting.dao;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class ResourceDataLoader extends AbstractS3Component {
	private static final Logger LOGGER = LoggerFactory.getLogger(ResourceDataLoader.class);

	private static final String[] fileNames = new String[] {
																"acceptable_characters.tsv",
																"acceptable_dose_forms.tsv",
																"aggregated_UK_usage_with_decile.tsv",
																"cs_words.tsv",
																"derivative-locations.tsv",
																"legacy_int_release_summary.json",
																"preposition-exceptions.txt",
																"prepositions.txt",
																"repeated-word-exceptions.txt",
																"us-to-gb-terms-map.txt"
														};

	private boolean initialised = false;

	@Autowired
	public void setResourceLoaderConfig(ResourceLoaderConfig config) {
		initS3Manager(config);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void init() throws TermServerScriptException {
		for (String fileName : fileNames) {
			copyFromS3(Path.of(fileName), Path.of("resources", fileName), true);
		}
		LOGGER.info("Resources download complete");
		initialised = true;
	}

	public String getInitalisationConfirmation() {
		return initialised ? "confirms" : "denies";
	}
}

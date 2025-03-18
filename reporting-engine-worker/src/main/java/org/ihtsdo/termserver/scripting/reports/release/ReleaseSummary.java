package org.ihtsdo.termserver.scripting.reports.release;

import java.io.*;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.dao.ReportDataBroker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.annotations.Expose;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReleaseSummary {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReleaseSummary.class);

	@Expose
	private String[] columnHeadings;
	
	@Expose
	private List<ReleaseSummaryDetail> releases;
	
	public ReleaseSummary() {
		releases = new ArrayList<>();
		columnHeadings = new String[] {
				"Release Date|",
				"Concept|Concepts added",
				"Concept|Modified per release",
				"Concept|Total unique",
				"Description|Added for new concepts",
				"Description|Updated for existing concepts",
				"Description|Total unique",
				"Text Definition|Added for new concepts",
				"Text Definition|Updated for existing concepts",
				"Text Definition|Total unique",
				"Language Refset|Added for new concepts",
				"Language Refset|Updated for existing concepts",
				"Language Refset|Total unique",
				"Axiom|Added for new concepts",
				"Axiom|Updated for existing concepts",
				"Axiom|Total unique",
				"Stated Relationship|Added for new concepts",
				"Stated Relationship|Updated for existing concepts",
				"Stated Relationship|Total unique",
				"Inferred Relationship|Added for new concepts",
				"Inferred Relationship|Updated for existing concepts",
				"Inferred Relationship|Total unique",
				"Concrete Relationship|Added for new concepts",
				"Concrete Relationship|Updated for existing concepts",
				"Concrete Relationship|Total unique"
		};
	}
	
	public static void main(String[] args) throws FileNotFoundException {
		ReleaseSummary rs = ReleaseSummary.loadFromLocal(new File("resources/legacy_int_release_summary.json"));
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(rs);
		LOGGER.info(json);
	}

	public String[] getColumnHeadings() {
		return columnHeadings;
	}

	public void setColumnHeadings(String[] columnHeadings) {
		this.columnHeadings = columnHeadings;
	}

	public List<ReleaseSummaryDetail> getReleases() {
		return releases;
	}

	public void setReleases(List<ReleaseSummaryDetail> releases) {
		this.releases = releases;
	}
	
	public static ReleaseSummary loadFromLocal(File fileName) throws FileNotFoundException {
		Reader reader = new FileReader(fileName);
		JsonElement elem = JsonParser.parseReader(reader);
		Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
		return gson.fromJson(elem, ReleaseSummary.class);
	}

	public void addDetail(ReleaseSummaryDetail releaseDetail) throws TermServerScriptException {
		if (releaseDetail.getEffectiveTime() == null) {
			throw new TermServerScriptException("Cannot add releaseDetail without an EffectiveTime");
		}
		//Do we already have detail for this effective time?  Replace if so
		if (releases.contains(releaseDetail)) {
			boolean moreToRemove = true;
			while (moreToRemove) {
				moreToRemove = releases.remove(releaseDetail);
			}
		}
		releases.add(releaseDetail);
		Collections.sort(releases);
	}

	public static ReleaseSummary loadFromS3(File file, ReportDataBroker broker) throws TermServerScriptException {
		Reader reader = new InputStreamReader(broker.download(file));
		JsonElement elem = JsonParser.parseReader(reader);
		return broker.getGson().fromJson(elem, ReleaseSummary.class);
	}

	public void uploadToS3(File file, ReportDataBroker broker) throws TermServerScriptException {
		String json = broker.getGson().toJson(this);
		broker.upload(file, json);
	}
}

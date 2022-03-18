package org.ihtsdo.termserver.scripting.reports.release;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.List;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.dao.ReportDataBroker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.annotations.Expose;

public class ReleaseSummary {
	
	@Expose
	private String[] columnHeadings;
	
	@Expose
	private List<ReleaseSummaryDetail> releases;
	
	public static void main(String[] args) throws FileNotFoundException {
		ReleaseSummary rs = ReleaseSummary.loadFromLocal(new File("resources/legacy_int_release_summary.json"));
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(rs);
		System.out.println(json);
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

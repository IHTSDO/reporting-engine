package org.ihtsdo.termserver.scripting.domain;

import java.util.*;

import com.google.gson.annotations.*;

public class CodeSystemVersionCollection {
	
	@SerializedName("items")
	@Expose
	List<CodeSystemVersion> items = new ArrayList<>();
	
	@SerializedName("limit")
	@Expose
	int limit;
	
	@SerializedName("offset")
	@Expose
	int offset;
	
	@SerializedName("searchAfter")
	@Expose
	String searchAfter;
	
	@SerializedName("total")
	@Expose
	int total;
	
	public int getLimit() {
		return limit;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}

	public int getOffset() {
		return offset;
	}

	public void setOffset(int offset) {
		this.offset = offset;
	}

	public int getTotal() {
		return total;
	}

	public void setTotal(int total) {
		this.total = total;
	}
	
	public List<CodeSystemVersion> getItems() {
		return items;
	}

	public void setItems(List<CodeSystemVersion> items) {
		this.items = items;
	}

	public String getSearchAfter() {
		return searchAfter;
	}

	public void setSearchAfter(String searchAfter) {
		this.searchAfter = searchAfter;
	}
}

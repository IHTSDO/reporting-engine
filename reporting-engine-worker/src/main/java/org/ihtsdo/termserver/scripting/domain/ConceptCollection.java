package org.ihtsdo.termserver.scripting.domain;

import java.util.*;

import com.google.gson.annotations.*;

public class ConceptCollection {
	
	@SerializedName("items")
	@Expose
	List<Concept> items = new ArrayList<>();
	
	@SerializedName("limit")
	@Expose
	int limit;
	
	@SerializedName("offset")
	@Expose
	int offset;
	
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
	
	public List<Concept> getItems() {
		return items;
	}

	public void setItems(List<Concept> items) {
		this.items = items;
	}
}

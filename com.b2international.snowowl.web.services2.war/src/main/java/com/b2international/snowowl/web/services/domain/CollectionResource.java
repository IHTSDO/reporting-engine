/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Objects;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

/**
 * Represents a collection resource in the RESTful API.
 * 
 * @author mczotter
 * @since 1.0
 */
@ApiModel("Collection")
public class CollectionResource<T> {

	@ApiModelProperty(value = "Collection of items contained in this resource", dataType = "array")
	private List<T> items;

	protected CollectionResource(List<T> items) {
		this.items = items == null ? Collections.<T> emptyList() : items;
	}

	/**
	 * Returns the items associated in the collection.
	 * 
	 * @return
	 */
	public final Collection<T> getItems() {
		return items;
	}

	/**
	 * Creates a new {@link CollectionResource} for the given items.
	 * 
	 * @param items
	 * @return
	 */
	public static <T> CollectionResource<T> of(List<T> items) {
		return new CollectionResource<T>(items);
	}
	
	@Override
	public String toString() {
		return Objects.toStringHelper(CollectionResource.class).add("items", getItems()).toString();
	}

}

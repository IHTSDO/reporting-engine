/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain;

import java.util.List;

import com.google.common.base.Objects;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

/**
 * {@link CollectionResource} containing paging information like offset, limit and total.
 * 
 * @author mczotter
 * @since 1.0
 */
@ApiModel("Pageable Collection")
public class PageableCollectionResource<T> extends CollectionResource<T> {

	@ApiModelProperty("Offset in the total collection")
	private int offset;
	
	@ApiModelProperty("The number of requested maximum items")
	private int limit;
	
	@ApiModelProperty("Total number of results available")
	private int total;

	protected PageableCollectionResource(List<T> items, int offset, int limit, int total) {
		super(items);
		this.offset = offset;
		this.limit = limit;
		this.total = total;
	}

	/**
	 * Returns the offset of this collection resource.
	 * 
	 * @return
	 */
	public final int getOffset() {
		return offset;
	}

	/**
	 * Returns the limit of this collection resource.
	 * 
	 * @return
	 */
	public final int getLimit() {
		return limit;
	}

	/**
	 * Returns the total number of results available.
	 * 
	 * @return
	 */
	public int getTotal() {
		return total;
	}
	
	@Override
	public String toString() {
		return Objects.toStringHelper(PageableCollectionResource.class).add("items", getItems()).add("offset", offset).add("limit", limit)
				.add("total", total).toString();
	}

	/**
	 * Creates a new {@link PageableCollectionResource} from the given items, offset, limit and total arguments.
	 * 
	 * @param items
	 * @param offset
	 * @param limit
	 * @param total
	 * @return
	 */
	public static <T> PageableCollectionResource<T> of(List<T> items, int offset, int limit, int total) {
		return new PageableCollectionResource<T>(items, offset, limit, total);
	}

}

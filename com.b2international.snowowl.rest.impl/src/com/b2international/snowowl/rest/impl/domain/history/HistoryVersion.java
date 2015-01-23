/**
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowl.rest.impl.domain.history;

import com.b2international.snowowl.rest.domain.history.IHistoryVersion;

/**
 * @author akitta
 *
 */
public class HistoryVersion implements IHistoryVersion {

	private int majorVersion;
	private int minorVersion;

	/**
	 * @param majorVersion
	 * @param minorVersion
	 */
	public HistoryVersion(int majorVersion, int minorVersion) {
		this.majorVersion = majorVersion;
		this.minorVersion = minorVersion;
	}

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(final IHistoryVersion version) {
		final int majorVersionDiff = getMajorVersion() - version.getMajorVersion();
		return majorVersionDiff != 0 ? majorVersionDiff : (getMinorVersion() - version.getMinorVersion());
	}

	/* (non-Javadoc)
	 * @see com.b2international.snowowl.rest.api.domain.IVersion#getMajorVersion()
	 */
	@Override
	public int getMajorVersion() {
		return majorVersion;
	}

	/* (non-Javadoc)
	 * @see com.b2international.snowowl.rest.api.domain.IVersion#getMinorVersion()
	 */
	@Override
	public int getMinorVersion() {
		return minorVersion;
	}
}

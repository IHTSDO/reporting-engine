package org.ihtsdo.snowowl.authoring.api.services;

import com.b2international.snowowl.api.domain.IComponentRef;

import java.util.List;
import java.util.Set;

public interface ContentService {
	Set<String> getDescendantIds(IComponentRef ref);
}

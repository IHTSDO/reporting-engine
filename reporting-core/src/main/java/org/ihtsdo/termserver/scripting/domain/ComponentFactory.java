package org.ihtsdo.termserver.scripting.domain;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class ComponentFactory {
    public static Component create(String id) {
        if (id == null) {
            return null;
        } else if (id.contains("-")) {
            return new RefsetMember(id);
        } else if (SnomedUtils.isConceptSctid(id)) {
            return new Concept(id);
        } else if (SnomedUtils.isDescriptionSctid(id)) {
            return new Description(id);
        } else if (SnomedUtils.isRelationshipSctid(id)) {
            return new Relationship(id);
        } else {
            throw new IllegalArgumentException("Cannot determine component type from SCTID: " + id);
        }
    }
}

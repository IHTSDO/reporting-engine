package org.ihtsdo.termserver.scripting.util;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.domain.AlternateIdentifier;
import org.ihtsdo.termserver.scripting.domain.AxiomEntry;
import org.ihtsdo.termserver.scripting.domain.ComponentComparisonResult;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ComponentComparisonHelper {

	private static final Logger LOGGER = LoggerFactory.getLogger(ComponentComparisonHelper.class);

	private ComponentComparisonHelper() {
		// utility class
	}

	// ------------------------------------------------------------
	// Public API
	// ------------------------------------------------------------

	public static List<ComponentComparisonResult> compareComponents(
			Concept left,
			Concept right,
			Set<Component.ComponentType> skipForComparison) {

		List<ComponentComparisonResult> changeSet = new ArrayList<>();

		logCheck(left, right);

		List<Component> leftComponents = getFilteredSortedComponents(left, skipForComparison);
		List<Component> rightComponents = getFilteredComponents(right, skipForComparison);
		Map<String, Component> rightById = mapRightComponentsById(rightComponents);

		Set<Component> matchedRight = new HashSet<>();

		for (Component leftComponent : leftComponents) {
			boolean matched = compareLeftComponent(
					leftComponent,
					rightComponents,
					rightById,
					matchedRight,
					changeSet
			);

			if (!matched) {
				handleRemovedLeftComponent(leftComponent, changeSet);
			}
		}

		addNewRightComponents(leftComponents, rightComponents, matchedRight, changeSet);

		return changeSet;
	}

	// ------------------------------------------------------------
	// Setup / preparation
	// ------------------------------------------------------------

	private static void logCheck(Concept left, Concept right) {
		if (checkMe(left) || checkMe(right)) {
			LOGGER.info("Check Here");
		}
	}

	private static boolean checkMe(Concept c) {
		if (c == null) {
			return false;
		}

		return c.getId().equals("580261010000100");
		// .anyMatch(s -> s.equals("Specimen type"))*/
	}

	private static List<Component> getFilteredSortedComponents(
			Concept concept,
			Set<ComponentType> skipForComparison) {

		return SnomedUtils.getAllComponents(concept).stream()
				.filter(c -> !skipForComparison.contains(c.getComponentType()))
				.sorted(Comparator.comparing(Component::isActiveSafely).reversed())
				.toList();
	}

	private static List<Component> getFilteredComponents(
			Concept concept,
			Set<ComponentType> skipForComparison) {

		return SnomedUtils.getAllComponents(concept).stream()
				.filter(c -> !skipForComparison.contains(c.getComponentType()))
				.toList();
	}

	private static Map<String, Component> mapRightComponentsById(List<Component> rightComponents) {
		return rightComponents.stream()
				.filter(c -> c.getId() != null)
				.collect(Collectors.toMap(Component::getId, Function.identity()));
	}

	// ------------------------------------------------------------
	// Left → Right comparison
	// ------------------------------------------------------------

	private static boolean compareLeftComponent(
			Component left,
			List<Component> rightComponents,
			Map<String, Component> rightById,
			Set<Component> matchedRight,
			List<ComponentComparisonResult> changeSet) {

		if (matchById(left, rightById, matchedRight, changeSet)) {
			return true;
		}

		return matchByHeuristics(left, rightComponents, matchedRight, changeSet);
	}

	private static boolean matchById(
			Component left,
			Map<String, Component> rightById,
			Set<Component> matchedRight,
			List<ComponentComparisonResult> changeSet) {

		if (left.getId() == null) {
			return false;
		}

		Component right = rightById.get(left.getId());
		if (right == null) {
			return false;
		}

		matchedRight.add(right);
		boolean matches = left.matchesMutableFields(right);
		changeSet.add(new ComponentComparisonResult(left, right).recordResult(matches));
		return true;
	}

	private static boolean matchByHeuristics(
			Component left,
			List<Component> rightComponents,
			Set<Component> matchedRight,
			List<ComponentComparisonResult> changeSet) {

		for (Component right : rightComponents) {
			if (matchedRight.contains(right) || !sameClass(left, right)) {
				continue;
			}

			if (left.matchesMutableFields(right)) {
				recordMatch(left, right, matchedRight, changeSet, true);
				return true;
			}

			if (considerSameObject(left, right)) {
				recordMatch(left, right, matchedRight, changeSet, false);
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------
	// Right-only components
	// ------------------------------------------------------------

	private static void addNewRightComponents(
			List<Component> leftComponents,
			List<Component> rightComponents,
			Set<Component> matchedRight,
			List<ComponentComparisonResult> changeSet) {

		for (Component right : rightComponents) {
			if (matchedRight.contains(right)) {
				continue;
			}

			if (!existsEquivalentLeft(right, leftComponents)) {
				changeSet.add(new ComponentComparisonResult(null, right));
			}
		}
	}

	private static boolean existsEquivalentLeft(
			Component right,
			List<Component> leftComponents) {

		for (Component left : leftComponents) {
			if (sameClass(left, right)
					&& (right.matchesMutableFields(left)
					|| hasSingleType(right)
					|| areRefsetMembersForSameReferencedComponent(left, right))) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------
	// Removed components
	// ------------------------------------------------------------

	private static void handleRemovedLeftComponent(
			Component left,
			List<ComponentComparisonResult> changeSet) {

		if (left.isActiveSafely()) {
			changeSet.add(new ComponentComparisonResult(left, null));
		}
	}

	// ------------------------------------------------------------
	// Domain rules
	// ------------------------------------------------------------

	private static boolean considerSameObject(Component left, Component right) {
		return hasSingleType(left)
				|| areRefsetMembersForSameReferencedComponent(left, right)
				|| areAltIdsForSameSchemeAndReferencedComponent(left, right);
	}

	private static boolean sameClass(Component a, Component b) {
		return a.getClass().equals(b.getClass());
	}

	private static void recordMatch(
			Component left,
			Component right,
			Set<Component> matchedRight,
			List<ComponentComparisonResult> changeSet,
			boolean matches) {

		matchedRight.add(right);
		ComponentComparisonResult result = new ComponentComparisonResult(left, right);
		changeSet.add(matches ? result.matches() : result.differs());
	}


	private static boolean areRefsetMembersForSameReferencedComponent(Component leftComponent, Component rightComponent) {
		//If these two components are refset members, and they are for the same referenced component, then they are considered the same
		//basic refset member, although the values contained may differ
		return (leftComponent instanceof RefsetMember leftRM && rightComponent instanceof RefsetMember rightRM
				&& leftRM.getRefsetId().equals(rightRM.getRefsetId())
				&& leftRM.getReferencedComponentId().equals(rightRM.getReferencedComponentId()));
	}

	private static boolean areAltIdsForSameSchemeAndReferencedComponent(Component leftComponent, Component rightComponent) {
		//If these two components are refset members, and they are for the same referenced component, then they are considered the same
		//basic refset member, although the values contained may differ
		return (leftComponent instanceof AlternateIdentifier leftRM && rightComponent instanceof AlternateIdentifier rightRM
				&& leftRM.getIdentifierSchemeId().equals(rightRM.getIdentifierSchemeId())
				&& leftRM.getReferencedComponentId().equals(rightRM.getReferencedComponentId()));
	}

	private static boolean hasSingleType(Component c) {
		return c instanceof AxiomEntry || c instanceof Concept || c instanceof AlternateIdentifier;
	}

}


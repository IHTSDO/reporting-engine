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
		List<Component> rightComponents = getFilteredSortedComponents(right, skipForComparison);

		Map<Class<?>, List<Component>> rightComponentsByClass = rightComponents.stream()
				.collect(Collectors.groupingBy(Component::getClass));

		Map<String, Component> rightComponentsById = rightComponents.stream()
				.filter(c -> c.getId() != null)
				.collect(Collectors.toMap(Component::getId, Function.identity()));

		Set<Component> matchedRight = new HashSet<>();

		for (Component leftComponent : leftComponents) {
			List<Component> rightComponentsOfSameClass = rightComponentsByClass.get(leftComponent.getClass());
			boolean matched = compareLeftComponent(
					leftComponent,
					rightComponentsOfSameClass,
					rightComponentsById,
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
		//Note that we're sorting so that we match active components before inactive ones
		return SnomedUtils.getAllComponents(concept).stream()
				.filter(c -> !skipForComparison.contains(c.getComponentType()))
				.sorted(Comparator.comparing(Component::isActiveSafely).reversed())
				.toList();
	}

	// ------------------------------------------------------------
	// Left → Right comparison
	// ------------------------------------------------------------
	private static boolean compareLeftComponent(
			Component left,
			List<Component> rightComponentsOfSameClass,
			Map<String, Component> rightById,
			Set<Component> matchedRight,
			List<ComponentComparisonResult> changeSet) {

		//If there are no right components of the same class, there's no point in trying to match anything
		if (rightComponentsOfSameClass == null || rightComponentsOfSameClass.isEmpty()) {
			return false;
		}

		if (matchById(left, rightById, matchedRight, changeSet)) {
			return true;
		}
		return matchByHeuristics(left, rightComponentsOfSameClass, matchedRight, changeSet);
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
			List<Component> rightComponentsOfSameClass,
			Set<Component> matchedRight,
			List<ComponentComparisonResult> changeSet) {

		for (Component right : rightComponentsOfSameClass) {
			if (matchedRight.contains(right)) {
				//Already matched this right component to another left component, so skip it
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

		Map<Class<?>, List<Component>> leftComponentsByClass = leftComponents.stream()
				.collect(Collectors.groupingBy(Component::getClass));

		for (Component right : rightComponents) {
			if (matchedRight.contains(right)) {
				continue;
			}
			List<Component> leftComponentsWithThisClass = leftComponentsByClass.get(right.getClass());
			if (leftComponentsWithThisClass == null || !existsEquivalentLeft(right, leftComponentsWithThisClass)) {
				changeSet.add(new ComponentComparisonResult(null, right));
			}
		}
	}

	private static boolean existsEquivalentLeft(
			Component right,
			List<Component> leftComponentsOfSameClass) {

		for (Component left : leftComponentsOfSameClass) {
			if ((right.matchesMutableFields(left)
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


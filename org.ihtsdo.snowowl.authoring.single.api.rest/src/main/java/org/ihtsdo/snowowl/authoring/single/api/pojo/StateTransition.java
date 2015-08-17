package org.ihtsdo.snowowl.authoring.single.api.pojo;

public class StateTransition {
	
	public final static String STATE_NEW = "New";
	public final static String STATE_IN_PROGRESS = "In Progress";
	public final static String STATE_IN_CLASSIFICATION = "In Classification";
	public final static String STATE_IN_REVIEW = "In Review";
	
	public final static String TRANSITION_NEW_TO_IN_PROGRESS = "Start Work";
	public static final String TRANSITION_IN_PROGRESS_TO_IN_CLASSIFICATION = "Classify";
	public static final String TRANSITION_IN_CLASSIFICATION_TO_IN_PROGRESS = "Classification finished"; 
	public static final String TRANSITION_IN_PROGRESS_TO_IN_REVIEW = "Review";
	
	private String initialState;
	private String transition;
	private Boolean transitionSuccessful = null;
	private boolean experiencedException = false;
	private String errorMessage;
	private String finalState;
	
	public StateTransition (String initialState, String transition) {
		this.initialState = initialState;
		this.transition = transition;
	}
	
	public String getInitialState() {
		return initialState;
	}
	public void setInitialState(String initialState) {
		this.initialState = initialState;
	}
	public String getTransition() {
		return transition;
	}
	public void setTransition(String transition) {
		this.transition = transition;
	}

	public boolean transitionSuccessful() {
		return transitionSuccessful == null ? false : transitionSuccessful.booleanValue();
	}
	public void transitionSuccessful(Boolean transitionSuccessful) {
		this.transitionSuccessful = transitionSuccessful;
	}
	public String getErrorMessage() {
		return errorMessage;
	}
	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}
	public String getFinalState() {
		return finalState;
	}
	public void setFinalState(String finalState) {
		this.finalState = finalState;
	}

	public boolean hasInitialState(String currentState) {
		return initialState.equals(currentState);
	}

	public boolean experiencedException() {
		return experiencedException;
	}

	public void experiencedException(boolean experiencedException) {
		this.experiencedException = experiencedException;
	}

}

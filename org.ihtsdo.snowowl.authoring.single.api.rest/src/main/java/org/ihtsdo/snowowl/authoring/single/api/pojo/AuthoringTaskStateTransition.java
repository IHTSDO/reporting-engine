package org.ihtsdo.snowowl.authoring.single.api.pojo;

public class AuthoringTaskStateTransition {
	
	public final static String STATE_NEW = "New";
	public final static String STATE_IN_PROGRESS = "IN PROGRESS";
	
	
	public final static String	TRANSITION_NEW_TO_IN_PROGRESS = "Start Work"; 
	
	private String initialState;
	private String transition;
	private Boolean transitionSuccessful = null;
	private String errorMessage;
	private String finalState;
	
	public AuthoringTaskStateTransition (String initialState, String transition) {
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
	public Boolean transitionSuccessful() {
		return transitionSuccessful;
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

}

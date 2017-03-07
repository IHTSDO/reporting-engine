package org.ihtsdo.snowowl.authoring.batchimport.api.pojo.task;

public class TaskTransferRequest  {

	String currentUser;
	String newUser;

	public TaskTransferRequest (String currentUser, String newUser) {
		this.currentUser = currentUser;
		this.newUser = newUser;
	}	
	public String getCurrentUser() {
		return currentUser;
	}
	public String getNewUser() {
		return newUser;
	}
}
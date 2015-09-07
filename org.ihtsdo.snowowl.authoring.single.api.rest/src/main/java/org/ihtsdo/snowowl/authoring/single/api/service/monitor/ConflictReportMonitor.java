package org.ihtsdo.snowowl.authoring.single.api.service.monitor;

import com.b2international.snowowl.datastore.server.review.ReviewStatus;
import org.ihtsdo.snowowl.authoring.single.api.pojo.ConflictReport;
import org.ihtsdo.snowowl.authoring.single.api.pojo.EntityType;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.ihtsdo.snowowl.authoring.single.api.service.BranchService;

import java.util.concurrent.ExecutionException;

public class ConflictReportMonitor extends Monitor {

	private final ConflictReport conflictReport;
	private final BranchService branchService;
	private final String projectId;
	private final String taskId;
	private boolean stale;

	public ConflictReportMonitor(String projectId, String taskId, ConflictReport conflictReport, BranchService branchService) {
		this.projectId = projectId;
		this.taskId = taskId;
		this.conflictReport = conflictReport;
		this.branchService = branchService;
	}

	@Override
	public Notification runOnce() throws MonitorException {
		if (!stale) {
			if (getStatus(conflictReport.getSourceReviewId()).equals(ReviewStatus.STALE)
					|| getStatus(conflictReport.getTargetReviewId()).equals(ReviewStatus.STALE)) {
				stale = true;
				return new Notification(projectId, taskId, EntityType.ConflictReport, ReviewStatus.STALE.name());
			}
		}
		return null;
	}

	private ReviewStatus getStatus(String sourceReviewId) throws MonitorException {
		ReviewStatus status;
		try {
			status = branchService.getReviewStatus(sourceReviewId);
		} catch (ExecutionException | InterruptedException e) {
			throw new MonitorException("Failed to retrieve the status of review " + sourceReviewId);
		}
		return status;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		ConflictReportMonitor that = (ConflictReportMonitor) o;

		if (conflictReport != null ? !conflictReport.equals(that.conflictReport) : that.conflictReport != null) return false;
		if (projectId != null ? !projectId.equals(that.projectId) : that.projectId != null) return false;
		return !(taskId != null ? !taskId.equals(that.taskId) : that.taskId != null);

	}

	@Override
	public int hashCode() {
		int result = conflictReport != null ? conflictReport.hashCode() : 0;
		result = 31 * result + (projectId != null ? projectId.hashCode() : 0);
		result = 31 * result + (taskId != null ? taskId.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return "ConflictReportMonitor{" +
				"conflictReport=" + conflictReport +
				", projectId='" + projectId + '\'' +
				", taskId='" + taskId + '\'' +
				'}';
	}
}

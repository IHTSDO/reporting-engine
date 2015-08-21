package org.ihtsdo.snowowl.authoring.single.api.review.repository;

import org.ihtsdo.snowowl.authoring.single.api.review.domain.Branch;
import org.ihtsdo.snowowl.authoring.single.api.review.domain.ReviewMessage;
import org.ihtsdo.snowowl.authoring.single.api.review.domain.ReviewMessageRead;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ReviewMessageReadRepository extends CrudRepository<ReviewMessageRead, Long> {

	@Query("select r from ReviewMessageRead as r " +
			"where r.message.branch = ?1 " +
			"and r.username = ?2 ")
	List<ReviewMessageRead> findByReviewMessageBranchAndUser(Branch branch, String username);

	ReviewMessageRead findOneByMessageAndConceptIdAndUsername(ReviewMessage message, String conceptId, String username);
}

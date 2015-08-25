package org.ihtsdo.snowowl.authoring.single.api.review.repository;

import org.ihtsdo.snowowl.authoring.single.api.review.domain.Branch;
import org.ihtsdo.snowowl.authoring.single.api.review.domain.ReviewMessage;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ReviewMessageRepository extends CrudRepository<ReviewMessage, Long> {

	List<ReviewMessage> findByBranch(Branch branch);

	@Query("select m from ReviewMessage as m " +
			"where m.branch = ?1 " +
			"and ?2 member of m.subjectConceptIds ")
	List<ReviewMessage> findByBranchAndConcept(Branch branch, String conceptId);

}

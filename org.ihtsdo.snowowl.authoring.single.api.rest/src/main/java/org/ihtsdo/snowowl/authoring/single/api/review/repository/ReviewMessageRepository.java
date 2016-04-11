package org.ihtsdo.snowowl.authoring.single.api.review.repository;

import org.ihtsdo.snowowl.authoring.single.api.review.domain.Branch;
import org.ihtsdo.snowowl.authoring.single.api.review.domain.ReviewMessage;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ReviewMessageRepository extends CrudRepository<ReviewMessage, Long> {

	List<ReviewMessage> findByBranch(Branch branch);

}

package org.ihtsdo.snowowl.authoring.single.api.review.repository;

import org.ihtsdo.snowowl.authoring.single.api.review.domain.Branch;
import org.ihtsdo.snowowl.authoring.single.api.review.domain.ReviewConceptView;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ReviewConceptViewRepository extends CrudRepository<ReviewConceptView, Long> {

	List<ReviewConceptView> findByBranchAndUsernameOrderByViewDateAsc(Branch branch, String username);

}

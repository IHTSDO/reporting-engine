package org.ihtsdo.snowowl.authoring.single.api.review.repository;

import org.ihtsdo.snowowl.authoring.single.api.review.domain.Branch;
import org.springframework.data.repository.CrudRepository;

public interface BranchRepository extends CrudRepository<Branch, Long> {

	Branch findOneByProjectAndTask(String project, String task);
}

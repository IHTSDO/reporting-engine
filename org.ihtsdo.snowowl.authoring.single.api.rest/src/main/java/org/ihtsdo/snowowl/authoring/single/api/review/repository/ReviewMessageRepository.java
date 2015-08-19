package org.ihtsdo.snowowl.authoring.single.api.review.repository;

import org.ihtsdo.snowowl.authoring.single.api.review.domain.ReviewMessage;
import org.springframework.data.repository.CrudRepository;

public interface ReviewMessageRepository extends CrudRepository<ReviewMessage, Long> {
}

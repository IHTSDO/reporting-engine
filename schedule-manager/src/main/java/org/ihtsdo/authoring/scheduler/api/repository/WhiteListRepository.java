package org.ihtsdo.authoring.scheduler.api.repository;

import org.snomed.otf.scheduler.domain.*;
import org.springframework.data.repository.CrudRepository;

public interface WhiteListRepository extends CrudRepository<WhiteList, Long> {

	WhiteList findOneById(long id);

}

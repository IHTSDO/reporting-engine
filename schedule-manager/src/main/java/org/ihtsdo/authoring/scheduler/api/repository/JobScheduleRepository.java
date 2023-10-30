package org.ihtsdo.authoring.scheduler.api.repository;

import java.util.UUID;

import org.snomed.otf.scheduler.domain.*;
import org.springframework.data.repository.CrudRepository;

public interface JobScheduleRepository extends CrudRepository<JobSchedule, UUID> {

}

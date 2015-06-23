package org.ihtsdo.snowowl.authoring.single.api.rest;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.snowowl.api.rest.common.AbstractRestService;
import org.ihtsdo.snowowl.api.rest.common.AbstractSnomedRestService;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringProject;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTask;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTaskCreateRequest;
import org.ihtsdo.snowowl.authoring.single.api.service.ServiceException;
import org.ihtsdo.snowowl.authoring.single.api.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api("Authoring Projects")
@RestController
@RequestMapping(produces={AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
public class ProjectController extends AbstractSnomedRestService {

	@Autowired
	private TaskService taskService;

	@ApiOperation(value="List authoring projects")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects", method= RequestMethod.GET)
	public List<AuthoringProject> listProjects() throws JiraException {
		return taskService.listProjects();
	}

	@ApiOperation(value="List tasks within a project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks", method= RequestMethod.GET)
	public List<AuthoringTask> listTasks(@PathVariable final String projectKey) throws JiraException {
		return taskService.listTasks(projectKey);
	}

	@ApiOperation(value="Create a task within a project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks", method= RequestMethod.POST)
	public AuthoringTask createTask(@PathVariable final String projectKey, @RequestBody final AuthoringTaskCreateRequest taskCreateRequest) throws JiraException, ServiceException {
		return taskService.createTask(projectKey, taskCreateRequest);
	}

	/** This is planned for a future sprint
	@ApiOperation(value="Playing with MRCM rules.", notes="")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/mrcm", method= RequestMethod.GET)
	public int listMRCMRules() throws IOException {
		SnomedPredicateBrowser predicateBrowser = ApplicationContext.getInstance().getService(SnomedPredicateBrowser.class);
		IBranchPath mainPath = BranchPathUtils.createMainPath();
		Collection<PredicateIndexEntry> predicate = predicateBrowser.getPredicates(mainPath, "361083003", null);
		return predicate.size();
	}
	**/

}

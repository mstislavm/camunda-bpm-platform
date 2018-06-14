/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.test.standalone.history;

import static org.camunda.bpm.engine.EntityTypes.JOB;
import static org.camunda.bpm.engine.EntityTypes.JOB_DEFINITION;
import static org.camunda.bpm.engine.EntityTypes.PROCESS_DEFINITION;
import static org.camunda.bpm.engine.EntityTypes.PROCESS_INSTANCE;
import static org.camunda.bpm.engine.history.UserOperationLogEntry.OPERATION_TYPE_SET_JOB_RETRIES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.camunda.bpm.engine.CaseService;
import org.camunda.bpm.engine.EntityTypes;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.camunda.bpm.engine.history.UserOperationLogQuery;
import org.camunda.bpm.engine.impl.ManagementServiceImpl;
import org.camunda.bpm.engine.impl.RuntimeServiceImpl;
import org.camunda.bpm.engine.impl.TaskServiceImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.history.HistoryLevel;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.api.authorization.util.AuthorizationTestBaseRule;
import org.camunda.bpm.engine.test.util.ProcessEngineBootstrapRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class CustomHistoryLevelWithoutUserOperationLogTest {

  public static final String USER_ID = "demo";
  private static final String ONE_TASK_PROCESS = "org/camunda/bpm/engine/test/api/oneTaskProcess.bpmn20.xml";
  protected static final String ONE_TASK_CASE = "org/camunda/bpm/engine/test/api/cmmn/oneTaskCase.cmmn";

  HistoryLevel customHistoryLevelFull = new CustomHistoryLevelFullWithoutUserOperationLog();
  public ProcessEngineBootstrapRule bootstrapRule = new ProcessEngineBootstrapRule() {
    public ProcessEngineConfiguration configureEngine(ProcessEngineConfigurationImpl configuration) {
      configuration.setJdbcUrl("jdbc:h2:mem:CustomHistoryLevelWithoutUserOperationLogTest");
      configuration.setCustomHistoryLevels(Arrays.asList(customHistoryLevelFull));
      configuration.setHistory("aCustomHistoryLevelWUOL");
      return configuration;
    }
  };

  public ProvidedProcessEngineRule engineRule = new ProvidedProcessEngineRule(bootstrapRule);
  public AuthorizationTestBaseRule authRule = new AuthorizationTestBaseRule(engineRule);
  public ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(bootstrapRule).around(engineRule).around(authRule).around(testRule);

  protected HistoryService historyService;
  protected RuntimeService runtimeService;
  protected ManagementServiceImpl managementService;
  protected IdentityService identityService;
  protected RepositoryService repositoryService;
  protected TaskService taskService;
  protected CaseService caseService;
  protected ProcessEngineConfigurationImpl processEngineConfiguration;

  protected ProcessInstance process;
  protected Task userTask;
  protected String processTaskId;

  @Before
  public void setUp() throws Exception {
    runtimeService = engineRule.getRuntimeService();
    historyService = engineRule.getHistoryService();
    managementService = (ManagementServiceImpl) engineRule.getManagementService();
    identityService = engineRule.getIdentityService();
    repositoryService = engineRule.getRepositoryService();
    taskService = engineRule.getTaskService();
    caseService = engineRule.getCaseService();
    processEngineConfiguration = engineRule.getProcessEngineConfiguration();
    identityService.setAuthenticatedUserId(USER_ID);
  }

  @After
  public void tearDown() throws Exception {
    identityService.clearAuthentication();
    managementService.purge();
  }

  @Test
  @Deployment(resources = {ONE_TASK_PROCESS})
  public void testQueryProcessInstanceOperationsByProcessDefinitionKey() {
    // given
    process = runtimeService.startProcessInstanceByKey("oneTaskProcess");

    // when
    runtimeService.suspendProcessInstanceByProcessDefinitionKey("oneTaskProcess");
    runtimeService.activateProcessInstanceByProcessDefinitionKey("oneTaskProcess");

    // then
    assertEquals(0, query().entityType(PROCESS_INSTANCE).count());
  }

  @Test
  @Deployment(resources = {ONE_TASK_PROCESS})
  public void testQueryProcessDefinitionOperationsById() {
    // given
    process = runtimeService.startProcessInstanceByKey("oneTaskProcess");

    // when
    repositoryService.suspendProcessDefinitionById(process.getProcessDefinitionId(), true, null);
    repositoryService.activateProcessDefinitionById(process.getProcessDefinitionId(), true, null);

    // then
    assertEquals(0, query().entityType(PROCESS_DEFINITION).count());
  }

  @Test
  @Deployment(resources = {"org/camunda/bpm/engine/test/history/HistoricJobLogTest.testAsyncContinuation.bpmn20.xml"})
  public void testQueryJobOperations() {
    // given
    process = runtimeService.startProcessInstanceByKey("process");

    // when
    managementService.suspendJobDefinitionByProcessDefinitionId(process.getProcessDefinitionId());
    managementService.activateJobDefinitionByProcessDefinitionId(process.getProcessDefinitionId());
    managementService.suspendJobByProcessInstanceId(process.getId());
    managementService.activateJobByProcessInstanceId(process.getId());

    // then
    assertEquals(0, query().entityType(JOB_DEFINITION).count());
    assertEquals(0, query().entityType(JOB).count());
  }

  @Test
  @Deployment(resources = { "org/camunda/bpm/engine/test/bpmn/async/FoxJobRetryCmdTest.testFailedServiceTask.bpmn20.xml" })
  public void testQueryJobRetryOperationsById() {
    // given
    process = runtimeService.startProcessInstanceByKey("failedServiceTask");
    Job job = managementService.createJobQuery().processInstanceId(process.getProcessInstanceId()).singleResult();

    managementService.setJobRetries(job.getId(), 10);

    // then
    assertEquals(0, query().entityType(JOB).operationType(OPERATION_TYPE_SET_JOB_RETRIES).count());
  }

  // ----- PROCESS INSTANCE MODIFICATION -----

  @Test
  @Deployment(resources = { ONE_TASK_PROCESS })
  public void testQueryProcessInstanceModificationOperation() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    processInstance.getId();

    repositoryService.createProcessDefinitionQuery().singleResult();

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .startBeforeActivity("theTask")
      .execute();

    UserOperationLogQuery logQuery = query()
      .entityType(EntityTypes.PROCESS_INSTANCE)
      .operationType(UserOperationLogEntry.OPERATION_TYPE_MODIFY_PROCESS_INSTANCE);

    assertEquals(0, logQuery.count());
  }

  // ----- ADD VARIABLES -----

  @Test
  @Deployment(resources = {ONE_TASK_PROCESS})
  public void testQueryAddExecutionVariablesMapOperation() {
    // given
    process = runtimeService.startProcessInstanceByKey("oneTaskProcess");

    // when
    runtimeService.setVariables(process.getId(), createMapForVariableAddition());

    // then
    verifyVariableOperationAsserts(UserOperationLogEntry.OPERATION_TYPE_SET_VARIABLE);
  }

  @Test
  @Deployment(resources = {ONE_TASK_PROCESS})
  public void testQueryAddTaskVariableOperation() {
    // given
    process = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    processTaskId = taskService.createTaskQuery().singleResult().getId();

    // when
    taskService.setVariable(processTaskId, "testVariable1", "THIS IS TESTVARIABLE!!!");

    // then
    verifyVariableOperationAsserts(UserOperationLogEntry.OPERATION_TYPE_SET_VARIABLE);
  }

  // ----- PATCH VARIABLES -----

    @Test  @Deployment(resources = {ONE_TASK_PROCESS})
  public void testQueryPatchExecutionVariablesOperation() {
    // given
    process = runtimeService.startProcessInstanceByKey("oneTaskProcess");

    // when
    ((RuntimeServiceImpl) runtimeService)
      .updateVariables(process.getId(), createMapForVariableAddition(), createCollectionForVariableDeletion());

    // then
   verifyVariableOperationAsserts(UserOperationLogEntry.OPERATION_TYPE_MODIFY_VARIABLE);
  }

  @Test
  @Deployment(resources = {ONE_TASK_PROCESS})
  public void testQueryPatchTaskVariablesOperation() {
    // given
    process = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    processTaskId = taskService.createTaskQuery().singleResult().getId();

    // when
    ((TaskServiceImpl) taskService)
      .updateVariablesLocal(processTaskId, createMapForVariableAddition(), createCollectionForVariableDeletion());

    // then
    verifyVariableOperationAsserts(UserOperationLogEntry.OPERATION_TYPE_MODIFY_VARIABLE);
  }

  // ----- REMOVE VARIABLES -----

  @Test
  @Deployment(resources = {ONE_TASK_PROCESS})
  public void testQueryRemoveExecutionVariableOperation() {
    // given
    process = runtimeService.startProcessInstanceByKey("oneTaskProcess");

    // when
    runtimeService.removeVariable(process.getId(), "testVariable1");

    // then
    verifyVariableOperationAsserts(UserOperationLogEntry.OPERATION_TYPE_REMOVE_VARIABLE);
  }

  @Test
  @Deployment(resources = {ONE_TASK_PROCESS})
  public void testQueryByEntityTypes() {
    // given
    process = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    processTaskId = taskService.createTaskQuery().singleResult().getId();

    // when
    taskService.setAssignee(processTaskId, "foo");
    taskService.setVariable(processTaskId, "foo", "bar");

    // then
    UserOperationLogQuery query = historyService
        .createUserOperationLogQuery()
        .entityTypeIn(EntityTypes.TASK, EntityTypes.VARIABLE);

    assertEquals(0, query.count());
  }

  // --------------- CMMN --------------------

  @Test
  @Deployment(resources={ONE_TASK_CASE})
  public void testQueryByCaseDefinitionId() {
    // given:
    // a deployed case definition
    String caseDefinitionId = repositoryService
        .createCaseDefinitionQuery()
        .singleResult()
        .getId();

    // an active case instance
    caseService
       .withCaseDefinition(caseDefinitionId)
       .create();

    Task task = taskService.createTaskQuery().singleResult();

    assertNotNull(task);

    // when
    taskService.setAssignee(task.getId(), "demo");

    // then

    UserOperationLogQuery query = historyService
      .createUserOperationLogQuery()
      .caseDefinitionId(caseDefinitionId);

    assertEquals(0, query.count());

    taskService.setAssignee(task.getId(), null);
  }

  @Test
  public void testQueryByDeploymentId() {
    // given
    String deploymentId = repositoryService
        .createDeployment()
        .addClasspathResource(ONE_TASK_PROCESS)
        .deploy()
        .getId();

    // when
    UserOperationLogQuery query = historyService
        .createUserOperationLogQuery()
        .deploymentId(deploymentId);

    // then
    assertEquals(0, query.count());

    repositoryService.deleteDeployment(deploymentId, true);
  }

  protected Map<String, Object> createMapForVariableAddition() {
    Map<String, Object> variables =  new HashMap<String, Object>();
    variables.put("testVariable1", "THIS IS TESTVARIABLE!!!");
    variables.put("testVariable2", "OVER 9000!");

    return variables;
  }

  protected Collection<String> createCollectionForVariableDeletion() {
    Collection<String> variables = new ArrayList<String>();
    variables.add("testVariable3");
    variables.add("testVariable4");

    return variables;
  }

  protected void verifyVariableOperationAsserts(String operationType) {
    UserOperationLogQuery logQuery = query().entityType(EntityTypes.VARIABLE).operationType(operationType);
    assertEquals(0, logQuery.count());
  }

  protected UserOperationLogQuery query() {
    return historyService.createUserOperationLogQuery();
  }

}

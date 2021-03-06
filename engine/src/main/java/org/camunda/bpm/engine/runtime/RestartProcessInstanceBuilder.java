/*
 * Copyright © 2012 - 2018 camunda services GmbH and various authors (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.runtime;

import java.util.List;

import org.camunda.bpm.engine.AuthorizationException;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.batch.Batch;
import org.camunda.bpm.engine.history.HistoricProcessInstanceQuery;

/**
 * 
 * @author Anna Pazola
 *
 */

public interface RestartProcessInstanceBuilder extends InstantiationBuilder<RestartProcessInstanceBuilder> {

  /**
   * @param query a query which selects the historic process instances to restart.
   *   Query results are restricted to process instances for which the user has {@link Permissions#READ_HISTORY} permission.
   */
  RestartProcessInstanceBuilder historicProcessInstanceQuery(HistoricProcessInstanceQuery query);

  /**
   * @param processInstanceIds the process instance ids to restart.
   */
  RestartProcessInstanceBuilder processInstanceIds(String... processInstanceIds);

  /**
   * @param processInstanceIds the process instance ids to restart.
   */
  RestartProcessInstanceBuilder processInstanceIds(List<String> processInstanceIds);

  /**
   * Sets the initial set of variables during restart. By default, the last set of variables is used
   */
  RestartProcessInstanceBuilder initialSetOfVariables();

  /**
   * Does not take over the business key of the historic process instance
   */
  RestartProcessInstanceBuilder withoutBusinessKey();

  /**
   * Skips custom execution listeners when creating activity instances during restart
   */
  RestartProcessInstanceBuilder skipCustomListeners();

  /**
   * Skips io mappings when creating activity instances during restart
   */
  RestartProcessInstanceBuilder skipIoMappings();

  /**
   * Executes the restart synchronously.
   */
  void execute();

  /**
   * Executes the restart asynchronously as batch. The returned batch
   * can be used to track the progress of the restart.
   *
   * @return the batch which executes the restart asynchronously.
   *
   * @throws AuthorizationException
   *   if the user has not all of the following permissions
   *   <ul>
   *     <li>{@link Permissions#CREATE} permission on {@link Resources#BATCH}</li>
   *   </ul>
   */
  Batch executeAsync();

}

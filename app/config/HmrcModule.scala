/*
 * Copyright 2022 HM Revenue & Customs
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

package config

import connectors.{AgentClientAuthorisationConnector, CachingAgentClientAuthorisationConnector, DefaultAgentClientAuthorisationConnector}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}

class HmrcModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    val useAgentClientAuthorisationCache = configuration
      .getOptional[Boolean]("feature.agent-client-authorisation.cached")
      .getOrElse(true)
    if (useAgentClientAuthorisationCache)
      Seq(
        bind[AgentClientAuthorisationConnector].to[CachingAgentClientAuthorisationConnector],
        bind[AgentClientAuthorisationConnector].qualifiedWith("default").to[DefaultAgentClientAuthorisationConnector]
      )
    else
      Seq(
        bind[AgentClientAuthorisationConnector].to[DefaultAgentClientAuthorisationConnector]
      )
  }
}

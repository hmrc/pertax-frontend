/*
 * Copyright 2024 HM Revenue & Customs
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

import connectors.{AgentClientAuthorisationConnector, CachingAgentClientAuthorisationConnector, CachingCitizenDetailsConnector, CitizenDetailsConnector, DefaultAgentClientAuthorisationConnector, DefaultCitizenDetailsConnector}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

import java.time.{Clock, ZoneId}

class HmrcModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    val defaultBindings: Seq[Binding[_]] = Seq(
      bind[Clock].toInstance(Clock.systemDefaultZone.withZone(ZoneId.of("Europe/London"))),
      bind[ApplicationStartUp].toSelf.eagerly(),
      bind[CitizenDetailsConnector].qualifiedWith("default").to[DefaultCitizenDetailsConnector],
      bind[CitizenDetailsConnector].to[CachingCitizenDetailsConnector],
      bind[Encrypter with Decrypter].toProvider[CryptoProvider]
    )

    val useAgentClientAuthorisationCache = configuration
      .getOptional[Boolean]("feature.agent-client-relationships.cached")
      .getOrElse(true)

    if (useAgentClientAuthorisationCache) {
      Seq(
        bind[AgentClientAuthorisationConnector].to[CachingAgentClientAuthorisationConnector],
        bind[AgentClientAuthorisationConnector].qualifiedWith("default").to[DefaultAgentClientAuthorisationConnector]
      ) ++ defaultBindings
    } else {
      Seq(
        bind[AgentClientAuthorisationConnector].to[DefaultAgentClientAuthorisationConnector]
      ) ++ defaultBindings
    }
  }
}

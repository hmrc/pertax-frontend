/*
 * Copyright 2017 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import connectors.PertaxAuditConnector
import play.api.{Configuration, Play}


/**
  * This class exists to keep all references to static dependencies in one place, before we can remove it all together
  */
@Singleton
class GlobalDependencies @Inject()(
  val pertaxAuditConnector: PertaxAuditConnector,
  val configuration: Configuration
)

/**
  * This class exists to keep all references to static dependencies in one place, before we can remove it all together
  */
object StaticGlobalDependencies {
  val deps = Play.current.injector.instanceOf[GlobalDependencies]
}

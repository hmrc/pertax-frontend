/*
 * Copyright 2018 HM Revenue & Customs
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

package filters

import javax.inject.{Inject, Singleton}

import com.typesafe.config.Config
import play.api.{Configuration, Play}
import uk.gov.hmrc.play.config.ControllerConfig
import net.ceedubs.ficus.Ficus._
import uk.gov.hmrc.play.frontend.filters.{ FrontendLoggingFilter, MicroserviceFilterSupport }


@Singleton
class LocalLoggingFilter @Inject() (val config: Configuration) extends FrontendLoggingFilter with MicroserviceFilterSupport with ControllerConfig {

  lazy val controllerConfigs = config.underlying.as[Config]("controllers")
  override def controllerNeedsLogging(controllerName: String) = paramsForController(controllerName).needsLogging
}

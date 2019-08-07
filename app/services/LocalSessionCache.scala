/*
 * Copyright 2019 HM Revenue & Customs
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

package services

import javax.inject.{Inject, Singleton}
import config.ConfigDecorator
import play.api.{Configuration, Environment}
import play.api.Mode.Mode
import services.http.WsAllMethods
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.config.{AppName, ServicesConfig}
@Singleton
class LocalSessionCache @Inject()(
  environment: Environment,
  configuration: Configuration,
  val appNameConfiguration: Configuration,
  override val http: WsAllMethods,
  configDecorator: ConfigDecorator)
    extends SessionCache with AppName with ServicesConfig {
  val mode: Mode = environment.mode
  val runModeConfiguration: Configuration = configuration
  override lazy val defaultSource = appName
  override lazy val baseUri = baseUrl("cachable.session-cache")
  override lazy val domain = getConfString("cachable.session-cache.domain", "keystore")
}

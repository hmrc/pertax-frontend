/*
 * Copyright 2020 HM Revenue & Customs
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

import com.google.inject.{Inject, Singleton}
import config.ConfigDecorator
import javax.inject.Named
import play.api.{Configuration, Environment}
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

@Singleton
class LocalSessionCache @Inject()(
  environment: Environment,
  configuration: Configuration,
  val appNameConfiguration: Configuration,
  override val http: HttpClient,
  configDecorator: ConfigDecorator,
  servicesConfig: ServicesConfig,
  @Named("appName") appName: String)
    extends SessionCache {
  override lazy val defaultSource = appName
  override lazy val baseUri = servicesConfig.baseUrl("cachable.session-cache")
  override lazy val domain = servicesConfig.getConfString("cachable.session-cache.domain", "keystore")
}

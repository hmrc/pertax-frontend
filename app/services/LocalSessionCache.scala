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

package services

import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class LocalSessionCache @Inject() (
  val appNameConfiguration: Configuration,
  override val http: HttpClient,
  servicesConfig: ServicesConfig,
  @Named("appName") appName: String
) extends SessionCache {
  override lazy val defaultSource = appName
  override lazy val baseUri       = servicesConfig.baseUrl("cachable.session-cache")
  override lazy val domain        = servicesConfig.getConfString("cachable.session-cache.domain", "keystore")
}

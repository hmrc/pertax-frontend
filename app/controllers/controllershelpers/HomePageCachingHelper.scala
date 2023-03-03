/*
 * Copyright 2023 HM Revenue & Customs
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

package controllers.controllershelpers

import com.google.inject.Inject
import services.LocalSessionCache
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap

import scala.concurrent.{ExecutionContext, Future}

class HomePageCachingHelper @Inject() (
  val sessionCache: LocalSessionCache
)(implicit executionContext: ExecutionContext) {

  def hasUserDismissedBanner(implicit hc: HeaderCarrier): Future[Boolean] =
    sessionCache.fetch() map {
      case Some(cacheMap) => cacheMap.getEntry[Boolean]("urBannerDismissed").getOrElse(false)
      case None           => false
    }

  def storeUserUrDismissal()(implicit hc: HeaderCarrier): Future[CacheMap] =
    sessionCache.cache("urBannerDismissed", true)
}

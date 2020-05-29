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

package controllers.controllershelpers

import com.google.inject.Inject
import play.api.Logger
import services.LocalSessionCache
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HomePageCachingHelper @Inject()(
  val sessionCache: LocalSessionCache
) {

  val logger = Logger(this.getClass)

  private def checkSessionId(functionName: String)(implicit hc: HeaderCarrier) =
    if (hc.sessionId.isEmpty) {
      logger.warn(s"HomePageCachingHelper.$functionName has no session id")
      throw new RuntimeException("Cannot write to session cache without session id in header carrier.")
    }

  def hasUserDismissedUrInvitation[T](implicit hc: HeaderCarrier): Future[Boolean] = {
    checkSessionId("hasUserDismissedUrInvitation")

    sessionCache.fetch() map {
      case Some(cacheMap) => cacheMap.getEntry[Boolean]("urBannerDismissed").getOrElse(false)
      case None           => false
    }
  }

  def storeUserUrDismissal()(implicit hc: HeaderCarrier): Future[CacheMap] = {
    checkSessionId("storeUserUrDismissal")
    sessionCache.cache("urBannerDismissed", true)
  }
}

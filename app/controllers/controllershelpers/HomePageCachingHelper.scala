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
import controllers.auth.requests.UserRequest
import models.UserAnswers
import repositories.JourneyCacheRepository
import routePages.HasUrBannerDismissedPage
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class HomePageCachingHelper @Inject() (
  val journeyCacheRepository: JourneyCacheRepository
)(implicit executionContext: ExecutionContext) {

  def hasUserDismissedBanner(implicit hc: HeaderCarrier): Future[Boolean] =
    journeyCacheRepository.get(hc).flatMap { userAnswers =>
      userAnswers.get[Boolean](HasUrBannerDismissedPage) match {
        case Some(hasDismissed) => Future.successful(hasDismissed)
        case None               => Future.successful(false)
      }
    }

  def storeUserUrDismissal()(implicit request: UserRequest[_]): Future[UserAnswers] = {
    val updatedUserAnswers = request.userAnswers.setOrException(HasUrBannerDismissedPage, true)
    journeyCacheRepository.set(updatedUserAnswers).map(_ => updatedUserAnswers)
  }
}

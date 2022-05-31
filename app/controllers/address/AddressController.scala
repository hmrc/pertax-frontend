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

package controllers.address

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.PertaxBaseController
import controllers.auth.requests.UserRequest
import controllers.auth.AuthJourney
import models.PersonDetails
import play.api.mvc.{ActionBuilder, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.domain.Nino
import views.html.interstitial.DisplayAddressInterstitialView

import scala.concurrent.{ExecutionContext, Future}

abstract class AddressController @Inject() (
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  displayAddressInterstitialView: DisplayAddressInterstitialView
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends PertaxBaseController(cc) {

  def authenticate: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails

  def addressJourneyEnforcer(
    block: Nino => PersonDetails => Future[Result]
  )(implicit request: UserRequest[_]): Future[Result] =
    (for {
      payeAccount   <- request.nino
      personDetails <- request.personDetails
    } yield block(payeAccount)(personDetails)).getOrElse {
      Future.successful {
        val continueUrl = configDecorator.pertaxFrontendHost + routes.PersonalDetailsController.onPageLoad.url
        Ok(displayAddressInterstitialView(continueUrl))
      }
    }
}

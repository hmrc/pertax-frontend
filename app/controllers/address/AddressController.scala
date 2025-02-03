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

package controllers.address

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.PertaxBaseController
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import error.ErrorRenderer
import models.PersonDetails
import models.admin.AddressChangeAllowedToggle
import play.api.mvc.{ActionBuilder, AnyContent, MessagesControllerComponents, Result}
import services.CitizenDetailsService
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import views.html.InternalServerErrorView

import scala.concurrent.{ExecutionContext, Future}

abstract class AddressController @Inject() (
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  featureFlagService: FeatureFlagService,
  errorRenderer: ErrorRenderer,
  citizenDetailsService: CitizenDetailsService,
  internalServerErrorView: InternalServerErrorView
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends PertaxBaseController(cc) {

  def authenticate: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails

  def addressJourneyEnforcer(
    block: Nino => PersonDetails => Future[Result]
  )(implicit request: UserRequest[_]): Future[Result] =
    featureFlagService.get(AddressChangeAllowedToggle).flatMap { toggle =>
      if (!toggle.isEnabled) {
        Future.successful(InternalServerError(internalServerErrorView()))
      } else {
        citizenDetailsService.personDetails(request.authNino).value.flatMap {
          case Right(personDetails) =>
            block(request.authNino)(personDetails)
          case Left(_)              => Future.successful(errorRenderer.error(INTERNAL_SERVER_ERROR))
        }
      }
    }
}

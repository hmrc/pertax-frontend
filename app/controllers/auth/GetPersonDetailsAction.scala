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

package controllers.auth

import cats.data.EitherT
import com.google.inject.Inject
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.PersonDetails
import models.admin.GetPersonFromCitizenDetailsToggle
import play.api.Logging
import play.api.http.Status.LOCKED
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Results.Locked
import play.api.mvc.{ActionFunction, ActionRefiner, ControllerComponents, Result}
import services.CitizenDetailsService
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import views.html.ManualCorrespondenceView

import scala.concurrent.{ExecutionContext, Future}

class GetPersonDetailsAction @Inject() (
  citizenDetailsService: CitizenDetailsService,
  cc: ControllerComponents,
  val messagesApi: MessagesApi,
  manualCorrespondenceView: ManualCorrespondenceView,
  featureFlagService: FeatureFlagService
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends ActionRefiner[UserRequest, UserRequest]
    with ActionFunction[UserRequest, UserRequest]
    with I18nSupport
    with Logging {

  override protected def refine[A](request: UserRequest[A]): Future[Either[Result, UserRequest[A]]] =
    getPersonDetails()(request).map { personalDetails =>
      val ninoFromCitizenDetailsIfPresent: Option[Nino] = {
        val citizenDetailsNino = personalDetails.flatMap(_.person.nino)
        citizenDetailsNino.fold(request.nino)(_ => citizenDetailsNino)
      }

      UserRequest(
        authNino = request.nino.getOrElse(request.authNino),
        nino = ninoFromCitizenDetailsIfPresent,
        retrievedName = request.retrievedName,
        saUserType = request.saUserType,
        credentials = request.credentials,
        confidenceLevel = request.confidenceLevel,
        personDetails = personalDetails,
        trustedHelper = request.trustedHelper,
        enrolments = request.enrolments,
        profile = request.profile,
        breadcrumb = request.breadcrumb,
        request = request.request,
        userAnswers = request.userAnswers
      )
    }.value

  private def getPersonDetails()(implicit request: UserRequest[_]): EitherT[Future, Result, Option[PersonDetails]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    EitherT[Future, Result, FeatureFlag](featureFlagService.get(GetPersonFromCitizenDetailsToggle).map(Right(_)))
      .flatMap { toggle =>
        request.nino match {
          case Some(nino) =>
            if (toggle.isEnabled) {
              citizenDetailsService
                .personDetails(nino)(hc, ec)
                .transform {
                  case Right(response)                           => Right(Some(response))
                  case Left(error) if error.statusCode == LOCKED => Left(Locked(manualCorrespondenceView()))
                  case _                                         => Right(None)
                }
            } else {
              EitherT.rightT[Future, Result](None)
            }
          case _          => throw new RuntimeException("There is some problem with NINO. It is either missing or incorrect")
        }
      }
  }

  override protected def executionContext: ExecutionContext = cc.executionContext
}

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

package controllers.auth

import com.google.inject.Inject
import connectors.EnrolmentsConnector
import controllers.auth.requests.{Activated, AuthenticatedRequest, NotYetActivated, SelfAssessmentEnrolment, UserRequest}
import models._
import play.api.mvc.{ActionFunction, ActionRefiner, Result}
import services.{CitizenDetailsService, MatchingDetailsSuccessResponse}
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

class SelfAssessmentStatusAction @Inject()(
  citizenDetailsService: CitizenDetailsService,
  enrolmentsConnector: EnrolmentsConnector)(implicit ec: ExecutionContext)
    extends ActionRefiner[AuthenticatedRequest, UserRequest] with ActionFunction[AuthenticatedRequest, UserRequest] {

  private def getSaUtrFromCitizenDetailsService(nino: Nino)(implicit hc: HeaderCarrier): Future[Option[SaUtr]] =
    citizenDetailsService.getMatchingDetails(nino) map {
      case MatchingDetailsSuccessResponse(matchingDetails) => matchingDetails.saUtr
      case _                                               => None
    }

  private def enrolledOnDifferentCredentials(saUtr: SaUtr)(implicit hc: HeaderCarrier): Future[SelfAssessmentUserType] =
    enrolmentsConnector
      .getUserIdsWithEnrolments(saUtr.utr)
      .map(x =>
        if (x.nonEmpty) {
          WrongCredentialsSelfAssessmentUser(saUtr)
        } else {
          NonFilerSelfAssessmentUser
      })

  private def getSelfAssessmentUserType[A](
    implicit hc: HeaderCarrier,
    request: AuthenticatedRequest[A]): Future[SelfAssessmentUserType] =
    request.nino.fold[Future[SelfAssessmentUserType]](Future.successful(NonFilerSelfAssessmentUser)) { nino =>
      request.saEnrolment match {
        case Some(SelfAssessmentEnrolment(saUtr, Activated)) =>
          Future.successful(ActivatedOnlineFilerSelfAssessmentUser(saUtr))
        case Some(SelfAssessmentEnrolment(saUtr, NotYetActivated)) =>
          Future.successful(NotYetActivatedOnlineFilerSelfAssessmentUser(saUtr))
        case None => {
          getSaUtrFromCitizenDetailsService(nino).flatMap {
            case Some(saUtr) => enrolledOnDifferentCredentials(saUtr)
            case None        => Future.successful(NonFilerSelfAssessmentUser)
          }
        }
      }
    }

  override protected def refine[A](request: AuthenticatedRequest[A]): Future[Either[Result, UserRequest[A]]] = {
    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
    getSelfAssessmentUserType(hc, request).map { saType =>
      Right(
        UserRequest(
          request.nino,
          request.name,
          request.previousLoginTime,
          saType,
          request.credentials,
          request.confidenceLevel,
          None,
          request.trustedHelper,
          None,
          None,
          None,
          request.request
        )
      )
    }
  }

}

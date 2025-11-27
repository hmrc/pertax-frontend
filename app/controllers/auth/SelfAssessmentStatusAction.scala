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

import com.google.inject.Inject
import controllers.auth.requests._
import models._
import play.api.mvc.{ActionFunction, ActionRefiner, ControllerComponents, Result}
import services.{CitizenDetailsService, EnrolmentStoreProxyService}
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import util.EnrolmentsHelper

import scala.concurrent.{ExecutionContext, Future}

class SelfAssessmentStatusAction @Inject() (
  CitizenDetailsService: CitizenDetailsService,
  enrolmentStoreProxyService: EnrolmentStoreProxyService,
  enrolmentsHelper: EnrolmentsHelper,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends ActionRefiner[AuthenticatedRequest, UserRequest]
    with ActionFunction[AuthenticatedRequest, UserRequest] {

  private def getSaUtrFromCitizenDetailsService(nino: Nino)(implicit hc: HeaderCarrier): Future[Option[SaUtr]] =
    CitizenDetailsService
      .getSaUtrFromMatchingDetails(nino)
      .leftMap(_ => Option.empty[SaUtr])
      .merge

  private def getSelfAssessmentUserType[A](implicit
    hc: HeaderCarrier,
    request: AuthenticatedRequest[A]
  ): Future[SelfAssessmentUserType] =
    if (request.trustedHelper.isEmpty) {
      enrolmentsHelper.selfAssessmentStatus(request.enrolments) match {
        case Some(SelfAssessmentEnrolment(saUtr, Activated))       =>
          Future.successful(ActivatedOnlineFilerSelfAssessmentUser(saUtr))
        case Some(SelfAssessmentEnrolment(saUtr, NotYetActivated)) =>
          Future.successful(NotYetActivatedOnlineFilerSelfAssessmentUser(saUtr))
        case None                                                  =>
          getSaUtrFromCitizenDetailsService(request.authNino).flatMap {
            case Some(saUtr) =>
              enrolmentStoreProxyService
                .findCredentialsWithIrSaForUtr(saUtr)
                .fold[SelfAssessmentUserType](
                  _ => NonFilerSelfAssessmentUser,
                  creds =>
                    if (creds.isEmpty) { NotEnrolledSelfAssessmentUser(saUtr) }
                    else { WrongCredentialsSelfAssessmentUser(saUtr) }
                )
            case None        => Future.successful(NonFilerSelfAssessmentUser)
          }
      }
    } else { Future.successful(NonFilerSelfAssessmentUser) }

  override protected def refine[A](request: AuthenticatedRequest[A]): Future[Either[Result, UserRequest[A]]] = {
    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    getSelfAssessmentUserType(hc, request).map { saType =>
      Right(
        UserRequest(
          request.authNino,
          saType,
          request.credentials,
          request.confidenceLevel,
          request.trustedHelper,
          request.enrolments,
          request.profile,
          None,
          request.request,
          request.userAnswers
        )
      )
    }
  }

  override protected def executionContext: ExecutionContext = cc.executionContext
}

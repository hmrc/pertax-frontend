/*
 * Copyright 2024 HM Revenue & Customs
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

import cats.data.EitherT
import com.google.inject.Inject
import connectors.EnrolmentsConnector
import controllers.auth.requests.UserRequest
import models.MtdUserType._
import models.MtdUser
import models.enrolments.{IdentifiersOrVerifiers, KnownFactsRequest}
import play.api.Logging
import play.api.mvc.Request
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class EnrolmentStoreProxyService @Inject() (
  enrolmentsConnector: EnrolmentsConnector
)(implicit ec: ExecutionContext)
    extends Logging {

  def getMtdUserType(
    nino: Nino
  )(implicit hc: HeaderCarrier, request: UserRequest[_]): EitherT[Future, UpstreamErrorResponse, MtdUser] = {
    val knownFactsRequest = KnownFactsRequest("HMRC-MTD-IT", List(IdentifiersOrVerifiers("NINO", nino.nino)))
    enrolmentsConnector.getKnownFacts(knownFactsRequest).flatMap { knownFactsResponse =>
      // Getting all the known facts from nino for MTDITID.
      // Once a unique id is founds, it used to find all the enrolments
      knownFactsResponse.enrolments
        .flatMap { enrolment =>
          enrolment.identifiers.find(_.key == "MTDITID").map(_.value)
        } match {
        case Nil            => EitherT.rightT[Future, UpstreamErrorResponse](NonFilerMtdUser)
        case mtdItId :: Nil =>
          val enrolmentKey = s"HMRC-MTD-IT~MTDITID~$mtdItId"
          enrolmentsConnector.getUserIdsWithEnrolments(enrolmentKey)(implicitly, request.request).map {
            case Nil         => NotEnrolledMtdUser
            case cred :: Nil =>
              if (request.credentials.providerId == cred) {
                EnrolledMtdUser(mtdItId)
              } else {
                WrongCredentialsMtdUser(mtdItId, cred)
              }
            case _           =>
              logger.error(s"More than one credential for enrolment HMRC-MTD-IT for nino $nino")
              UnknownMtdUser
          }
        case _              =>
          logger.error(s"More than one MTDITID know fact for nino $nino")
          EitherT.rightT[Future, UpstreamErrorResponse](UnknownMtdUser)
      }
    }
  }

  def findCredentialsWithIrSaForUtr(utr: SaUtr)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Seq[String]] = {
    val enrolmentKey = s"IR-SA~UTR~${utr.utr}"
    enrolmentsConnector.getUserIdsWithEnrolments(enrolmentKey)
  }

}

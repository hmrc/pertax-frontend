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

package services

import cats.data.EitherT
import com.google.inject.Inject
import connectors.IdentityVerificationFrontendConnector
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

sealed trait IdentityVerificationResponse

case object Success extends IdentityVerificationResponse {
  override def toString: String = "Success"
}
case object Incomplete extends IdentityVerificationResponse {
  override def toString: String = "Incomplete"
}
case object FailedMatching extends IdentityVerificationResponse {
  override def toString: String = "FailedMatching"
}
case object InsufficientEvidence extends IdentityVerificationResponse {
  override def toString: String = "InsufficientEvidence"
}
case object LockedOut extends IdentityVerificationResponse {
  override def toString: String = "LockedOut"
}
case object UserAborted extends IdentityVerificationResponse {
  override def toString: String = "UserAborted"
}
case object Timeout extends IdentityVerificationResponse {
  override def toString: String = "Timeout"
}
case object TechnicalIssue extends IdentityVerificationResponse {
  override def toString: String = "TechnicalIssue"
}
case object PrecondFailed extends IdentityVerificationResponse {
  override def toString: String = "PreconditionFailed"
}
case object InvalidResponse extends IdentityVerificationResponse

class IdentityVerificationFrontendService @Inject() (
  identityVerificationFrontendConnector: IdentityVerificationFrontendConnector
) {

  def getIVJourneyStatus(journeyId: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse] =
    identityVerificationFrontendConnector.getIVJourneyStatus(journeyId).map { response =>
      val status =
        List(
          (response.json \ "journeyResult").asOpt[String],
          (response.json \ "result").asOpt[String]
        ).flatten.head

      status match {
        case "Success"              => Success
        case "Incomplete"           => Incomplete
        case "FailedMatching"       => FailedMatching
        case "InsufficientEvidence" => InsufficientEvidence
        case "LockedOut"            => LockedOut
        case "UserAborted"          => UserAborted
        case "Timeout"              => Timeout
        case "TechnicalIssue"       => TechnicalIssue
        case "PreconditionFailed"   => PrecondFailed
        case _                      => InvalidResponse
      }
    }
}

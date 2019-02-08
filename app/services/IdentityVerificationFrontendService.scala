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

package services

import javax.inject.{Inject, Singleton}
import com.kenshoo.play.metrics.Metrics
import metrics.HasMetrics
import play.api.{Configuration, Logger}
import play.api.Mode.Mode
import play.api.http.Status._
import services.http.SimpleHttp
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.Future
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}


trait IdentityVerificationResponse
case class IdentityVerificationSuccessResponse(result: String) extends IdentityVerificationResponse
case object IdentityVerificationNotFoundResponse extends IdentityVerificationResponse
case class IdentityVerificationUnexpectedResponse(r: HttpResponse) extends IdentityVerificationResponse
case class IdentityVerificationErrorResponse(cause: Exception) extends IdentityVerificationResponse

object IdentityVerificationSuccessResponse {
  val Success = "Success"
  val Incomplete = "Incomplete"
  val FailedMatching = "FailedMatching"
  val InsufficientEvidence = "InsufficientEvidence"
  val LockedOut = "LockedOut"
  val UserAborted = "UserAborted"
  val Timeout = "Timeout"
  val TechnicalIssue = "TechnicalIssue"
  val PrecondFailed = "PreconditionFailed"
}


@Singleton
class IdentityVerificationFrontendService @Inject() (val mode:Mode, val runModeConfiguration: Configuration, val simpleHttp: SimpleHttp, val metrics: Metrics) extends ServicesConfig with HasMetrics {

  lazy val identityVerificationFrontendUrl = baseUrl("identity-verification-frontend")

  def getIVJourneyStatus(journeyId: String)(implicit hc: HeaderCarrier): Future[IdentityVerificationResponse] = {
    withMetricsTimer("get-iv-journey-status") { t =>

      simpleHttp.get[IdentityVerificationResponse](s"$identityVerificationFrontendUrl/mdtp/journey/journeyId/$journeyId") (
        onComplete = {
          case r if r.status >= 200 && r.status < 300 =>
            t.completeTimerAndIncrementSuccessCounter()
            val result = List((r.json \ "journeyResult").asOpt[String], (r.json \ "result").asOpt[String]).flatten.head  //FIXME - dont use head
            IdentityVerificationSuccessResponse(result)

          case r if r.status == NOT_FOUND =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn("Unable to get IV journey status from identity-verification-frontend-service")
            IdentityVerificationNotFoundResponse

          case r =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn(s"Unexpected ${r.status} response getting IV journey status from identity-verification-frontend-service")
            IdentityVerificationUnexpectedResponse(r)
        },
        onError = {
          case e =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn("Error getting IV journey status from identity-verification-frontend-service", e)
            IdentityVerificationErrorResponse(e)
        }
      )

    }
  }
}

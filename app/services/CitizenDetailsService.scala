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
import metrics._
import models._
import play.api.{Configuration, Logger}
import play.api.Mode.Mode
import play.api.libs.json.{JsObject, Json}
import services.http.SimpleHttp
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._

import scala.concurrent.Future
import play.api.http.Status._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

sealed trait PersonDetailsResponse
case class PersonDetailsSuccessResponse(personDetails: PersonDetails) extends PersonDetailsResponse
case object PersonDetailsNotFoundResponse extends PersonDetailsResponse
case object PersonDetailsHiddenResponse extends PersonDetailsResponse
case class PersonDetailsUnexpectedResponse(r: HttpResponse) extends PersonDetailsResponse
case class PersonDetailsErrorResponse(cause: Exception) extends PersonDetailsResponse

sealed trait UpdateAddressResponse
case object UpdateAddressSuccessResponse extends UpdateAddressResponse
case object UpdateAddressBadRequestResponse extends UpdateAddressResponse
case class UpdateAddressUnexpectedResponse(r: HttpResponse) extends UpdateAddressResponse
case class UpdateAddressErrorResponse(cause: Exception) extends UpdateAddressResponse

sealed trait MatchingDetailsResponse
case class MatchingDetailsSuccessResponse(matchingDetails: MatchingDetails) extends MatchingDetailsResponse
case object MatchingDetailsNotFoundResponse extends MatchingDetailsResponse
case class MatchingDetailsUnexpectedResponse(r: HttpResponse) extends MatchingDetailsResponse
case class MatchingDetailsErrorResponse(cause: Exception) extends MatchingDetailsResponse


@Singleton
class CitizenDetailsService @Inject() (val mode:Mode, val runModeConfiguration: Configuration, val simpleHttp: SimpleHttp, val metrics: Metrics) extends ServicesConfig with HasMetrics {

  lazy val citizenDetailsUrl = baseUrl("citizen-details")

  /**
    * Gets the person details
    */
  def personDetails(nino: Nino)(implicit hc: HeaderCarrier): Future[PersonDetailsResponse] = {
    withMetricsTimer("get-person-details") { t =>

      simpleHttp.get[PersonDetailsResponse](s"$citizenDetailsUrl/citizen-details/$nino/designatory-details")(
        onComplete = {
          case r if r.status >= 200 && r.status < 300 =>
            t.completeTimerAndIncrementSuccessCounter()
            PersonDetailsSuccessResponse(r.json.as[PersonDetails])

          case r if r.status == LOCKED =>
            t.completeTimerAndIncrementFailedCounter()  //TODO - check this
            Logger.warn("Personal details record in citizen-details was hidden")
            PersonDetailsHiddenResponse

          case r if r.status == NOT_FOUND =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn("Unable to find personal details record in citizen-details")
            PersonDetailsNotFoundResponse

          case r =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn(s"Unexpected ${r.status} response getting personal details record from citizen-details")
            PersonDetailsUnexpectedResponse(r)
        },
        onError = {
          case e =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn("Error getting personal details record from citizen-details", e)
            PersonDetailsErrorResponse(e)
        }
      )
    }
  }

  def updateAddress(nino: Nino, etag: String, address: Address)(implicit headerCarrier: HeaderCarrier): Future[UpdateAddressResponse] = {
    val body = Json.obj("etag" -> etag, "address" -> Json.toJson(address))

    withMetricsTimer("update-address") { t =>
      simpleHttp.post[JsObject, UpdateAddressResponse](s"$citizenDetailsUrl/citizen-details/$nino/designatory-details/address", body)(
        onComplete = {
          case r if r.status >= 200 && r.status < 300 =>
            t.completeTimerAndIncrementSuccessCounter()
            UpdateAddressSuccessResponse

          case r if r.status == BAD_REQUEST =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn(s"Bad Request ${r.status} response updating address record in citizen-details")
            UpdateAddressBadRequestResponse

          case r =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn(s"Unexpected ${r.status} response updating address record in citizen-details")
            UpdateAddressUnexpectedResponse(r)
        },
        onError = {
          case e =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn("Error updating address record in citizen-details", e)
            UpdateAddressErrorResponse(e)
        }
      )
    }
  }

  def getMatchingDetails(nino: Nino)(implicit hc: HeaderCarrier): Future[MatchingDetailsResponse] = {

    withMetricsTimer("get-matching-details") { t =>
      simpleHttp.get[MatchingDetailsResponse](s"$citizenDetailsUrl/citizen-details/nino/$nino")(
        onComplete = {
          case r if r.status >= 200 && r.status < 300 =>
            t.completeTimerAndIncrementSuccessCounter()
            MatchingDetailsSuccessResponse(MatchingDetails.fromJsonMatchingDetails(r.json))
          case r if r.status == NOT_FOUND =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn("Unable to find matching details in citizen-details")
            MatchingDetailsNotFoundResponse
          case r =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn(s"Unexpected ${r.status} response getting matching details from citizen-details")
            MatchingDetailsUnexpectedResponse(r)
        },
        onError = {
          case e =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn("Error getting matching details from citizen-details", e)
            MatchingDetailsErrorResponse(e)
        }
      )
    }
  }
}

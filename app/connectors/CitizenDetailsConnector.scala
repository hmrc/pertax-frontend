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

package connectors

import com.google.inject.{Inject, Singleton}
import com.kenshoo.play.metrics.Metrics
import metrics._
import models._
import play.api.Logging
import play.api.http.Status._
import play.api.libs.json.{JsObject, Json}
import services._
import services.http.SimpleHttp
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.Future

sealed trait PersonDetailsResponse
case class PersonDetailsSuccessResponse(personDetails: PersonDetails) extends PersonDetailsResponse
case object PersonDetailsNotFoundResponse extends PersonDetailsResponse
case object PersonDetailsHiddenResponse extends PersonDetailsResponse
case class PersonDetailsUnexpectedResponse(r: HttpResponse) extends PersonDetailsResponse
case class PersonDetailsErrorResponse(cause: Exception) extends PersonDetailsResponse

sealed trait MatchingDetailsResponse
case class MatchingDetailsSuccessResponse(matchingDetails: MatchingDetails) extends MatchingDetailsResponse
case object MatchingDetailsNotFoundResponse extends MatchingDetailsResponse
case class MatchingDetailsUnexpectedResponse(r: HttpResponse) extends MatchingDetailsResponse
case class MatchingDetailsErrorResponse(cause: Exception) extends MatchingDetailsResponse

@Singleton
class CitizenDetailsConnector @Inject() (
  val simpleHttp: SimpleHttp,
  val metrics: Metrics,
  servicesConfig: ServicesConfig
) extends HasMetrics with Logging {

  lazy val citizenDetailsUrl = servicesConfig.baseUrl("citizen-details")

  def personDetails(nino: Nino)(implicit hc: HeaderCarrier): Future[PersonDetailsResponse] =
    withMetricsTimer("get-person-details") { timer =>
      simpleHttp.get[PersonDetailsResponse](s"$citizenDetailsUrl/citizen-details/$nino/designatory-details")(
        onComplete = {
          case response if response.status >= 200 && response.status < 300 =>
            println("PPPP23: " + response.toString)
            timer.completeTimerAndIncrementSuccessCounter()
            PersonDetailsSuccessResponse(response.json.as[PersonDetails])

          case response if response.status == LOCKED =>
            timer.completeTimerAndIncrementFailedCounter()
            logger.warn("Personal details record in citizen-details was hidden")
            PersonDetailsHiddenResponse

          case response if response.status == NOT_FOUND =>
            timer.completeTimerAndIncrementFailedCounter()
            logger.warn("Unable to find personal details record in citizen-details")
            PersonDetailsNotFoundResponse

          case response =>
            timer.completeTimerAndIncrementFailedCounter()
            logger.warn(s"Unexpected ${response.status} response getting personal details record from citizen-details")
            PersonDetailsUnexpectedResponse(response)
        },
        onError = { e =>
          timer.completeTimerAndIncrementFailedCounter()
          logger.warn("Error getting personal details record from citizen-details", e)
          PersonDetailsErrorResponse(e)
        }
      )
    }

  def updateAddress(nino: Nino, etag: String, address: Address)(implicit
    headerCarrier: HeaderCarrier
  ): Future[UpdateAddressResponse] = {
    val body = Json.obj("etag" -> etag, "address" -> Json.toJson(address))
    withMetricsTimer("update-address") { timer =>
      simpleHttp.post[JsObject, UpdateAddressResponse](
        s"$citizenDetailsUrl/citizen-details/$nino/designatory-details/address",
        body
      )(
        onComplete = {
          case response if response.status >= 200 && response.status < 300 =>
            timer.completeTimerAndIncrementSuccessCounter()
            UpdateAddressSuccessResponse

          case response if response.status == BAD_REQUEST =>
            timer.completeTimerAndIncrementFailedCounter()
            logger.warn(
              s"Bad Request ${response.status}-${response.body} response updating address record in citizen-details"
            )
            UpdateAddressBadRequestResponse

          case response =>
            timer.completeTimerAndIncrementFailedCounter()
            logger.warn(
              s"Unexpected ${response.status}-${response.body} response updating address record in citizen-details"
            )
            UpdateAddressUnexpectedResponse(response)
        },
        onError = { e =>
          timer.completeTimerAndIncrementFailedCounter()
          logger.warn("Error updating address record in citizen-details", e)
          UpdateAddressErrorResponse(e)
        }
      )
    }
  }

  def getMatchingDetails(nino: Nino)(implicit hc: HeaderCarrier): Future[MatchingDetailsResponse] =
    withMetricsTimer("get-matching-details") { timer =>
      simpleHttp.get[MatchingDetailsResponse](s"$citizenDetailsUrl/citizen-details/nino/$nino")(
        onComplete = {
          case response if response.status >= 200 && response.status < 300 =>
            timer.completeTimerAndIncrementSuccessCounter()
            MatchingDetailsSuccessResponse(MatchingDetails.fromJsonMatchingDetails(response.json))
          case response if response.status == NOT_FOUND =>
            timer.completeTimerAndIncrementFailedCounter()
            logger.warn("Unable to find matching details in citizen-details")
            MatchingDetailsNotFoundResponse
          case response =>
            timer.completeTimerAndIncrementFailedCounter()
            logger.warn(s"Unexpected ${response.status} response getting matching details from citizen-details")
            MatchingDetailsUnexpectedResponse(response)
        },
        onError = { e =>
          timer.completeTimerAndIncrementFailedCounter()
          logger.warn("Error getting matching details from citizen-details", e)
          MatchingDetailsErrorResponse(e)
        }
      )
    }

  def getEtag(nino: String)(implicit hc: HeaderCarrier): Future[Option[ETag]] =
    withMetricsTimer("get-etag") { timer =>
      simpleHttp.get[Option[ETag]](s"$citizenDetailsUrl/citizen-details/$nino/etag")(
        onComplete = {
          case response: HttpResponse if response.status == OK =>
            timer.completeTimerAndIncrementSuccessCounter()
            response.json.asOpt[ETag]
          case response =>
            auditEtagFailure(
              timer,
              s"[CitizenDetailsService.getEtag] failed to find etag in citizen-details: ${response.status}"
            )
        },
        onError = { e: Exception =>
          auditEtagFailure(
            timer,
            s"[CitizenDetailsService.getEtag] returned an Exception: ${e.getMessage}"
          )
        }
      )
    }

  private def auditEtagFailure(timer: MetricsTimer, message: String): None.type = {
    timer.completeTimerAndIncrementFailedCounter()
    logger.warn(message)
    None
  }

}

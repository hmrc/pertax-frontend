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

import play.api.http.Status.{BAD_GATEWAY, INTERNAL_SERVER_ERROR}
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.http.HttpReads.{is4xx, is5xx}
import uk.gov.hmrc.http.{HttpReads, HttpReadsEither, HttpResponse, UpstreamErrorResponse}

trait CustomError extends HttpReadsEither {

  case class ErrorMessage(error: String)
  implicit val errorMessageFormat: OFormat[ErrorMessage] = Json.format[ErrorMessage]

  private def handleResponseEither(response: HttpResponse): Either[UpstreamErrorResponse, HttpResponse] =
    response.status match {
      case status if is4xx(status) || is5xx(status) =>
        val error = response.json.asOpt[ErrorMessage]
        Left(
          UpstreamErrorResponse(
            message = error.fold(response.body)(_.error),
            statusCode = status,
            reportAs = if (is4xx(status)) INTERNAL_SERVER_ERROR else BAD_GATEWAY,
            headers = response.headers
          )
        )
      case _                                        => Right(response)
    }

  override implicit def readEitherOf[A: HttpReads]: HttpReads[Either[UpstreamErrorResponse, A]] =
    HttpReads.ask.flatMap { case (_, _, response) =>
      handleResponseEither(response) match {
        case Left(err) => HttpReads.pure(Left(err))
        case Right(_)  => HttpReads[A].map(Right.apply)
      }
    }
}

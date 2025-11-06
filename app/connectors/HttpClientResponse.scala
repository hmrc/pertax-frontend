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

package connectors

import cats.data.EitherT
import com.google.inject.Inject
import play.api.Logging
import play.api.http.Status.*
import uk.gov.hmrc.http.{HttpException, HttpResponse, UpstreamErrorResponse}
import util.EtagError

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class HttpClientResponse @Inject() (implicit ec: ExecutionContext) extends Logging {
  private val logErrorResponsesMain: PartialFunction[Try[Either[UpstreamErrorResponse, HttpResponse]], Unit] = {
    case Success(Left(error)) if error.statusCode == NOT_FOUND                                    =>
      logger.info(error.message)
    case Success(Left(error)) if error.statusCode == LOCKED                                       =>
      logger.warn(error.message)
    case Success(Left(error)) if error.statusCode >= 499 || error.statusCode == TOO_MANY_REQUESTS =>
      logger.error(error.message)
    case Failure(exception: HttpException)                                                        =>
      logger.error(exception.message)
  }

  private val logUpstreamErrorResponseAsError
    : PartialFunction[Try[Either[UpstreamErrorResponse, HttpResponse]], Unit] = { case Success(Left(error)) =>
    logger.error(error.message, error)
  }

  private val recoverHttpException: PartialFunction[Throwable, Either[UpstreamErrorResponse, HttpResponse]] = {
    case exception: HttpException =>
      Left(UpstreamErrorResponse(exception.message, BAD_GATEWAY, BAD_GATEWAY))
  }

  private val logUpdateAddressErrors: PartialFunction[Try[Either[UpstreamErrorResponse, HttpResponse]], Unit] = {
    case Success(Left(error))
        if error.statusCode == BAD_REQUEST &&
          error.message.toLowerCase().contains("start date") =>
      logger.info(s"Specific 400 Error - Address Update: ${error.message}")
    case Success(Left(error)) if EtagError.isConflict(error) =>
      logger.info(error.message)
    case Success(Left(error))                                =>
      logger.error(error.message, error)
  }

  def read(
    response: Future[Either[UpstreamErrorResponse, HttpResponse]]
  ): EitherT[Future, UpstreamErrorResponse, HttpResponse] =
    EitherT(
      response
        andThen
          (logErrorResponsesMain orElse logUpstreamErrorResponseAsError)
          recover recoverHttpException
    )

  def readUpdateAddress(
    response: Future[Either[UpstreamErrorResponse, HttpResponse]]
  ): EitherT[Future, UpstreamErrorResponse, HttpResponse] =
    EitherT(
      response
        andThen
          (logErrorResponsesMain orElse logUpdateAddressErrors)
          recover recoverHttpException
    )

  def readLogForbiddenAsWarning(
    response: Future[Either[UpstreamErrorResponse, HttpResponse]]
  ): EitherT[Future, UpstreamErrorResponse, HttpResponse] = {
    val logForbiddenAsWarning: PartialFunction[Try[Either[UpstreamErrorResponse, HttpResponse]], Unit] = {
      case Success(Left(error)) if error.statusCode == FORBIDDEN => logger.warn(error.message)
    }
    EitherT(
      response
        andThen
          (logErrorResponsesMain orElse logForbiddenAsWarning orElse logUpstreamErrorResponseAsError)
          recover recoverHttpException
    )
  }

  def readLogUnauthorisedAsInfo(
    response: Future[Either[UpstreamErrorResponse, HttpResponse]]
  ): EitherT[Future, UpstreamErrorResponse, HttpResponse] = {
    val logUnauthorisedAsInfo: PartialFunction[Try[Either[UpstreamErrorResponse, HttpResponse]], Unit] = {
      case Success(Left(error)) if error.statusCode == UNAUTHORIZED => logger.info(error.message)
    }
    EitherT(
      response
        andThen
          (logErrorResponsesMain orElse logUnauthorisedAsInfo orElse logUpstreamErrorResponseAsError)
          recover recoverHttpException
    )
  }
}

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

package controllers.auth

import com.google.inject.Inject
import config.ConfigDecorator
import connectors.PertaxConnector
import controllers.auth.requests.AuthenticatedRequest
import models.PertaxResponse
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Results.{InternalServerError, Redirect, Status}
import play.api.mvc.{ActionFunction, ActionRefiner, ControllerComponents, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.binders.SafeRedirectUrl
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.play.partials.HtmlPartial
import views.html.{InternalServerErrorView, UnauthenticatedMainView}

import scala.concurrent.{ExecutionContext, Future}

class PertaxAuthAction @Inject() (
  pertaxConnector: PertaxConnector,
  internalServerErrorView: InternalServerErrorView,
  mainTemplate: UnauthenticatedMainView,
  cc: ControllerComponents
)(implicit messagesApi: MessagesApi, configDecorator: ConfigDecorator)
    extends ActionRefiner[AuthenticatedRequest, AuthenticatedRequest]
    with ActionFunction[AuthenticatedRequest, AuthenticatedRequest]
    with I18nSupport
    with Logging {

  override def messagesApi: MessagesApi = cc.messagesApi

  override def refine[A](request: AuthenticatedRequest[A]): Future[Either[Result, AuthenticatedRequest[A]]] = {
    implicit val hc: HeaderCarrier                             =
      HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    implicit val authenticatedRequest: AuthenticatedRequest[_] = request

    pertaxConnector
      .pertaxAuthorise(request.nino.get.nino)
      .value
      .flatMap {
        case Right(PertaxResponse("ACCESS_GRANTED", _, _, _))                    =>
          Future.successful(Right(request))
        case Right(PertaxResponse("NO_HMRC_PT_ENROLMENT", _, _, Some(redirect))) =>
          Future.successful(Left(Redirect(s"$redirect/?redirectUrl=${SafeRedirectUrl(request.uri).encodedUrl}")))
        case Right(PertaxResponse(_, _, Some(errorView), _))                     =>
          pertaxConnector.loadPartial(errorView.url).map {
            case partial: HtmlPartial.Success =>
              Left(Status(errorView.statusCode)(mainTemplate(partial.title.getOrElse(""))(partial.content)))
            case _: HtmlPartial.Failure       =>
              logger.error(s"The partial ${errorView.url} failed to be retrieved")
              Left(InternalServerError(internalServerErrorView()))
          }
        case Right(response)                                                     =>
          val ex =
            new RuntimeException(s"Pertax response `${response.code}` with message ${response.message} is not handled")
          logger.error(ex.getMessage, ex)
          Future.successful(Left(InternalServerError(internalServerErrorView())))

        case _ => Future.successful(Left(InternalServerError(internalServerErrorView())))
      }
  }

  override protected implicit val executionContext: ExecutionContext = cc.executionContext
}

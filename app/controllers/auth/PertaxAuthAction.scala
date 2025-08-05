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

package controllers.auth

import com.google.inject.{Inject, Singleton}
import config.ConfigDecorator
import connectors.PertaxConnector
import views.MainView
import models.PertaxResponse
import views.html.InternalServerErrorView
import play.api.Logging
import play.api.http.Status.UNAUTHORIZED
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.*
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.binders.{AbsoluteWithHostnameFromAllowlist, OnlyRelative, RedirectUrl, SafeRedirectUrl}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl.idFunctor
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.play.partials.HtmlPartial

import java.net.URLEncoder
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PertaxAuthAction @Inject() (
  pertaxConnector: PertaxConnector,
  internalServerErrorView: InternalServerErrorView,
  mainView: MainView,
  cc: ControllerComponents
)(implicit appConfig: ConfigDecorator)
    extends ActionFilter[Request]
    with Results
    with I18nSupport
    with Logging {

  override def messagesApi: MessagesApi = cc.messagesApi

  // scalastyle:off cyclomatic.complexity
  override def filter[A](request: Request[A]): Future[Option[Result]] = {
    implicit val implicitRequest: Request[A] = request
    implicit val hc: HeaderCarrier           = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    pertaxConnector.pertaxPostAuthorise.value.flatMap {
      case Left(UpstreamErrorResponse(_, status, _, _)) if status == UNAUTHORIZED                =>
        Future.successful(Some(signInJourney))
      case Left(_)                                                                               =>
        Future.successful(Some(InternalServerError(internalServerErrorView())))
      case Right(PertaxResponse("ACCESS_GRANTED", _, _, _))                                      =>
        Future.successful(None)
      case Right(PertaxResponse("NO_HMRC_PT_ENROLMENT", _, _, Some(redirect)))                   =>
        val redirectUrl = RedirectUrl(request.uri).getEither(
          OnlyRelative | AbsoluteWithHostnameFromAllowlist("localhost")
        ) match {
          case Right(safeRedirectUrl) => URLEncoder.encode(safeRedirectUrl.url, "UTF-8")
          case Left(error)            => throw new IllegalArgumentException(error)
        }
        Future.successful(Some(Redirect(s"$redirect?redirectUrl=$redirectUrl")))
      case Right(PertaxResponse("CONFIDENCE_LEVEL_UPLIFT_REQUIRED", _, _, Some(upliftRedirect))) =>
        Future.successful(Some(upliftJourney(request, upliftRedirect)))
      case Right(PertaxResponse("CREDENTIAL_STRENGTH_UPLIFT_REQUIRED", _, _, Some(_)))           =>
        val ex =
          new RuntimeException(
            s"Weak credentials should be dealt before the service"
          )
        logger.error(ex.getMessage, ex)
        Future.successful(Some(InternalServerError(internalServerErrorView())))

      case Right(PertaxResponse(_, _, Some(errorView), _)) =>
        pertaxConnector.loadPartial(errorView.url).map {
          case partial: HtmlPartial.Success =>
            Some(Status(errorView.statusCode)(mainView(partial.title.getOrElse(""))(partial.content)))
          case _: HtmlPartial.Failure       =>
            logger.error(s"The partial ${errorView.url} failed to be retrieved")
            Some(InternalServerError(internalServerErrorView()))
        }
      case Right(response)                                 =>
        val ex =
          new RuntimeException(
            s"Pertax response `${response.code}` with message ${response.message} is not handled"
          )
        logger.error(ex.getMessage, ex)
        Future.successful(Some(InternalServerError(internalServerErrorView())))
    }
  }

  override protected implicit val executionContext: ExecutionContext = cc.executionContext

  private def signInJourney[A]: Result =
    Redirect(
      appConfig.ggSignInUrl,
      Map(
        "continue_url" -> Seq(s"${appConfig.pertaxFrontendHost}${appConfig.personalAccount}"),
        "origin"       -> Seq("pertax-frontend"),
        "accountType"  -> Seq("individual")
      )
    )

  private def upliftJourney(request: Request[_], upliftRedirect: String): Result =
    Redirect(
      upliftRedirect,
      Map(
        "origin"          -> Seq(appConfig.defaultOrigin.origin),
        "confidenceLevel" -> Seq(ConfidenceLevel.L200.toString),
        "completionURL"   -> Seq(s"${appConfig.pertaxFrontendForAuthHost}${request.uri}"),
        "failureURL"      -> Seq(
          s"${appConfig.pertaxFrontendForAuthHost}${appConfig.serviceIdentityCheckFailedUrl}?continueUrl=${appConfig.pertaxFrontendForAuthHost}${request.uri}"
        )
      )
    )

}

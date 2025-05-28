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

import com.google.inject.{ImplementedBy, Inject}
import config.ConfigDecorator
import controllers.auth.TrustedHelperResult.{Error, Found, NotFound}
import controllers.auth.requests.AuthenticatedRequest
import io.lemonlabs.uri.Url
import play.api.Logging
import play.api.mvc._
import repositories.JourneyCacheRepository
import services.FandfService
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.{Retrievals, TrustedHelper}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

class AuthRetrievalsImpl @Inject() (
  val authConnector: AuthConnector,
  mcc: MessagesControllerComponents,
  journeyCacheRepository: JourneyCacheRepository,
  fandfService: FandfService
)(implicit ec: ExecutionContext, configDecorator: ConfigDecorator)
    extends AuthRetrievals
    with AuthorisedFunctions
    with Logging {

  private type RetrievalsType = Option[String] ~ Option[AffinityGroup] ~ Enrolments ~ Option[Credentials] ~
    Option[String] ~ ConfidenceLevel ~ Option[String]

  override def parser: BodyParser[AnyContent]               = mcc.parsers.defaultBodyParser
  override protected def executionContext: ExecutionContext = mcc.executionContext

  private def addRedirect(profileUrl: Option[String]): Option[String] =
    for {
      url    <- profileUrl
      parsed <- Url.parseOption(url)
    } yield parsed.replaceParams("redirect_uri", configDecorator.pertaxFrontendBackLink).toString()

  private def trimRequest[A](request: Request[A]): Request[A] = request
    .map {
      case AnyContentAsFormUrlEncoded(data) =>
        AnyContentAsFormUrlEncoded(data.map { case (k, v) => (k, v.map(_.trim)) })
      case other                            => other
    }
    .asInstanceOf[Request[A]]

  private def applyCookie(result: Result, helperResult: TrustedHelperResult): Result = helperResult match {
    case Found(_) =>
      result.withCookies(
        Cookie(
          name = "trustedHelper",
          value = "true",
          httpOnly = true,
          path = "/"
        )
      )
    case NotFound =>
      result.discardingCookies(
        DiscardingCookie(name = "trustedHelper", path = "/")
      )
    case Error(_) =>
      result
  }

  override def invokeBlock[A](
    request: Request[A],
    block: AuthenticatedRequest[A] => Future[Result]
  ): Future[Result] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    val retrievals: Retrieval[RetrievalsType] =
      Retrievals.nino and
        Retrievals.affinityGroup and
        Retrievals.allEnrolments and
        Retrievals.credentials and
        Retrievals.credentialStrength and
        Retrievals.confidenceLevel and
        Retrievals.profile

    authorised().retrieve(retrievals) {
      case Some(nino) ~ affinityGroup ~ Enrolments(enrolments) ~ Some(credentials) ~
          Some(CredentialStrength.strong) ~ confidenceLevel ~ profile =>
        for {
          userAnswers                         <- journeyCacheRepository.get(hc)
          trustedHelperResult                 <- fandfService.getTrustedHelper()
          trustedHelper: Option[TrustedHelper] = trustedHelperResult match {
                                                   case Found(helper) => Some(helper)
                                                   case NotFound      => None
                                                   case Error(ex)     =>
                                                     logger.warn(
                                                       "TrustedHelper service call failed, continuing without it",
                                                       ex
                                                     )
                                                     None
                                                 }

          authRequest = AuthenticatedRequest[A](
                          authNino = Nino(nino),
                          credentials = credentials,
                          confidenceLevel = confidenceLevel,
                          trustedHelper = trustedHelper,
                          profile = addRedirect(profile),
                          enrolments = enrolments,
                          request = trimRequest(request),
                          affinityGroup = affinityGroup,
                          userAnswers = userAnswers,
                          trustedHelperFromSession = request.cookies.get("trustedHelper").isDefined
                        )

          result <- block(authRequest)
        } yield applyCookie(result, trustedHelperResult)

      case _ =>
        Future.failed(new RuntimeException("Can't authenticate user"))
    }
  }
}

@ImplementedBy(classOf[AuthRetrievalsImpl])
trait AuthRetrievals extends ActionBuilder[AuthenticatedRequest, AnyContent] with ActionFunction[Request, Request]

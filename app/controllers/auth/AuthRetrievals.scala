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
import controllers.auth.requests.AuthenticatedRequest
import io.lemonlabs.uri.Url
import models.UserName
import play.api.Logging
import play.api.mvc._
import repositories.JourneyCacheRepository
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.{Retrievals, TrustedHelper}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name, Retrieval, ~}
import uk.gov.hmrc.domain
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

class AuthRetrievalsImpl @Inject() (
  val authConnector: AuthConnector,
  mcc: MessagesControllerComponents,
  journeyCacheRepository: JourneyCacheRepository
)(implicit ec: ExecutionContext, configDecorator: ConfigDecorator)
    extends AuthRetrievals
    with AuthorisedFunctions
    with Logging {

  private def addRedirect(profileUrl: Option[String]): Option[String] =
    for {
      url <- profileUrl
      res <- Url.parseOption(url).filter(parsed => parsed.schemeOption.isDefined)
    } yield res.replaceParams("redirect_uri", configDecorator.pertaxFrontendBackLink).toString()

  type RetrievalsType = Option[String] ~ Option[AffinityGroup] ~ Enrolments ~ Option[Credentials] ~ Option[
    String
  ] ~ ConfidenceLevel ~ Option[Name] ~ Option[TrustedHelper] ~ Option[String]

  //scalastyle:off cyclomatic.complexity
  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    val retrievals: Retrieval[RetrievalsType] =
      Retrievals.nino and Retrievals.affinityGroup and Retrievals.allEnrolments and Retrievals.credentials and Retrievals.credentialStrength and
        Retrievals.confidenceLevel and Retrievals.name and Retrievals.trustedHelper and Retrievals.profile

    authorised()
      .retrieve(retrievals) {
        case Some(nino) ~
            affinityGroup ~
            Enrolments(enrolments) ~
            Some(credentials) ~
            Some(CredentialStrength.strong) ~
            confidenceLevel ~
            name ~
            trustedHelper ~
            profile =>
          journeyCacheRepository.get(hc).flatMap { userAnswers =>
            val trimmedRequest: Request[A] = request
              .map {
                case AnyContentAsFormUrlEncoded(data) =>
                  AnyContentAsFormUrlEncoded(data.map { case (key, vals) =>
                    (key, vals.map(_.trim))
                  })
                case b                                => b
              }
              .asInstanceOf[Request[A]]

            val authenticatedRequest = AuthenticatedRequest[A](
              authNino = Nino(nino),
              nino = Some(
                trustedHelper.fold(domain.Nino(nino))(helper => domain.Nino(helper.principalNino.getOrElse(nino)))
              ),
              credentials = credentials,
              confidenceLevel = confidenceLevel,
              name = Some(
                UserName(
                  trustedHelper.fold(name.getOrElse(Name(None, None)))(helper => Name(Some(helper.principalName), None))
                )
              ),
              trustedHelper = trustedHelper,
              profile = addRedirect(profile),
              enrolments = enrolments,
              request = trimmedRequest,
              affinityGroup = affinityGroup,
              userAnswers = userAnswers
            )
            block(authenticatedRequest)
          }
        case _ => throw new RuntimeException("Can't authenticate user")
      }
  }

  override def parser: BodyParser[AnyContent] = mcc.parsers.defaultBodyParser

  override protected def executionContext: ExecutionContext = mcc.executionContext
}

@ImplementedBy(classOf[AuthRetrievalsImpl])
trait AuthRetrievals extends ActionBuilder[AuthenticatedRequest, AnyContent] with ActionFunction[Request, Request]

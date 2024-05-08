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
import controllers.routes
import io.lemonlabs.uri.Url
import models.UserName
import models.admin.SingleAccountCheckToggle
import play.api.Logging
import play.api.i18n.Messages
import play.api.mvc.Results.Redirect
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name, Retrieval, ~}
import play.api.mvc._
import uk.gov.hmrc.auth.core.AffinityGroup.{Agent, Individual, Organisation}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.{Retrievals, TrustedHelper}
import uk.gov.hmrc.domain
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import util.{BusinessHours, EnrolmentsHelper}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class AuthRetrievalsImpl @Inject() (
  val authConnector: AuthConnector,
  sessionAuditor: SessionAuditor,
  mcc: MessagesControllerComponents,
  enrolmentsHelper: EnrolmentsHelper,
  businessHours: BusinessHours,
  featureFlagService: FeatureFlagService
)(implicit ec: ExecutionContext, configDecorator: ConfigDecorator)
    extends AuthRetrievals
    with AuthorisedFunctions
    with Logging {

  private def addRedirect(profileUrl: Option[String]): Option[String] =
    for {
      url <- profileUrl
      res <- Url.parseOption(url).filter(parsed => parsed.schemeOption.isDefined)
    } yield res.replaceParams("redirect_uri", configDecorator.pertaxFrontendBackLink).toString()

  private object LT200 {
    def unapply(confLevel: ConfidenceLevel): Option[ConfidenceLevel] =
      if (confLevel.level < ConfidenceLevel.L200.level) Some(confLevel) else None
  }

  private object GTOE200 {
    def unapply(confLevel: ConfidenceLevel): Option[ConfidenceLevel] =
      if (confLevel.level >= ConfidenceLevel.L200.level) Some(confLevel) else None
  }

  type RetrievalsType = Option[String] ~ Option[AffinityGroup] ~ Enrolments ~ Option[Credentials] ~ Option[
    String
  ] ~ ConfidenceLevel ~ Option[Name] ~ Option[TrustedHelper] ~ Option[String]

  //scalastyle:off cyclomatic.complexity
  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
    implicit val messages: Messages = mcc.messagesApi.preferred(request)

    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    val retrievals: Retrieval[RetrievalsType] =
      Retrievals.nino and Retrievals.affinityGroup and Retrievals.allEnrolments and Retrievals.credentials and Retrievals.credentialStrength and
        Retrievals.confidenceLevel and Retrievals.name and Retrievals.trustedHelper and Retrievals.profile

    authorised()
      .retrieve(retrievals) {

        case _ ~ Some(Individual) ~ _ ~ _ ~ (Some(CredentialStrength.weak) | None) ~ _ ~ _ ~ _ ~ _ =>
          upliftCredentialStrength

        case _ ~ Some(Individual) ~ _ ~ _ ~ _ ~ LT200(_) ~ _ ~ _ ~ _ =>
          upliftConfidenceLevel(request)

        case _ ~ Some(Organisation | Agent) ~ _ ~ _ ~ _ ~ LT200(_) ~ _ ~ _ ~ _ =>
          upliftConfidenceLevel(request)

        case _ ~ Some(Organisation | Agent) ~ _ ~ _ ~ (Some(CredentialStrength.weak) | None) ~ _ ~ _ ~ _ ~ _ =>
          upliftCredentialStrength

        case None ~ affinityGroup ~ _ ~ _ ~ credentialStrength ~ confidenceLevel ~ _ ~ _ ~ _ =>
          // After the uplifts required above a nino should always be present
          val affinityGroupText      = affinityGroup.map("an " + _).getOrElse("a user without affinity group")
          val credentialStrengthText = credentialStrength.map(_ + " credentials").getOrElse("no credential strength")
          throw new RuntimeException(
            s"No nino found in session for $affinityGroupText with confidence level ${confidenceLevel.toString} and $credentialStrengthText"
          )

        case Some(nino) ~
            affinityGroup ~
            Enrolments(enrolments) ~
            Some(credentials) ~
            Some(CredentialStrength.strong) ~
            GTOE200(confidenceLevel) ~
            name ~
            trustedHelper ~
            profile =>
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
            nino = Some(trustedHelper.fold(domain.Nino(nino))(helper => domain.Nino(helper.principalNino))),
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
            affinityGroup = affinityGroup
          )

          lazy val updatedResult = for {
            result        <- block(authenticatedRequest)
            updatedResult <- sessionAuditor.auditOnce(authenticatedRequest, result)
          } yield updatedResult

          featureFlagService.get(SingleAccountCheckToggle).flatMap { toggle =>
            (toggle.isEnabled, businessHours.isTrue(LocalDateTime.now())) match {
              case (true, true) =>
                implicit val request: AuthenticatedRequest[A] = authenticatedRequest
                enrolmentsHelper
                  .singleAccountEnrolmentPresent(enrolments, domain.Nino(nino))
                  .fold(
                    left => Future.successful(left),
                    status =>
                      if (status) {
                        updatedResult
                      } else {
                        Future.successful(Redirect(configDecorator.taxEnrolmentDeniedRedirect(request.uri)))
                      }
                  )
              case _            => updatedResult
            }
          }

        case _ => throw new RuntimeException("Can't authenticate user")
      }
  }

  private def upliftCredentialStrength: Future[Result] =
    Future.successful(
      Redirect(
        configDecorator.multiFactorAuthenticationUpliftUrl,
        Map(
          "origin"      -> Seq(configDecorator.origin),
          "continueUrl" -> Seq(configDecorator.pertaxFrontendForAuthHost + configDecorator.personalAccount)
        )
      )
    )

  private def upliftConfidenceLevel(request: Request[_]): Future[Result] =
    Future.successful(
      Redirect(
        configDecorator.identityVerificationUpliftUrl,
        Map(
          "origin"          -> Seq(configDecorator.origin),
          "confidenceLevel" -> Seq(ConfidenceLevel.L200.toString),
          "completionURL"   -> Seq(
            configDecorator.pertaxFrontendForAuthHost + routes.ApplicationController
              .showUpliftJourneyOutcome(Some(RedirectUrl(request.uri)))
          ),
          "failureURL"      -> Seq(
            configDecorator.pertaxFrontendForAuthHost + routes.ApplicationController
              .showUpliftJourneyOutcome(Some(RedirectUrl(request.uri)))
          )
        )
      )
    )

  override def parser: BodyParser[AnyContent] = mcc.parsers.defaultBodyParser

  override protected def executionContext: ExecutionContext = mcc.executionContext
}

@ImplementedBy(classOf[AuthRetrievalsImpl])
trait AuthRetrievals extends ActionBuilder[AuthenticatedRequest, AnyContent] with ActionFunction[Request, Request]

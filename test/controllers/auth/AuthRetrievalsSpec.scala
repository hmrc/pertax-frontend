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

import controllers.auth.requests.AuthenticatedRequest
import models.{UserAnswers, UserName}
import org.mockito.ArgumentMatchers.any
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.JourneyCacheRepository
import services.partials.MessageFrontendService
import testUtils.RetrievalOps._
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.auth.core.AffinityGroup.Individual
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.domain.SaUtrGenerator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import util.EnrolmentsHelper

import scala.concurrent.Future

class AuthRetrievalsSpec extends BaseSpec {

  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
    .configure(Map("metrics.enabled" -> false))
    .build()

  val mockAuthConnector: AuthConnector                = mock[AuthConnector]
  lazy val controllerComponents: ControllerComponents = app.injector.instanceOf[ControllerComponents]
  val enrolmentsHelper: EnrolmentsHelper              = app.injector.instanceOf[EnrolmentsHelper]
  val sessionAuditor                                  =
    new SessionAuditorFake(app.injector.instanceOf[AuditConnector], enrolmentsHelper)

  val mockMessageFrontendService: MessageFrontendService = mock[MessageFrontendService]
  val mockJourneyCacheRepository: JourneyCacheRepository = mock[JourneyCacheRepository]

  class Harness(authAction: AuthRetrievalsImpl) extends InjectedController {
    def onPageLoad: Action[AnyContent] = authAction { request: AuthenticatedRequest[AnyContent] =>
      Ok(
        s"Nino: ${request.nino.getOrElse("fail").toString}, Enrolments: ${request.enrolments.toString}," +
          s"trustedHelper: ${request.trustedHelper}, profileUrl: ${request.profile}"
      )
    }
  }

  type AuthRetrievals =
    Option[String] ~ Option[AffinityGroup] ~ Enrolments ~ Option[Credentials] ~
      Option[String] ~ ConfidenceLevel ~ Option[UserName] ~ Option[TrustedHelper] ~
      Option[String]

  val nino: String                                               = Fixtures.fakeNino.nino
  val fakeCredentials: Credentials                               = Credentials("foo", "bar")
  val fakeCredentialStrength: String                             = CredentialStrength.strong
  val fakeConfidenceLevel: ConfidenceLevel                       = ConfidenceLevel.L200
  val enrolmentHelper: EnrolmentsHelper                          = inject[EnrolmentsHelper]
  def messagesControllerComponents: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]

  def fakeEnrolments(utr: String): Set[Enrolment] = Set(
    Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", utr)), "Activated"),
    Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", nino)), "None", None)
  )

  def retrievals(
    nino: Option[String] = Some(nino.toString),
    affinityGroup: Option[AffinityGroup] = Some(Individual),
    saEnrolments: Enrolments = Enrolments(
      Set(Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", nino.toString)), "None", None))
    ),
    credentialStrength: String = CredentialStrength.strong,
    confidenceLevel: ConfidenceLevel = ConfidenceLevel.L200,
    trustedHelper: Option[TrustedHelper] = None,
    profileUrl: Option[String] = None
  ): Harness = {

    when(mockAuthConnector.authorise[AuthRetrievals](any(), any())(any(), any())) thenReturn Future.successful(
      nino ~ affinityGroup ~ saEnrolments ~ Some(fakeCredentials) ~ Some(
        credentialStrength
      ) ~ confidenceLevel ~ None ~ trustedHelper ~ profileUrl
    )

    when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(UserAnswers.empty("id")))

    val authAction =
      new AuthRetrievalsImpl(
        mockAuthConnector,
        messagesControllerComponents,
        mockJourneyCacheRepository
      )(implicitly, config)

    new Harness(authAction)
  }

  val ivRedirectUrl =
    "http://localhost:9948/iv-stub/uplift?origin=PERTAX&confidenceLevel=200&completionURL=http%3A%2F%2Flocalhost%3A9232%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account&failureURL=http%3A%2F%2Flocalhost%3A9232%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account"

  "A user with a nino and an SA enrolment must" must {
    "create an authenticated request" in {

      val utr = new SaUtrGenerator().nextSaUtr.utr

      val controller = retrievals(saEnrolments = Enrolments(fakeEnrolments(utr)))

      val result = controller.onPageLoad(FakeRequest("", ""))
      status(result) mustBe OK
      contentAsString(result) must include(nino)
      contentAsString(result) must include(utr)
    }
  }

  "A user with trustedHelper must" must {
    "create an authenticated request containing the trustedHelper" in {

      val fakePrincipalNino = Fixtures.fakeNino.toString()

      val controller =
        retrievals(trustedHelper =
          Some(TrustedHelper("principalName", "attorneyName", "returnUrl", Some(fakePrincipalNino)))
        )

      val result = controller.onPageLoad(FakeRequest("", ""))
      status(result) mustBe OK
      contentAsString(result) must include(
        s"Some(TrustedHelper(principalName,attorneyName,returnUrl,Some($fakePrincipalNino)))"
      )
    }
  }

  "A user with a SCP Profile Url must include a redirect uri back to the home controller" in {
    val controller = retrievals(profileUrl = Some("http://www.google.com/"))

    val result = controller.onPageLoad(FakeRequest("", ""))
    status(result) mustBe OK
    contentAsString(result) must include(s"http://www.google.com/?redirect_uri=${config.pertaxFrontendBackLink}")
  }

  "A user without a SCP Profile Url must continue to not have one" in {
    val controller = retrievals(profileUrl = None)

    val result = controller.onPageLoad(FakeRequest("", ""))
    status(result) mustBe OK
    contentAsString(result) mustNot include(s"http://www.google.com/?redirect_uri=${config.pertaxFrontendBackLink}")
  }

  "A user with a SCP Profile Url that is not valid must strip out the SCP Profile Url" in {
    val controller = retrievals(profileUrl = Some("notAUrl"))

    val result = controller.onPageLoad(FakeRequest("", ""))
    status(result) mustBe OK
    contentAsString(result) mustNot include(config.pertaxFrontendBackLink)
  }
}

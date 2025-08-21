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
import models.UserAnswers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.JourneyCacheRepository
import testUtils.RetrievalOps.*
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.AffinityGroup.Individual
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.domain.SaUtrGenerator
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.sca.models.{PtaMinMenuConfig, WrapperDataResponse}
import uk.gov.hmrc.sca.utils.Keys

import scala.concurrent.Future

class AuthRetrievalsSpec extends BaseSpec {

  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
    .configure(Map("metrics.enabled" -> false))
    .build()

  private val mockAuthConnector: AuthConnector                   = mock[AuthConnector]
  private val mockJourneyCacheRepository: JourneyCacheRepository = mock[JourneyCacheRepository]

  private class Harness(authAction: AuthRetrievalsImpl) extends InjectedController {
    def onPageLoad: Action[AnyContent] = authAction { (request: AuthenticatedRequest[AnyContent]) =>
      Ok(
        s"Nino: ${request.authNino.nino}, Enrolments: ${request.enrolments.toString}," +
          s"trustedHelper: ${request.trustedHelper}, profileUrl: ${request.profile}"
      )
    }
  }

  private type AuthRetrievals =
    Option[String] ~ Option[AffinityGroup] ~ Enrolments ~ Option[Credentials] ~ Option[String] ~ ConfidenceLevel ~
      Option[String]

  private val nino: String                                               = Fixtures.fakeNino.nino
  private val fakeCredentials: Credentials                               = Credentials("foo", "bar")
  private def messagesControllerComponents: MessagesControllerComponents =
    app.injector.instanceOf[MessagesControllerComponents]

  private def fakeEnrolments(utr: String): Set[Enrolment] = Set(
    Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", utr)), "Activated"),
    Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", nino)), "None", None)
  )

  private def retrievals(
    nino: Option[String] = Some(nino.toString),
    affinityGroup: Option[AffinityGroup] = Some(Individual),
    saEnrolments: Enrolments = Enrolments(
      Set(Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", nino.toString)), "None", None))
    ),
    credentialStrength: String = CredentialStrength.strong,
    confidenceLevel: ConfidenceLevel = ConfidenceLevel.L200,
    profileUrl: Option[String] = None
  ): Harness = {

    when(mockAuthConnector.authorise[AuthRetrievals](any(), any())(any(), any())) thenReturn Future.successful(
      nino ~ affinityGroup ~ saEnrolments ~ Some(fakeCredentials) ~ Some(
        credentialStrength
      ) ~ confidenceLevel ~ profileUrl
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
      val controller =
        retrievals()

      val testTrustedHelper                                                 =
        TrustedHelper("principalName", "attorneyName", "returnUrl", Some(generatedTrustedHelperNino.nino))
      val fakeRequestWithTrustedHelper: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("", "")
        .addAttr(
          Keys.wrapperDataKey,
          WrapperDataResponse(
            Nil,
            PtaMinMenuConfig("", ""),
            Nil,
            Nil,
            Some(0),
            Some(testTrustedHelper)
          )
        )
        .asInstanceOf[FakeRequest[AnyContentAsEmpty.type]]
        .withSession(SessionKeys.sessionId -> "foo")
        .asInstanceOf[FakeRequest[AnyContentAsEmpty.type]]

      val result = controller.onPageLoad(fakeRequestWithTrustedHelper)
      status(result) mustBe OK
      contentAsString(result) must include(
        s"Some(TrustedHelper(principalName,attorneyName,returnUrl,Some($generatedTrustedHelperNino)))"
      )
    }
  }

  "A user with a SCP Profile Url must include a redirect uri back to the home controller" in {
    val controller = retrievals(profileUrl = Some("http://www.google.com/"))

    val result = controller.onPageLoad(FakeRequest("", ""))
    status(result) mustBe OK
    contentAsString(result) must include(s"http://www.google.com/?redirect_uri=${config.pertaxFrontendBackLink}")
  }

  "A user with a One login Profile relative Url must include a redirect uri back to the home controller" in {
    val controller = retrievals(profileUrl = Some("/relative/url/"))

    val result = controller.onPageLoad(FakeRequest("", ""))
    status(result) mustBe OK
    contentAsString(result) must include(s"/relative/url/?redirect_uri=${config.pertaxFrontendBackLink}")
  }

  "A user without a SCP Profile Url must continue to not have one" in {
    val controller = retrievals(profileUrl = None)

    val result = controller.onPageLoad(FakeRequest("", ""))
    status(result) mustBe OK
    contentAsString(result) mustNot include(s"http://www.google.com/?redirect_uri=${config.pertaxFrontendBackLink}")
  }

  "A user with a SCP Profile Url that is not valid must strip out the SCP Profile Url" in {
    val controller = retrievals(profileUrl = Some("://notAUrl"))

    val result = controller.onPageLoad(FakeRequest("", ""))
    status(result) mustBe OK
    contentAsString(result) mustNot include(config.pertaxFrontendBackLink)
  }
}

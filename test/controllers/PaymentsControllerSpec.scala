/*
 * Copyright 2019 HM Revenue & Customs
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

package controllers

import config.ConfigDecorator
import connectors._
import controllers.auth.requests.UserRequest
import models.{ActivatedOnlineFilerSelfAssessmentUser, UserDetails}
import org.joda.time.DateTime
import org.mockito.Matchers.{any, eq => meq}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.inject.bind
import play.api.libs.json.JsBoolean
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import services.partials.{CspPartialService, MessageFrontendService}
import services._
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.binders.Origin
import uk.gov.hmrc.time.CurrentTaxYear
import util.Fixtures.buildFakeRequestWithAuth
import util.{BaseSpec, Fixtures, LocalPartialRetriever}

import scala.concurrent.Future

class PaymentsControllerSpec extends BaseSpec with CurrentTaxYear with MockitoSugar {

  override def now: () => DateTime = DateTime.now

  lazy val fakeRequest = FakeRequest("", "")
  lazy val userRequest = UserRequest(
    None,
    None,
    None,
    ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111")),
    "SomeAuth",
    ConfidenceLevel.L200,
    None,
    None,
    None,
    None,
    fakeRequest)

  val mockConfigDecorator = mock[ConfigDecorator]
  val mockAuditConnector = mock[PertaxAuditConnector]

  override implicit lazy val app: Application = localGuiceApplicationBuilder
    .overrides(
      bind[CitizenDetailsService].toInstance(mock[CitizenDetailsService]),
      bind[MessageFrontendService].toInstance(mock[MessageFrontendService]),
      bind[CspPartialService].toInstance(mock[CspPartialService]),
      bind[PertaxAuthConnector].toInstance(mock[PertaxAuthConnector]),
      bind[PertaxAuditConnector].toInstance(mockAuditConnector),
      bind[FrontEndDelegationConnector].toInstance(mock[FrontEndDelegationConnector]),
      bind[UserDetailsService].toInstance(mock[UserDetailsService]),
      bind[SelfAssessmentService].toInstance(mock[SelfAssessmentService]),
      bind[LocalPartialRetriever].toInstance(mock[LocalPartialRetriever]),
      bind[ConfigDecorator].toInstance(mockConfigDecorator),
      bind[LocalSessionCache].toInstance(mock[LocalSessionCache]),
      bind[PayApiConnector].toInstance(mock[PayApiConnector])
    )
    .build()

  trait LocalSetup {

    lazy val nino: Nino = Fixtures.fakeNino
    lazy val personDetailsResponse: PersonDetailsResponse = PersonDetailsSuccessResponse(Fixtures.buildPersonDetails)
    lazy val confidenceLevel: ConfidenceLevel = ConfidenceLevel.L200
    lazy val withPaye: Boolean = true

    val allowLowConfidenceSA = false

    lazy val controller = {

      val c = injected[PaymentsController]

      when(c.userDetailsService.getUserDetails(any())(any())) thenReturn {
        Future.successful(Some(UserDetails(UserDetails.GovernmentGatewayAuthProvider)))
      }
      when(c.citizenDetailsService.personDetails(meq(nino))(any())) thenReturn {
        Future.successful(personDetailsResponse)
      }
      when(mockAuditConnector.sendEvent(any())(any(), any())) thenReturn {
        Future.successful(AuditResult.Success)
      }
      when(injected[LocalSessionCache].fetch()(any(), any())) thenReturn {
        Future.successful(Some(CacheMap("id", Map("urBannerDismissed" -> JsBoolean(true)))))
      }
      when(injected[MessageFrontendService].getUnreadMessageCount(any())) thenReturn {
        Future.successful(None)
      }

      when(mockConfigDecorator.defaultOrigin) thenReturn Origin("PERTAX")
      when(mockConfigDecorator.getFeedbackSurveyUrl(Origin("PERTAX"))) thenReturn "/feedback/PERTAX"
      when(mockConfigDecorator.ssoUrl) thenReturn Some("ssoUrl")
      when(mockConfigDecorator.analyticsToken) thenReturn Some("N/A")

      c
    }
  }

  "makePayment" should {
    "redirect to the response's nextUrl" in new LocalSetup {

      val expectedNextUrl = "someNextUrl"
      val createPaymentResponse = CreatePayment("someJourneyId", expectedNextUrl)

      when(controller.payApiConnector.createPayment(any())(any(), any()))
        .thenReturn(Future.successful(Some(createPaymentResponse)))

      val r = controller.makePayment()(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("someNextUrl")
    }

    "redirect to a BAD_REQUEST page if createPayment failed" in new LocalSetup {

      when(controller.payApiConnector.createPayment(any())(any(), any()))
        .thenReturn(Future.successful(None))

      val r = controller.makePayment()(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe BAD_REQUEST
    }
  }
}

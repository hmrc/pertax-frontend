/*
 * Copyright 2020 HM Revenue & Customs
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
import connectors.PayApiConnector
import controllers.auth._
import models._
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.exceptions.TestFailedException
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.i18n.MessagesApi
import play.api.inject.bind
import play.api.mvc.{AnyContentAsEmpty, MessagesControllerComponents, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, redirectLocation, _}
import services.SelfAssessmentPaymentsService
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.Upstream5xxResponse
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.renderer.TemplateRenderer
import uk.gov.hmrc.time.CurrentTaxYear
import util.BaseSpec
import util.Fixtures.buildFakeRequestWithAuth

import scala.concurrent.{ExecutionContext, Future}

class SelfAssessmentControllerSpec extends BaseSpec with CurrentTaxYear with MockitoSugar {
  override def now: () => DateTime = DateTime.now

  val mockAuditConnector = mock[AuditConnector]
  val mockAuthAction = mock[AuthAction]
  val mockSelfAssessmentStatusAction = mock[SelfAssessmentStatusAction]
  val mockPayApiConnector = mock[PayApiConnector]
  val mockSelfAssessmentPaymentsService = mock[SelfAssessmentPaymentsService]

  val saUtr = SaUtr(new SaUtrGenerator().nextSaUtr.utr)
  val defaultFakeAuthJourney = new FakeAuthJourney(NotYetActivatedOnlineFilerSelfAssessmentUser(saUtr))

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[AuditConnector].toInstance(mockAuditConnector),
      bind[AuthAction].toInstance(mockAuthAction),
      bind[SelfAssessmentStatusAction].toInstance(mockSelfAssessmentStatusAction),
      bind[AuthJourney].toInstance(defaultFakeAuthJourney),
      bind[SelfAssessmentPaymentsService].toInstance(mockSelfAssessmentPaymentsService)
    )
    .build()

  override def beforeEach: Unit =
    reset(mockAuditConnector, mockAuthAction, mockSelfAssessmentStatusAction)

  trait LocalSetup {

    def fakeAuthJourney = defaultFakeAuthJourney

    def controller =
      new SelfAssessmentController(
        mockSelfAssessmentPaymentsService,
        fakeAuthJourney,
        injected[WithBreadcrumbAction],
        mockAuditConnector,
        injected[MessagesControllerComponents]
      )(mockLocalPartialRetriever, injected[ConfigDecorator], injected[TemplateRenderer], injected[ExecutionContext])

    when(mockAuditConnector.sendEvent(any())(any(), any())) thenReturn {
      Future.successful(AuditResult.Success)
    }

    def routeWrapper[T](req: FakeRequest[AnyContentAsEmpty.type]): Option[Future[Result]] = {
      controller //Call to inject mocks
      route(app, req)
    }
  }

  "Calling SelfAssessmentController.handleSelfAssessment" should {

    "return 303 when called with a GG user that needs to activate their SA enrolment." in new LocalSetup {
      val result = controller.handleSelfAssessment()(FakeRequest())
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(
        "/enrolment-management-frontend/IR-SA/get-access-tax-scheme?continue=/personal-account")
    }

    "return 303 when called with a GG user that is SA or has an SA enrollment in another account." in new LocalSetup {
      override def fakeAuthJourney: FakeAuthJourney = new FakeAuthJourney(WrongCredentialsSelfAssessmentUser(saUtr))

      val result = controller.handleSelfAssessment()(FakeRequest())
      status(result) shouldBe SEE_OTHER
      redirectLocation(await(result)) shouldBe Some(routes.SaWrongCredentialsController.landingPage().url)
    }

    "return 200 when called with a GG user that is has a UTR but no enrolment" in new LocalSetup {
      override def fakeAuthJourney: FakeAuthJourney = new FakeAuthJourney(NotEnrolledSelfAssessmentUser(saUtr))

      val result = controller.handleSelfAssessment()(FakeRequest())
      status(result) shouldBe SEE_OTHER
      redirectLocation(await(result)) shouldBe Some(routes.SelfAssessmentController.requestAccess().url)
    }
  }

  "Calling SelfAssessmentController.ivExemptLandingPage" should {
    "return 200 for a user who has logged in with GG linked and has a full SA enrollment" in new LocalSetup {
      override def fakeAuthJourney: FakeAuthJourney = new FakeAuthJourney(ActivatedOnlineFilerSelfAssessmentUser(saUtr))

      val result = controller.ivExemptLandingPage(None)(FakeRequest())

      status(result) shouldBe OK
    }

    "redirect to the SA activation page on the portal for a user logged in with GG linked to SA which is not yet activated" in new LocalSetup {
      override def fakeAuthJourney: FakeAuthJourney =
        new FakeAuthJourney(NotYetActivatedOnlineFilerSelfAssessmentUser(saUtr))

      val result = controller.ivExemptLandingPage(None)(FakeRequest())
      val doc = Jsoup.parse(contentAsString(result))
      status(result) shouldBe OK

      doc
        .getElementsByClass("heading-large")
        .toString()
        .contains("Activate your Self Assessment registration") shouldBe true
    }

    "redirect to 'Find out how to access your Self Assessment' page for a user who has a SAUtr but logged into the wrong GG account" in new LocalSetup {
      override def fakeAuthJourney: FakeAuthJourney = new FakeAuthJourney(WrongCredentialsSelfAssessmentUser(saUtr))

      val result = controller.ivExemptLandingPage(None)(FakeRequest())
      val doc = Jsoup.parse(contentAsString(result))
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.SaWrongCredentialsController.landingPage().url)
    }

    "render the page for a user who has a SAUtr but has never enrolled" in new LocalSetup {
      override def fakeAuthJourney: FakeAuthJourney = new FakeAuthJourney(NotEnrolledSelfAssessmentUser(saUtr))

      val result = controller.ivExemptLandingPage(None)(FakeRequest())
      status(result) shouldBe SEE_OTHER
      redirectLocation(await(result)) shouldBe Some(routes.SelfAssessmentController.requestAccess().url)
    }

    "redirect to 'We cannot confirm your identity' page for a user who has no SAUTR" in new LocalSetup {
      override def fakeAuthJourney: FakeAuthJourney = new FakeAuthJourney(NonFilerSelfAssessmentUser)

      val result = controller.ivExemptLandingPage(None)(FakeRequest())
      val doc = Jsoup.parse(contentAsString(result))
      status(result) shouldBe OK
    }

    "return bad request when continueUrl is not relative" in new LocalSetup {
      override def fakeAuthJourney: FakeAuthJourney = new FakeAuthJourney(NonFilerSelfAssessmentUser)

      val result: Future[Result] =
        routeWrapper(buildFakeRequestWithAuth("GET", "/personal-account/sa-continue?continueUrl=http://example.com"))
          .getOrElse(throw new TestFailedException("Failed to route", 0))

      status(result) shouldBe BAD_REQUEST
    }
  }

  "Calling SelfAssessmentController.viewPayments" should {

    "redirect to the home page" in new LocalSetup {

      override def fakeAuthJourney: FakeAuthJourney = new FakeAuthJourney(NonFilerSelfAssessmentUser)

      val result =
        routeWrapper(buildFakeRequestWithAuth("GET", routes.SelfAssessmentController.viewPayments().url))
          .getOrElse(throw new TestFailedException("Failed to route", 0))

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(routes.HomeController.index().url)
    }
  }
}

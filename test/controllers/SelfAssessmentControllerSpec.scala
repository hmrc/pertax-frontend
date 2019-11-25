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
import connectors.{PayApiConnector, PertaxAuditConnector}
import controllers.auth._
import models._
import org.joda.time.{DateTime, LocalDate}
import org.jsoup.Jsoup
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.exceptions.TestFailedException
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.i18n.MessagesApi
import play.api.inject.bind
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, redirectLocation, _}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.renderer.TemplateRenderer
import uk.gov.hmrc.time.CurrentTaxYear
import util.BaseSpec
import util.Fixtures.buildFakeRequestWithAuth
import viewmodels.SelfAssessmentPayment

import scala.concurrent.Future

class SelfAssessmentControllerSpec extends BaseSpec with CurrentTaxYear with MockitoSugar {
  override def now: () => DateTime = DateTime.now

  val mockAuditConnector = mock[PertaxAuditConnector]
  val mockAuthAction = mock[AuthAction]
  val mockSelfAssessmentStatusAction = mock[SelfAssessmentStatusAction]

  val saUtr = SaUtr("1111111111")
  val defaultFakeAuthJourney = new FakeAuthJourney(NotYetActivatedOnlineFilerSelfAssessmentUser(saUtr))

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[PertaxAuditConnector].toInstance(mockAuditConnector),
      bind[AuthAction].toInstance(mockAuthAction),
      bind[SelfAssessmentStatusAction].toInstance(mockSelfAssessmentStatusAction),
      bind[AuthJourney].toInstance(defaultFakeAuthJourney)
    )
    .build()

  override def beforeEach: Unit =
    reset(mockAuditConnector, mockAuthAction, mockSelfAssessmentStatusAction)

  trait LocalSetup {
    val messagesApi = injected[MessagesApi]

    def fakeAuthJourney = defaultFakeAuthJourney

    def controller =
      new SelfAssessmentController(
        messagesApi,
        injected[PayApiConnector],
        fakeAuthJourney,
        injected[WithBreadcrumbAction],
        mockAuditConnector
      )(mockLocalPartialRetriever, injected[ConfigDecorator], injected[TemplateRenderer])

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

    "return 200 and render viewPayments page" when {

      "a user is an Activated SA user" in new LocalSetup {

        override def fakeAuthJourney: FakeAuthJourney =
          new FakeAuthJourney(ActivatedOnlineFilerSelfAssessmentUser(saUtr))

        val result: Future[Result] = controller.viewPayments()(FakeRequest())

        status(result) shouldBe OK

        Jsoup.parse(contentAsString(result)).text() should include(
          messagesApi("title.selfAssessment.viewPayments.h1")
        )

      }
    }

    "redirect to the home page" when {

      "a user is not an Activated SA user" in new LocalSetup {

        override def fakeAuthJourney: FakeAuthJourney = new FakeAuthJourney(NonFilerSelfAssessmentUser)

        val result =
          routeWrapper(buildFakeRequestWithAuth("GET", routes.SelfAssessmentController.viewPayments().url))
            .getOrElse(throw new TestFailedException("Failed to route", 0))

        val doc = Jsoup.parse(contentAsString(result))

        status(result) shouldBe SEE_OTHER

        redirectLocation(result) shouldBe Some(routes.HomeController.index().url)
      }
    }

    "Calling SelfAssessmentController.filterAndSortPayments" should {

      "filter payments to only include payments in the past 60 days" in new LocalSetup {

        override def fakeAuthJourney: FakeAuthJourney =
          new FakeAuthJourney(ActivatedOnlineFilerSelfAssessmentUser(saUtr))

        implicit val localDateOrdering: Ordering[LocalDate] = Ordering.fromLessThan(_ isAfter _)

        val outlier = SelfAssessmentPayment(LocalDate.now().minusDays(61), "KT123459", 7.00)

        val list = List(
          SelfAssessmentPayment(LocalDate.now().minusDays(59), "KT123458", 361.85),
          SelfAssessmentPayment(LocalDate.now().minusDays(24), "KT123457", 21.74),
          outlier,
          SelfAssessmentPayment(LocalDate.now().minusDays(12), "KT123456", 103.05)
        )

        controller.filterAndSortPayments(list) should not contain (outlier)

      }

      "order payments from latest payment descending" in new LocalSetup {

        override def fakeAuthJourney: FakeAuthJourney =
          new FakeAuthJourney(ActivatedOnlineFilerSelfAssessmentUser(saUtr))

        implicit val localDateOrdering: Ordering[LocalDate] = Ordering.fromLessThan(_ isAfter _)

        val latest = SelfAssessmentPayment(LocalDate.now().minusDays(12), "KT123456", 103.05)

        val middle = SelfAssessmentPayment(LocalDate.now().minusDays(24), "KT123457", 21.74)

        val earliest = SelfAssessmentPayment(LocalDate.now().minusDays(59), "KT123458", 361.85)

        val list = List(middle, earliest, latest)

        controller.filterAndSortPayments(list) shouldBe List(latest, middle, earliest)

      }
    }
  }
}

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
import connectors.PertaxAuditConnector
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthAction, AuthJourney, SelfAssessmentStatusAction, WithBreadcrumbAction}
import models._
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.i18n.MessagesApi
import play.api.inject.bind
import play.api.mvc.{ActionBuilder, AnyContentAsEmpty, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, redirectLocation, _}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.renderer.TemplateRenderer
import uk.gov.hmrc.time.CurrentTaxYear
import util.BaseSpec
import util.Fixtures.buildFakeRequestWithAuth
import util.UserRequestFixture.buildUserRequest

import scala.concurrent.Future

class SelfAssessmentControllerSpec extends BaseSpec with CurrentTaxYear with MockitoSugar {
  override def now: () => DateTime = DateTime.now

  val mockAuditConnector = mock[PertaxAuditConnector]
  val mockAuthAction = mock[AuthAction]
  val mockSelfAssessmentStatusAction = mock[SelfAssessmentStatusAction]
  val mockAuthJourney = mock[AuthJourney]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[PertaxAuditConnector].toInstance(mockAuditConnector),
      bind[AuthAction].toInstance(mockAuthAction),
      bind[SelfAssessmentStatusAction].toInstance(mockSelfAssessmentStatusAction),
      bind[AuthJourney].toInstance(mockAuthJourney)
    )
    .build()

  override def beforeEach: Unit =
    reset(
      mockAuditConnector,
      mockAuthAction,
      mockSelfAssessmentStatusAction,
      mockAuthJourney
    )

  trait LocalSetup {
    val messagesApi = injected[MessagesApi]

    def controller =
      new SelfAssessmentController(
        messagesApi,
        mockAuthJourney,
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

  "Calling ApplicationController.handleSelfAssessment" should {

    "return 303 when called with a GG user that needs to activate their SA enrolment." in new LocalSetup {
      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111")),
              request = request
            ))
      })

      val result = controller.handleSelfAssessment()(FakeRequest())
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(
        "/enrolment-management-frontend/IR-SA/get-access-tax-scheme?continue=/personal-account")

    }

    "return 303 when called with a GG user that is SA or has an SA enrollment in another account." in new LocalSetup {
      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = WrongCredentialsSelfAssessmentUser(SaUtr("1111111111")),
              request = request
            ))
      })

      val result = controller.handleSelfAssessment()(FakeRequest())
      status(result) shouldBe SEE_OTHER
      redirectLocation(await(result)) shouldBe Some(routes.SaWrongCredentialsController.landingPage().url)
    }

    "return 200 when called with a GG user that is has a UTR but no enrolment" in new LocalSetup {
      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NotEnrolledSelfAssessmentUser(SaUtr("1111111111")),
              request = request
            ))
      })

      val result = controller.handleSelfAssessment()(FakeRequest())
      status(result) shouldBe OK
      contentAsString(result) should include(messagesApi("title.request_sa_access.h1"))
    }
  }

  "Calling ApplicationController.ivExemptLandingPage" should {
    "return 200 for a user who has logged in with GG linked and has a full SA enrollment" in new LocalSetup {

      when(mockAuthJourney.minimumAuthWithSelfAssessment).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              confidenceLevel = ConfidenceLevel.L50,
              request = request
            ))
      })

      val result = controller.ivExemptLandingPage(None)(FakeRequest())

      status(result) shouldBe OK

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1))
        .sendEvent(eventCaptor.capture())(any(), any()) //TODO - check captured event
    }

    "redirect to the SA activation page on the portal for a user logged in with GG linked to SA which is not yet activated" in new LocalSetup {

      when(mockAuthJourney.minimumAuthWithSelfAssessment).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              confidenceLevel = ConfidenceLevel.L50,
              saUser = NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111")),
              request = request
            ))
      })

      val result = controller.ivExemptLandingPage(None)(FakeRequest())
      val doc = Jsoup.parse(contentAsString(result))
      status(result) shouldBe OK

      doc
        .getElementsByClass("heading-large")
        .toString()
        .contains("Activate your Self Assessment registration") shouldBe true
      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1))
        .sendEvent(eventCaptor.capture())(any(), any()) //TODO - check captured event
    }

    "redirect to 'Find out how to access your Self Assessment' page for a user who has a SAUtr but logged into the wrong GG account" in new LocalSetup {

      when(mockAuthJourney.minimumAuthWithSelfAssessment).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              confidenceLevel = ConfidenceLevel.L50,
              saUser = WrongCredentialsSelfAssessmentUser(SaUtr("1111111111")),
              request = request
            ))
      })

      val result = controller.ivExemptLandingPage(None)(FakeRequest())
      val doc = Jsoup.parse(contentAsString(result))
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.SaWrongCredentialsController.landingPage().url)

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1))
        .sendEvent(eventCaptor.capture())(any(), any())
    }

    "render the page for a user who has a SAUtr but has never enrolled" in new LocalSetup {
      when(mockAuthJourney.minimumAuthWithSelfAssessment).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              confidenceLevel = ConfidenceLevel.L50,
              saUser = NotEnrolledSelfAssessmentUser(SaUtr("1111111111")),
              request = request
            ))
      })

      val result = controller.ivExemptLandingPage(None)(FakeRequest())
      val doc = Jsoup.parse(contentAsString(result))
      status(result) shouldBe OK

      doc
        .getElementsByClass("heading-xlarge")
        .toString()
        .contains(messagesApi("title.request_sa_access.h1")) shouldBe true

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1))
        .sendEvent(eventCaptor.capture())(any(), any())
    }

    "redirect to 'We cannot confirm your identity' page for a user who has no SAUTR" in new LocalSetup {
      when(mockAuthJourney.minimumAuthWithSelfAssessment).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              confidenceLevel = ConfidenceLevel.L50,
              saUser = NonFilerSelfAssessmentUser,
              request = request
            ))
      })

      val result = controller.ivExemptLandingPage(None)(FakeRequest())
      val doc = Jsoup.parse(contentAsString(result))
      status(result) shouldBe OK

      doc.getElementsByClass("heading-xlarge").toString().contains("We cannot confirm your identity") shouldBe true
      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any()) //TODO - check captured event

    }

    "return bad request when continueUrl is not relative" in new LocalSetup {
      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              request = request
            ))
      })

      val result = routeWrapper(
        buildFakeRequestWithAuth("GET", "/personal-account/sa-continue?continueUrl=http://example.com")).get

      status(result) shouldBe BAD_REQUEST
    }
  }
}

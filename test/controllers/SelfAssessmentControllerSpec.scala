/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.data.EitherT
import connectors.PayApiConnector
import controllers.auth.requests.UserRequest
import controllers.auth.{FakeAuthJourney, _}
import error.ErrorRenderer
import models._
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.ArgumentMatchers.any
import org.scalatest.exceptions.TestFailedException
import play.api.Application
import play.api.http.Status.{BAD_GATEWAY, BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND, REQUEST_TIMEOUT, SERVICE_UNAVAILABLE, UNPROCESSABLE_ENTITY}
import play.api.inject.bind
import play.api.mvc.{AnyContentAsEmpty, DefaultActionBuilder, MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, redirectLocation, _}
import services.SelfAssessmentService
import testUtils.{ActionBuilderFixture, BaseSpec}
import testUtils.Fixtures.buildFakeRequestWithAuth
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.time.CurrentTaxYear
import views.html.iv.failure.{CannotConfirmIdentityView, FailedIvContinueToActivateSaView}
import views.html.selfassessment.RequestAccessToSelfAssessmentView

import java.time.LocalDate
import scala.concurrent.Future

class SelfAssessmentControllerSpec extends BaseSpec with CurrentTaxYear {
  override def now: () => LocalDate = () => LocalDate.now()

  val mockAuditConnector: AuditConnector                         = mock[AuditConnector]
  val mockAuthAction: AuthRetrievals                             = mock[AuthRetrievals]
  val mockSelfAssessmentStatusAction: SelfAssessmentStatusAction = mock[SelfAssessmentStatusAction]
  val mockPayApiConnector: PayApiConnector                       = mock[PayApiConnector]
  val mockSelfAssessmentService: SelfAssessmentService           = mock[SelfAssessmentService]

  val saUtr: SaUtr = SaUtr(new SaUtrGenerator().nextSaUtr.utr)

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[AuditConnector].toInstance(mockAuditConnector),
      bind[SelfAssessmentStatusAction].toInstance(mockSelfAssessmentStatusAction)
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuditConnector, mockAuthAction, mockSelfAssessmentStatusAction)
  }

  trait LocalSetup {

    val mockAuthJourney: AuthJourney = mock[AuthJourney]

    when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(request = request)
        )
    })

    def defaultFakeAuthJourney: FakeAuthJourney = new FakeAuthJourney(
      authAction = mock[AuthRetrievals],
      selfAssessmentStatusAction = mock[SelfAssessmentStatusAction],
      pertaxAuthAction = mock[PertaxAuthAction],
      getPersonDetailsAction = mock[GetPersonDetailsAction],
      defaultActionBuilder = mock[DefaultActionBuilder],
      saUser = NotYetActivatedOnlineFilerSelfAssessmentUser(saUtr),
      mcc = mock[MessagesControllerComponents],
      personDetails = Some(mock[PersonDetails])
    )

    def controller: SelfAssessmentController =
      new SelfAssessmentController(
        mockAuthJourney,
        inject[WithBreadcrumbAction],
        mockAuditConnector,
        mockSelfAssessmentService,
        inject[MessagesControllerComponents],
        inject[ErrorRenderer],
        inject[FailedIvContinueToActivateSaView],
        inject[CannotConfirmIdentityView],
        inject[RequestAccessToSelfAssessmentView]
      )(config, ec)

    when(mockAuditConnector.sendEvent(any())(any(), any())) thenReturn {
      Future.successful(AuditResult.Success)
    }

    def routeWrapper[T](req: FakeRequest[AnyContentAsEmpty.type]): Option[Future[Result]] = {
      controller //Call to inject mocks
      route(app, req)
    }
  }

  "Calling SelfAssessmentController.handleSelfAssessment" must {

    "return 303 when called with a GG user that needs to activate their SA enrolment." in new LocalSetup {
      val result: Future[Result] = controller.handleSelfAssessment()(FakeRequest())
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(
        "http://localhost:9555/enrolment-management-frontend/IR-SA/get-access-tax-scheme?continue=/personal-account"
      )
    }

    "return 303 when called with a GG user that is SA or has an SA enrollment in another account." in new LocalSetup {
      override def defaultFakeAuthJourney: FakeAuthJourney =
        defaultFakeAuthJourney.copy(WrongCredentialsSelfAssessmentUser(saUtr))

      val result: Future[Result] = controller.handleSelfAssessment()(FakeRequest())
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.SaWrongCredentialsController.landingPage().url)
    }

    "return 200 when called with a GG user that is has a UTR but no enrolment" in new LocalSetup {
      override def defaultFakeAuthJourney: FakeAuthJourney =
        defaultFakeAuthJourney.copy(NotEnrolledSelfAssessmentUser(saUtr))

      val result: Future[Result] = controller.handleSelfAssessment()(FakeRequest())
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.SelfAssessmentController.requestAccess.url)
    }
  }

  "Calling SelfAssessmentController.ivExemptLandingPage" must {
    "return 303 for a user who has logged in with GG linked and has a full SA enrollment" in new LocalSetup {
      override def defaultFakeAuthJourney: FakeAuthJourney =
        defaultFakeAuthJourney.copy(ActivatedOnlineFilerSelfAssessmentUser(saUtr))

      val result: Future[Result] = controller.ivExemptLandingPage(None)(FakeRequest())
      status(result) mustBe SEE_OTHER
    }

    "redirect to the SA activation page on the portal for a user logged in with GG linked to SA which is not yet activated" in new LocalSetup {
      override def defaultFakeAuthJourney: FakeAuthJourney =
        defaultFakeAuthJourney.copy(NotYetActivatedOnlineFilerSelfAssessmentUser(saUtr))

      val result: Future[Result] = controller.ivExemptLandingPage(None)(FakeRequest())
      val doc: Document          = Jsoup.parse(contentAsString(result))
      status(result) mustBe OK

      doc
        .getElementsByClass("govuk-heading-l")
        .toString
        .contains("Activate your Self Assessment registration") mustBe true
    }

    "redirect to 'Find out how to access your Self Assessment' page for a user who has a SAUtr but logged into the wrong GG account" in new LocalSetup {
      override def defaultFakeAuthJourney: FakeAuthJourney =
        defaultFakeAuthJourney.copy(WrongCredentialsSelfAssessmentUser(saUtr))

      val result: Future[Result] = controller.ivExemptLandingPage(None)(FakeRequest())
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.SaWrongCredentialsController.landingPage().url)
    }

    "render the page for a user who has a SAUtr but has never enrolled" in new LocalSetup {
      override def defaultFakeAuthJourney: FakeAuthJourney =
        defaultFakeAuthJourney.copy(NotEnrolledSelfAssessmentUser(saUtr))

      val result: Future[Result] = controller.ivExemptLandingPage(None)(FakeRequest())
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.SelfAssessmentController.requestAccess.url)
    }

    "redirect to 'We cannot confirm your identity' page for a user who has no SAUTR" in new LocalSetup {
      override def defaultFakeAuthJourney: FakeAuthJourney = defaultFakeAuthJourney.copy(NonFilerSelfAssessmentUser)

      val result: Future[Result] = controller.ivExemptLandingPage(None)(FakeRequest())
      status(result) mustBe OK
    }

    "return bad request when continueUrl is not relative" in new LocalSetup {
      override def defaultFakeAuthJourney: FakeAuthJourney = defaultFakeAuthJourney.copy(NonFilerSelfAssessmentUser)

      val result: Future[Result] =
        routeWrapper(buildFakeRequestWithAuth("GET", "/personal-account/sa-continue?continueUrl=http://example.com"))
          .getOrElse(throw new TestFailedException("Failed to route", 0))

      status(result) mustBe BAD_REQUEST
    }
  }

  "redirectToEnrolForSa" must {

    "redirect to the url returned by the SelfAssessmentService" in new LocalSetup {
      val redirectUrl = "/foo"

      when(mockSelfAssessmentService.getSaEnrolmentUrl(any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[String]](
          Future.successful(Right(Some(redirectUrl)))
        )
      )
      redirectLocation(controller.redirectToEnrolForSa(FakeRequest())) mustBe Some(redirectUrl)
    }

    "show an error page if no url is returned" in new LocalSetup {

      when(mockSelfAssessmentService.getSaEnrolmentUrl(any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[String]](
          Future.successful(Right(None))
        )
      )

      status(controller.redirectToEnrolForSa(FakeRequest())) mustBe INTERNAL_SERVER_ERROR
    }

    List(
      BAD_REQUEST,
      NOT_FOUND,
      REQUEST_TIMEOUT,
      UNPROCESSABLE_ENTITY,
      INTERNAL_SERVER_ERROR,
      BAD_GATEWAY,
      SERVICE_UNAVAILABLE
    ).foreach { error =>
      s"show an error page if the service returns a $error response" in new LocalSetup {

        when(mockSelfAssessmentService.getSaEnrolmentUrl(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Option[String]](
            Future.successful(Left(UpstreamErrorResponse("", error)))
          )
        )

        status(controller.redirectToEnrolForSa(FakeRequest())) mustBe INTERNAL_SERVER_ERROR
      }
    }
  }
}

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

package controllers.auth

import cats.data.EitherT
import config.ConfigDecorator
import connectors.PertaxConnector
import controllers.auth.requests.UserRequest
import controllers.bindable.Origin
import models.{ErrorView, PertaxResponse, UserAnswers, WrongCredentialsSelfAssessmentUser}
import org.mockito.ArgumentMatchers.any
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.http.Status.{INTERNAL_SERVER_ERROR, SEE_OTHER, UNAUTHORIZED}
import play.api.i18n.{Lang, Messages, MessagesImpl}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout}
import play.twirl.api.Html
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.partials.HtmlPartial
import views.html.{InternalServerErrorView, MainView}

import scala.concurrent.Future

class PertaxAuthActionSpec extends BaseSpec with IntegrationPatience {

  override implicit lazy val app: Application = GuiceApplicationBuilder().build()

  private val configDecorator: ConfigDecorator         = mock[ConfigDecorator]
  private val mockPertaxConnector                      = mock[PertaxConnector]
  val internalServerErrorView: InternalServerErrorView = app.injector.instanceOf[InternalServerErrorView]
  val mainView: MainView                               = app.injector.instanceOf[MainView]
  private val cc                                       = app.injector.instanceOf[ControllerComponents]
  implicit lazy val messages: Messages                 = MessagesImpl(Lang("en"), messagesApi).messages

  val pertaxAuthAction =
    new PertaxAuthAction(
      mockPertaxConnector,
      internalServerErrorView,
      mainView,
      cc
    )(configDecorator)

  when(configDecorator.pertaxUrl).thenReturn("PERTAX_URL")
  when(configDecorator.defaultOrigin).thenReturn(Origin("PERTAX"))
  when(configDecorator.serviceIdentityCheckFailedUrl).thenReturn("/personal-account/identity-check-failed")

  private val fakeRequest             = FakeRequest("GET", "/personal-account")
  val expectedRequest: UserRequest[_] =
    UserRequest(
      Fixtures.fakeNino,
      None,
      WrongCredentialsSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
      Credentials("", "GovernmentGateway"),
      ConfidenceLevel.L50,
      None,
      Set(),
      None,
      None,
      fakeRequest,
      UserAnswers.empty
    )

  "Pertax auth action" when {
    "the pertax API returns an ACCESS_GRANTED response" must {
      "load the request" in {
        when(mockPertaxConnector.pertaxPostAuthorise(any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PertaxResponse](
              Future.successful(Right(PertaxResponse("ACCESS_GRANTED", "", None, None)))
            )
          )

        val result = pertaxAuthAction.filter(expectedRequest).futureValue
        result mustBe None
      }
    }

    "the pertax API response returns a NO_HMRC_PT_ENROLMENT response" must {
      "redirect to the returned location" in {
        when(mockPertaxConnector.pertaxPostAuthorise(any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PertaxResponse](
              Future.successful(Right(PertaxResponse("NO_HMRC_PT_ENROLMENT", "", None, Some("redirectLocation"))))
            )
          )

        val result = pertaxAuthAction.filter(expectedRequest).futureValue

        result must not be empty
        result.get.header.headers
          .get("Location") mustBe Some("redirectLocation?redirectUrl=%2Fpersonal-account")
      }
    }

    "the pertax API response returns a CONFIDENCE_LEVEL_UPLIFT_REQUIRED response" must {
      "redirect to uplift Journey" in {
        when(mockPertaxConnector.pertaxPostAuthorise(any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PertaxResponse](
              Future.successful(
                Right(PertaxResponse("CONFIDENCE_LEVEL_UPLIFT_REQUIRED", "", None, Some("redirectLocation")))
              )
            )
          )

        val result = pertaxAuthAction.filter(expectedRequest).futureValue

        result must not be empty
        result.get.header.headers
          .get("Location") mustBe Some(
          "redirectLocation?origin=PERTAX&confidenceLevel=200&completionURL=%2Fpersonal-account&failureURL=%2Fpersonal-account%2Fidentity-check-failed%3FcontinueUrl%3D%2Fpersonal-account"
        )
      }
    }

    "the pertax API response returns a CREDENTIAL_STRENGTH_UPLIFT_REQUIRED response" must {
      "return an InternalServerError" in {
        when(mockPertaxConnector.pertaxPostAuthorise(any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PertaxResponse](
              Future.successful(
                Right(PertaxResponse("CREDENTIAL_STRENGTH_UPLIFT_REQUIRED", "", None, Some("Something")))
              )
            )
          )

        val result = pertaxAuthAction.filter(expectedRequest).futureValue

        result.get.header.status mustBe INTERNAL_SERVER_ERROR
        contentAsString(Future.successful(result.get)) must include(
          messages("global.error.InternalServerError500.pta.title")
        )

      }
    }

    "the pertax API response returns an error view" must {
      "show the corresponding view" in {
        when(mockPertaxConnector.pertaxPostAuthorise(any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PertaxResponse](
              Future.successful(
                Right(
                  PertaxResponse(
                    "INVALID_AFFINITY",
                    "The user is neither an individual or an organisation",
                    Some(ErrorView("/path/for/partial", UNAUTHORIZED)),
                    None
                  )
                )
              )
            )
          )

        when(mockPertaxConnector.loadPartial(any())(any(), any()))
          .thenReturn(Future.successful(HtmlPartial.Success(None, Html("Should be in the resulting view"))))

        val result = pertaxAuthAction.filter(expectedRequest).futureValue

        result                                         must not be empty
        result.get.header.status mustBe UNAUTHORIZED
        contentAsString(Future.successful(result.get)) must include(messages("Should be in the resulting view"))
      }
      "return internal server error" in {
        when(mockPertaxConnector.pertaxPostAuthorise(any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PertaxResponse](
              Future.successful(
                Right(
                  PertaxResponse(
                    "INVALID_AFFINITY",
                    "The user is neither an individual or an organisation",
                    Some(ErrorView("", UNAUTHORIZED)),
                    None
                  )
                )
              )
            )
          )

        when(mockPertaxConnector.loadPartial(any())(any(), any()))
          .thenReturn(Future.successful(HtmlPartial.Failure(None, "")))

        val result = pertaxAuthAction.filter(expectedRequest).futureValue

        result                                         must not be empty
        result.get.header.status mustBe INTERNAL_SERVER_ERROR
        contentAsString(Future.successful(result.get)) must include(
          messages("global.error.InternalServerError500.pta.title")
        )
      }
    }

    "the pertax API response returns an unauthorised response" must {
      "redirect to sign in Journey" in {
        when(mockPertaxConnector.pertaxPostAuthorise(any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PertaxResponse](
              Future.successful(Left(UpstreamErrorResponse("", UNAUTHORIZED)))
            )
          )

        val result = pertaxAuthAction.filter(expectedRequest).futureValue

        result must not be empty
        result.get.header.status mustBe SEE_OTHER
        result.get.header.headers
          .get("Location") mustBe Some(
          "?continue_url=&origin=pertax-frontend&accountType=individual"
        )
      }
    }

    "the pertax API response returns an unexpected response" must {
      "throw an internal server error" in {
        when(mockPertaxConnector.pertaxPostAuthorise(any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PertaxResponse](
              Future.successful(Left(UpstreamErrorResponse("", INTERNAL_SERVER_ERROR)))
            )
          )

        val result = pertaxAuthAction.filter(expectedRequest).futureValue

        result                                         must not be empty
        result.get.header.status mustBe INTERNAL_SERVER_ERROR
        contentAsString(Future.successful(result.get)) must include(
          messages("global.error.InternalServerError500.pta.title")
        )
      }
    }
  }
}

/*
 * Copyright 2022 HM Revenue & Customs
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
import controllers.auth.requests.AuthenticatedRequest
import models.{ErrorView, PertaxResponse}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.HttpEntity
import play.api.http.Status.{IM_A_TEAPOT, INTERNAL_SERVER_ERROR, SEE_OTHER, UNAUTHORIZED}
import play.api.i18n.{Lang, MessagesApi, MessagesImpl}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{ControllerComponents, ResponseHeader, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout}
import play.twirl.api.Html
import testUtils.BaseSpec
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.partials.HtmlPartial
import views.html.{InternalServerErrorView, UnauthenticatedMainView}

import scala.concurrent.Future
import scala.util.Random

class PertaxAuthActionSpec extends BaseSpec {

  override implicit lazy val app: Application = GuiceApplicationBuilder().build()

  private val mockPertaxConnector = mock[PertaxConnector]
  val internalServerErrorView     = app.injector.instanceOf[InternalServerErrorView]
  val mainTemplateView            = app.injector.instanceOf[UnauthenticatedMainView]
  private val cc                  = app.injector.instanceOf[ControllerComponents]
  val messagesApi                 = app.injector.instanceOf[MessagesApi]

  private val testAppConfig: ConfigDecorator = mock[ConfigDecorator]
  when(testAppConfig.pertaxUrl).thenReturn("PERTAX_URL")

  val pertaxAuthAction =
    new PertaxAuthAction(mockPertaxConnector, internalServerErrorView, mainTemplateView, cc)(messagesApi, testAppConfig)

  val nino = new Generator(new Random()).nextNino

  implicit lazy val messages: MessagesImpl = MessagesImpl(Lang("en"), messagesApi)

  private val testRequest     = FakeRequest("GET", "/")
  private val fakeCredentials = Credentials("foo", "bar")
  val expectedRequest         = AuthenticatedRequest(
    Some(nino),
    fakeCredentials,
    ConfidenceLevel.L200,
    None,
    None,
    None,
    Set.empty,
    testRequest,
    None
  )

  "Pertax auth action" when {
    "the pertax API returns an ACCESS_GRANTED response" must {
      "load the request" in {
        when(mockPertaxConnector.pertaxAuthorise(any())(any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PertaxResponse](
              Future.successful(Right(PertaxResponse("ACCESS_GRANTED", "", None, None)))
            )
          )

        val result = pertaxAuthAction.refine(expectedRequest).futureValue
        result must be(Right(expectedRequest))
      }
    }

    "the pertax API response returns a NO_HMRC_PT_ENROLMENT response" must {
      "redirect to the returned location" in {
        when(mockPertaxConnector.pertaxAuthorise(any())(any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PertaxResponse](
              Future.successful(Right(PertaxResponse("NO_HMRC_PT_ENROLMENT", "", None, Some("redirectLocation"))))
            )
          )

        val result = pertaxAuthAction.refine(expectedRequest).futureValue

        result mustBe a[Left[_, _]]
        val resultValue = result.swap.getOrElse(Result(ResponseHeader(IM_A_TEAPOT, Map("" -> "")), HttpEntity.NoEntity))
        resultValue.header.status mustBe SEE_OTHER
        resultValue.header.headers mustBe Map(
          "Location" -> "redirectLocation/?redirectUrl=%2F"
        )
      }
    }

    "the pertax API response returns an error view" must {
      "show the corresponding view" in {
        when(mockPertaxConnector.pertaxAuthorise(any())(any()))
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

        val result = pertaxAuthAction.refine(expectedRequest).futureValue

        result mustBe a[Left[_, _]]
        val resultValue = result.swap.getOrElse(Result(ResponseHeader(IM_A_TEAPOT, Map("" -> "")), HttpEntity.NoEntity))
        resultValue.header.status mustBe UNAUTHORIZED
        contentAsString(Future.successful(resultValue)) must include(messages("Should be in the resulting view"))
      }
    }

    "the pertax API response returns an unexpected response" must {
      "throw an internal server error" in {
        when(mockPertaxConnector.pertaxAuthorise(any())(any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PertaxResponse](
              Future.successful(Left(UpstreamErrorResponse("", INTERNAL_SERVER_ERROR)))
            )
          )

        val result = pertaxAuthAction.refine(expectedRequest).futureValue

        result mustBe a[Left[_, _]]
        val resultValue = result.swap.getOrElse(Result(ResponseHeader(IM_A_TEAPOT, Map("" -> "")), HttpEntity.NoEntity))
        resultValue.header.status mustBe INTERNAL_SERVER_ERROR
        contentAsString(Future.successful(resultValue)) must include(
          messages("global.error.InternalServerError500.pta.title")
        )
      }
    }
  }
}

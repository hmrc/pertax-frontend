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

package services

import com.kenshoo.play.metrics.Metrics
import controllers.auth.requests.UserRequest
import models.{ActivatePaperlessActivatedResponse, ActivatePaperlessNotAllowedResponse, ActivatePaperlessRequiresUserActionResponse, NonFilerSelfAssessmentUser, UserName}
import org.joda.time.DateTime
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import services.http.SimpleHttp
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.http.HttpResponse
import util.{BaseSpec, Fixtures}

import scala.concurrent.Future

class PreferencesFrontendServiceSpec extends BaseSpec with GuiceOneAppPerSuite with MockitoSugar {

  val mockSimpleHttp = mock[SimpleHttp]
  val mockMetrics = mock[Metrics]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[SimpleHttp].toInstance(mockSimpleHttp))
    .overrides(bind[Metrics].toInstance(mockMetrics))
    .build()

  //TODO: Find a way to mock metrics in a testable way
  "PreferencesFrontend" should {

    "return ActivatePaperlessActivatedResponse if it is successful, and user is Government GateWay" in {

      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        NonFilerSelfAssessmentUser,
        "GovernmentGateway",
        ConfidenceLevel.L200,
        None,
        None,
        None,
        None,
        FakeRequest()
      )

      implicit val service = app.injector.instanceOf[PreferencesFrontendService]

      when(mockSimpleHttp.put[AnyContent, HttpResponse](any(), any())(any(), any())(any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(Json.obj()))))

      lazy val r = service.getPaperlessPreference()

      await(r) shouldBe ActivatePaperlessActivatedResponse

      /*      verify(mockMetrics., times(1)).startTimer(metricId)
      verify(mockMetrics, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()*/
    }

    "return ActivatePaperlessNotAllowedResponse if user is not Government Gateway" in {
      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        NonFilerSelfAssessmentUser,
        "Verify",
        ConfidenceLevel.L500,
        None,
        None,
        None,
        None,
        FakeRequest()
      )

      implicit val service = app.injector.instanceOf[PreferencesFrontendService]

      when(mockSimpleHttp.put[AnyContent, HttpResponse](any(), any())(any(), any())(any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(Json.obj()))))

      lazy val r = service.getPaperlessPreference()

      await(r) shouldBe ActivatePaperlessNotAllowedResponse

      /*      verify(met, times(0)).startTimer(metricId)
      verify(met, times(0)).incrementSuccessCounter(metricId)
      verify(timer, times(0)).stop()*/
    }

    "return ActivatePaperlessNotAllowedResponse if any upstream exceptions are thrown" in {
      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        NonFilerSelfAssessmentUser,
        "Verify",
        ConfidenceLevel.L500,
        None,
        None,
        None,
        None,
        FakeRequest()
      )

      implicit val service = app.injector.instanceOf[PreferencesFrontendService]
      when(mockSimpleHttp.put[AnyContent, HttpResponse](any(), any())(any(), any())(any(), any()))
        .thenReturn(Future.successful(HttpResponse(SEE_OTHER)))
      lazy val r = service.getPaperlessPreference()

      await(r) shouldBe ActivatePaperlessNotAllowedResponse

      /*      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()*/
    }

    "return ActivatePaperlessNotAllowedResponse if BadRequestException is thrown" in {
      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        NonFilerSelfAssessmentUser,
        "Verify",
        ConfidenceLevel.L500,
        None,
        None,
        None,
        None,
        FakeRequest()
      )

      implicit val service = app.injector.instanceOf[PreferencesFrontendService]
      when(mockSimpleHttp.put[AnyContent, HttpResponse](any(), any())(any(), any())(any(), any()))
        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, Some(Json.obj()))))
      lazy val r = service.getPaperlessPreference()

      await(r) shouldBe ActivatePaperlessNotAllowedResponse

      /*      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()*/
    }

    "return ActivatePaperlessRequiresUserActionResponse if Precondition failed with 412 response" in {
      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        NonFilerSelfAssessmentUser,
        "Verify",
        ConfidenceLevel.L500,
        None,
        None,
        None,
        None,
        FakeRequest()
      )

      implicit val service = app.injector.instanceOf[PreferencesFrontendService]
      when(mockSimpleHttp.put[AnyContent, HttpResponse](any(), any())(any(), any())(any(), any()))
        .thenReturn(Future.successful(
          HttpResponse(PRECONDITION_FAILED, Some(Json.obj("redirectUserTo" -> "http://www.testurl.com")))))
      lazy val r = service.getPaperlessPreference()

      await(r) shouldBe ActivatePaperlessRequiresUserActionResponse("http://www.testurl.com")

      /*      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()*/
    }

    "return ActivatePaperlessNotAllowedResponse when called and service is down" in {
      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        NonFilerSelfAssessmentUser,
        "Verify",
        ConfidenceLevel.L500,
        None,
        None,
        None,
        None,
        FakeRequest()
      )

      implicit val service = app.injector.instanceOf[PreferencesFrontendService]
      when(mockSimpleHttp.put[AnyContent, HttpResponse](any(), any())(any(), any())(any(), any()))
        .thenReturn(Future.failed(new RuntimeException("Any")))
      lazy val r = service.getPaperlessPreference()

      await(r) shouldBe ActivatePaperlessNotAllowedResponse
      /*      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()*/
    }
  }
}

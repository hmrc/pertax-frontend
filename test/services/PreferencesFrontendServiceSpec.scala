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

import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import config.ConfigDecorator
import models.{PertaxUser, UserDetails}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status._
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.test.FakeRequest
import services.http.FakeSimpleHttp
import uk.gov.hmrc.play.http._
import util.BaseSpec
import util.Fixtures._
import uk.gov.hmrc.http.HttpResponse

class PreferencesFrontendServiceSpec extends BaseSpec {

  trait SpecSetup {
    def httpResponse: HttpResponse
    def simulatePreferencesFrontendServiceIsDown: Boolean

    val anException = new RuntimeException("Any")

    implicit val request = FakeRequest()

    lazy val (service, met, timer) = {

      val fakeSimpleHttp = {
        if(simulatePreferencesFrontendServiceIsDown) new FakeSimpleHttp(Right(anException))
        else new FakeSimpleHttp(Left(httpResponse))
      }

      val timer = MockitoSugar.mock[Timer.Context]
      val preferencesFrontendService: PreferencesFrontendService = new PreferencesFrontendService(fakeSimpleHttp, injected[MessagesApi], MockitoSugar.mock[Metrics], injected[ConfigDecorator]) {
        override val metricsOperator: MetricsOperator = MockitoSugar.mock[MetricsOperator]
        when(metricsOperator.startTimer(any())) thenReturn timer
      }

      (preferencesFrontendService, preferencesFrontendService.metricsOperator, timer)
    }
  }

  "PreferencesFrontend" should {

    trait LocalSetup extends SpecSetup {
      val metricId = "get-activate-paperless"
      lazy val r = service.getPaperlessPreference(PertaxUser(buildFakeAuthContext(), UserDetails(UserDetails.GovernmentGatewayAuthProvider), None, true))
      lazy val httpResponse = HttpResponse(OK, Some(Json.obj()))
      lazy val simulatePreferencesFrontendServiceIsDown = false
    }

    "return ActivatePaperlessActivatedResponse if it is successful, and user is Government GateWay"  in new LocalSetup {
      await(r) shouldBe ActivatePaperlessActivatedResponse

      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return ActivatePaperlessNotAllowedResponse if user is not Government Gateway" in new LocalSetup {
      override lazy val r = service.getPaperlessPreference(PertaxUser(buildFakeAuthContext(), UserDetails(UserDetails.VerifyAuthProvider), None, true))

      await(r) shouldBe ActivatePaperlessNotAllowedResponse

      verify(met, times(0)).startTimer(metricId)
      verify(met, times(0)).incrementSuccessCounter(metricId)
      verify(timer, times(0)).stop()
    }

    "return ActivatePaperlessNotAllowedResponse if any upstream exceptions are thrown" in new LocalSetup {
      override lazy val httpResponse = HttpResponse(SEE_OTHER)

      await(r) shouldBe ActivatePaperlessNotAllowedResponse

      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return ActivatePaperlessNotAllowedResponse if BadRequestException is thrown" in new LocalSetup {
      override lazy val httpResponse = HttpResponse(BAD_REQUEST, Some(Json.obj()))

      await(r) shouldBe ActivatePaperlessNotAllowedResponse

      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return ActivatePaperlessRequiresUserActionResponse if Precondition failed with 412 response" in new LocalSetup {
      override lazy val httpResponse = HttpResponse(PRECONDITION_FAILED, Some(Json.obj("redirectUserTo" -> "http://www.testurl.com")))

      await(r) shouldBe ActivatePaperlessRequiresUserActionResponse("http://www.testurl.com")

      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return ActivatePaperlessNotAllowedResponse when called and service is down" in new LocalSetup {
      override lazy val simulatePreferencesFrontendServiceIsDown = true
      override lazy val httpResponse = ???

      await(r) shouldBe ActivatePaperlessNotAllowedResponse
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }
  }
}

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
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.{Configuration, Environment}
import play.api.Mode.Mode
import play.twirl.api.Html
import services.http.WsAllMethods
import services.partials.FormPartialService
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.play.partials.HtmlPartial
import util.BaseSpec
import util.Fixtures._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FormPartialServiceSpec extends BaseSpec {

  trait LocalSetup {

    val timer = MockitoSugar.mock[Timer.Context]
    val formPartialService: FormPartialService = new FormPartialService(
      injected[Environment],
      injected[Configuration],
      MockitoSugar.mock[WsAllMethods],
      MockitoSugar.mock[Metrics],
      MockitoSugar.mock[ConfigDecorator],
      injected[ApplicationCrypto]
    ) {
      override val metricsOperator: MetricsOperator = MockitoSugar.mock[MetricsOperator]
      when(metricsOperator.startTimer(any())) thenReturn timer
    }
  }

  "Calling FormPartialServiceSpec" should {

    "return form list for National insurance" in new LocalSetup {

      when(formPartialService.http.GET[HtmlPartial](any())(any(), any(), any())) thenReturn
        Future.successful[HtmlPartial](HtmlPartial.Success(Some("Title"), Html("<title/>")))

      formPartialService.getNationalInsurancePartial(buildFakeRequestWithAuth("GET")).map(p => p shouldBe "<title/>")
      verify(formPartialService.http, times(1)).GET[Html](any())(any(), any(), any())
    }

    "return form list for Self-assessment" in new LocalSetup {

      when(formPartialService.http.GET[HtmlPartial](any())(any(), any(), any())) thenReturn
        Future.successful[HtmlPartial](HtmlPartial.Success(Some("Title"), Html("<title/>")))

      formPartialService.getSelfAssessmentPartial(buildFakeRequestWithAuth("GET")).map(p => p shouldBe "<title/>")
      verify(formPartialService.http, times(1)).GET[Html](any())(any(), any(), any())
    }

  }

}

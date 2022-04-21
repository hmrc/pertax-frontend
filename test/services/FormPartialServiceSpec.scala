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

package services

import config.ConfigDecorator
import metrics.Metrics
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.twirl.api.Html
import services.partials.FormPartialService
import uk.gov.hmrc.play.partials.{HeaderCarrierForPartialsConverter, HtmlPartial}
import util.{BaseSpec, EnhancedPartialRetriever}
import util.Fixtures._

import scala.concurrent.Future

class FormPartialServiceSpec extends BaseSpec {
  val mockEnhancedPartialRetriever = mock[EnhancedPartialRetriever]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockEnhancedPartialRetriever)
  }

  trait LocalSetup {
    val formPartialService: FormPartialService = new FormPartialService(
      mock[ConfigDecorator],
      mockEnhancedPartialRetriever
    )
  }

  "Calling FormPartialServiceSpec" must {

    "return form list for National insurance" in new LocalSetup {

      when(mockEnhancedPartialRetriever.loadPartial(any())(any(), any())) thenReturn
        Future.successful[HtmlPartial](HtmlPartial.Success(Some("Title"), Html("<title/>")))

      formPartialService.getNationalInsurancePartial(buildFakeRequestWithAuth("GET")).map(p => p mustBe "<title/>")
      verify(mockEnhancedPartialRetriever, times(1)).loadPartial(any())(any(), any())
    }

    "return form list for Self-assessment" in new LocalSetup {

      when(mockEnhancedPartialRetriever.loadPartial(any())(any(), any())) thenReturn
        Future.successful[HtmlPartial](HtmlPartial.Success(Some("Title"), Html("<title/>")))

      formPartialService.getSelfAssessmentPartial(buildFakeRequestWithAuth("GET")).map(p => p mustBe "<title/>")
      verify(mockEnhancedPartialRetriever, times(1)).loadPartial(any())(any(), any())
    }

  }

}

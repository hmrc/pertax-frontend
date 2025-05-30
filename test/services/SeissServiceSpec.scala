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

package services

import cats.data.EitherT
import cats.implicits._
import config.ConfigDecorator
import connectors.SeissConnector
import models.{ActivatedOnlineFilerSelfAssessmentUser, NonFilerSelfAssessmentUser, SeissModel}
import org.mockito.ArgumentMatchers.any
import testUtils.BaseSpec
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.Future
import org.mockito.Mockito.{times, verify, when}

class SeissServiceSpec extends BaseSpec {

  val mockConfig: ConfigDecorator = mock[ConfigDecorator]

  val mockSeissConnector: SeissConnector = mock[SeissConnector]
  val sut                                = new SeissService(mockSeissConnector, mockConfig)

  when(mockConfig.isSeissTileEnabled).thenReturn(true)

  "Calling hasClaims" must {
    "return true" when {
      "The user has 1 or more claims" in {
        when(mockSeissConnector.getClaims(any())(any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](List(SeissModel("utr"))))

        val result = sut.hasClaims(ActivatedOnlineFilerSelfAssessmentUser(SaUtr("utr"))).futureValue

        result mustBe true
      }
    }

    "return false" when {
      "The user has no claims" in {
        when(mockSeissConnector.getClaims(any())(any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](List.empty: List[SeissModel]))

        val result = sut.hasClaims(ActivatedOnlineFilerSelfAssessmentUser(SaUtr("utr"))).futureValue

        result mustBe false
      }
      "there is an error" in {
        when(mockSeissConnector.getClaims(any())(any()))
          .thenReturn(EitherT.leftT[Future, List[SeissModel]](UpstreamErrorResponse("error", 500, 500)))

        val result = sut.hasClaims(ActivatedOnlineFilerSelfAssessmentUser(SaUtr("utr"))).futureValue

        result mustBe false
      }
      "The user is not a sa filer" in {
        when(mockSeissConnector.getClaims(any())(any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](List.empty: List[SeissModel]))

        val result = sut.hasClaims(NonFilerSelfAssessmentUser).futureValue

        result mustBe false
      }
      "isSeissTileEnabled is false" in {
        when(mockConfig.isSeissTileEnabled).thenReturn(false)

        val result = sut.hasClaims(ActivatedOnlineFilerSelfAssessmentUser(SaUtr("utr"))).futureValue

        result mustBe false
      }
    }
  }
}

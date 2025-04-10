/*
 * Copyright 2025 HM Revenue & Customs
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
import connectors.FandFConnector
import org.mockito.ArgumentMatchers.any
import org.scalatest.concurrent.IntegrationPatience
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.mvc.AnyContentAsEmpty
import play.api.test.Helpers.GET
import play.api.test.{FakeRequest, Injecting}
import testUtils.BaseSpec
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.Future

class FandfServiceSpec extends BaseSpec with Injecting with IntegrationPatience {

  val mockConnector: FandFConnector                         = mock[FandFConnector]
  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/")
  val sut: FandfService                                     = new FandfService(mockConnector)

  val trustedHelper: TrustedHelper =
    TrustedHelper("principal Name", "attorneyName", "returnLink", Some(generatedNino.nino))

  "FandfService" when {
    "calling getTrustedHelper" must {
      "return Some(trustedHelper)" in {
        when(mockConnector.getTrustedHelper()(any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Option[TrustedHelper]](Future.successful(Right(Some(trustedHelper))))
        )

        val result =
          sut
            .getTrustedHelper()
            .futureValue

        result mustBe Some(trustedHelper)
      }

      "return None when call fails" in {
        when(mockConnector.getTrustedHelper()(any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Option[TrustedHelper]](
            Future.successful(Left(UpstreamErrorResponse("failure", INTERNAL_SERVER_ERROR)))
          )
        )

        val result =
          sut
            .getTrustedHelper()
            .futureValue

        result mustBe None
      }

    }
  }
}

/*
 * Copyright 2024 HM Revenue & Customs
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
import org.mockito.Mockito.{reset, when}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.BaseSpec
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.Future

class FandFServiceSpec extends BaseSpec {

  private val mockFandFConnector: FandFConnector = mock[FandFConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockFandFConnector)
    ()
  }

  implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  val sut = new FandFService(mockFandFConnector)

  "isAnyFandFRelationships" must {
    "return true" when {
      "there is some relationships" in {
        val stubResponse = Json.obj(
          "key" -> Json.arr(
            Json.obj(
              "principal"     -> Json.obj("nino" -> generatedNino, "firstName" -> "firstName", "lastName" -> "lastName"),
              "attorney"      -> Json.obj("nino" -> generatedNino, "firstName" -> "firstName", "lastName" -> "lastName"),
              "serviceScopes" -> Json.arr(
                Json.obj("scope" -> "scope", "status" -> "status")
              )
            )
          )
        )

        when(mockFandFConnector.getFandFAccountDetails(any())(any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](stubResponse)
        )

        val result = sut.isAnyFandFRelationships(generatedNino).futureValue

        result mustBe true
      }
    }

    "return false" when {
      "there is no relationship" in {
        val stubResponse = Json.obj(
          "key" -> Json.arr()
        )

        when(mockFandFConnector.getFandFAccountDetails(any())(any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](stubResponse)
        )

        val result = sut.isAnyFandFRelationships(generatedNino).futureValue

        result mustBe false
      }

      "there is a server error" in {
        when(mockFandFConnector.getFandFAccountDetails(any())(any(), any())).thenReturn(
          EitherT.leftT[Future, JsValue](UpstreamErrorResponse("error", 500))
        )

        val result = sut.isAnyFandFRelationships(generatedNino).futureValue

        result mustBe false
      }
    }
  }
}

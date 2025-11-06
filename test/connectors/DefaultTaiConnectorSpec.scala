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

package connectors

import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.WireMockHelper
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.UpstreamErrorResponse
import play.api.libs.json.Json

class DefaultTaiConnectorSpec extends ConnectorSpec with WireMockHelper with IntegrationPatience {

  override implicit lazy val app: Application =
    app(
      Map(
        "microservice.services.tai.port" -> server.port(),
        "tai.timeoutInMilliseconds"      -> 1000
      )
    )

  def connector: DefaultTaiConnector =
    app.injector.instanceOf[DefaultTaiConnector]

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  private val nino = Nino("AA123456A")
  private val year = 2024
  private val url  = s"/tai/${nino.nino}/tax-account/$year/tax-components"

  "DefaultTaiConnector.taxComponents" must {

    "return Right(json)" in {
      val jsonString = """{"x": "some Json"}"""
      stubGet(url, OK, Some(jsonString))

      val result =
        connector.taxComponents(nino, year).value.futureValue

      result mustBe Right(Json.parse(jsonString))
    }

    "return Left(UpstreamErrorResponse) when downstream returns 5xx" in {
      stubGet(url, INTERNAL_SERVER_ERROR, None)

      val result =
        connector.taxComponents(nino, year).value.futureValue

      result mustBe a[Left[UpstreamErrorResponse, _]]
    }
  }
}

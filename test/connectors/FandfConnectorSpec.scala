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

package connectors

import org.scalatestplus.mockito.MockitoSugar
import play.api
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import testUtils.WireMockHelper
import uk.gov.hmrc.domain.{Generator, Nino}
import play.api.libs.json.Json
import scala.util.Random

class FandfConnectorSpec extends ConnectorSpec with WireMockHelper with MockitoSugar {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(Map("microservice.services.fandf.port" -> server.port()))
    .build()

  private val nino: Nino             = Nino(new Generator(new Random()).nextNino.nino)
  private val detailsRelationshipUrl = s"/fandf/$nino"

  private def connector: FandFConnector = app.injector.instanceOf[FandFConnector]

  "getFandFAccountDetails" must {
    "return the list of relationships for a given state" in {
      val stubResponse = Json.obj(
        "key" -> Json.obj(
          "principal"     -> "principal",
          "attorney"      -> "attorney",
          "serviceScopes" -> Json.arr(
            Json.obj("scope" -> "scope", "status" -> "status")
          )
        )
      )
      stubGet(detailsRelationshipUrl, OK, Some(Json.toJson(stubResponse).toString))

      val result = connector.getFandFAccountDetails(nino).value.futureValue

      result mustBe Right(stubResponse)
    }
  }
}

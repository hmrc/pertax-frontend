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

package connectors

import cats.data.EitherT
import cats.implicits._
import models.{Address, AgentClientStatus}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.Application
import play.api.inject.bind
import play.api.libs.json.JsValue
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import repositories.SessionCacheRepository
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongo.cache.DataKey
import scala.concurrent.Future

import java.time.LocalDate
import scala.concurrent.ExecutionContext

class CachingCitizenDetailsConnectorSpec extends ConnectorSpec with BaseSpec with WireMockHelper {

  val mockCitizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]
  val mockSessionCacheRepository: SessionCacheRepository   = mock[SessionCacheRepository]

  override implicit val hc: HeaderCarrier         = HeaderCarrier()
  override implicit lazy val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  override implicit lazy val app: Application = app(
    Map("microservice.services.citizen-details.port" -> server.port()),
    bind(classOf[CitizenDetailsConnector])
      .qualifiedWith("default")
      .toInstance(mockCitizenDetailsConnector),
    bind[SessionCacheRepository].toInstance(mockSessionCacheRepository)
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockCitizenDetailsConnector)
    reset(mockSessionCacheRepository)
  }

  def connector: CachingCitizenDetailsConnector = inject[CachingCitizenDetailsConnector]

  def url(nino: String): String = s"/citizen-details/$nino/designatory-details?cached=true"

  implicit val userRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  val address: Address = Address(
    line1 = Some("1 Fake Street"),
    line2 = Some("Fake Town"),
    line3 = Some("Fake City"),
    line4 = Some("Fake Region"),
    line5 = None,
    postcode = None,
    country = Some("AA1 1AA"),
    startDate = Some(LocalDate.of(2015, 3, 15)),
    endDate = None,
    `type` = Some("Residential"),
    isRls = false
  )

  "Calling CachingCitizenDetailsConnector.updateAddress" must {
    "clear the cache" when {
      "updating an address" in {

        when(
          mockSessionCacheRepository.deleteFromSessionEitherT[AgentClientStatus, JsValue](DataKey(any[String]()))(any())
        )
          .thenReturn(EitherT.rightT[Future, AgentClientStatus](()))

        when(mockCitizenDetailsConnector.updateAddress(any(), any(), any())(any(), any(), any()))
          .thenReturn(EitherT.rightT[Future, Boolean](true))

        val _ = connector.updateAddress(generatedNino, "0", address).value.futureValue

        verify(mockSessionCacheRepository, times(1))
          .deleteFromSessionEitherT[AgentClientStatus, JsValue](DataKey(any[String]()))(any())

        verify(mockCitizenDetailsConnector, times(1)).updateAddress(any(), any(), any())(any(), any(), any())
      }

    }
  }
}

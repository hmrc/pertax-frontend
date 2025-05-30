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

import models._
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, notFound, ok, serverError, urlEqualTo}
import org.mockito.{ArgumentCaptor, ArgumentMatchers, Mockito}
import org.scalatest.RecoverMethods
import org.scalatest.concurrent.IntegrationPatience
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsResultException
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.{Application, Logger}
import play.twirl.api.Html
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.partials.{HeaderCarrierForPartialsConverter, HtmlPartial}
import org.mockito.Mockito.{reset, times}

class EnhancedPartialRetrieverSpec extends BaseSpec with WireMockHelper with IntegrationPatience with RecoverMethods {

  private lazy implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("", "")

  server.start()
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "metrics.enabled"        -> false,
      "auditing.enabled"       -> false,
      "auditing.traceRequests" -> false
    )
    .build()

  private def httpClientV2: HttpClientV2        = app.injector.instanceOf[HttpClientV2]
  private def headerCarrierForPartialsConverter = app.injector.instanceOf[HeaderCarrierForPartialsConverter]
  private val mockLogger: Logger                = mock[Logger]
  private def sut: EnhancedPartialRetriever     =
    new EnhancedPartialRetriever(httpClientV2, headerCarrierForPartialsConverter) {
      override protected val logger: Logger = mockLogger
    }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockLogger)
  }

  "Calling EnhancedPartialRetriever.loadPartial" must {

    "return a successful partial and log the right metrics" in {

      val returnPartial: HtmlPartial = HtmlPartial.Success.apply(None, Html("my body content"))
      val url                        = s"http://localhost:${server.port()}/"
      server.stubFor(
        get(urlEqualTo("/")).willReturn(ok("my body content"))
      )
      sut.loadPartial(url).futureValue mustBe returnPartial
    }

    "return a failed partial and log the right metrics" in {

      val returnPartial: HtmlPartial = HtmlPartial.Failure(Some(404), "Not Found")
      val url                        = s"http://localhost:${server.port()}/"
      server.stubFor(
        get(urlEqualTo("/")).willReturn(notFound.withBody("Not Found"))
      )
      sut.loadPartial(url).futureValue mustBe returnPartial
    }

    "when the call to the service fails log the right metrics" in {

      val returnPartial: HtmlPartial = HtmlPartial.Failure(Some(500), "Error")
      val url                        = s"http://localhost:${server.port()}/"
      server.stubFor(
        get(urlEqualTo("/")).willReturn(serverError.withBody("Error"))
      )
      sut.loadPartial(url).futureValue mustBe returnPartial
    }

    "when the call to the service times out return failed Html partial" in {
      val returnPartial: HtmlPartial = HtmlPartial.Failure(Some(504), "")
      val url                        = s"http://localhost:${server.port()}/"
      server.stubFor(
        get(urlEqualTo("/")).willReturn(aResponse().withFixedDelay(100))
      )
      sut.loadPartial(url, 1).futureValue mustBe returnPartial
    }
  }

  "Calling EnhancedPartialRetriever.loadPartialSeqSummaryCard using SummaryCardPartial + associated reads" must {
    "return a list of successful partial summary card objects, one to test each reconciliation status" in {
      val response                               =
        """[
          |{"partialName": "card1", "partialContent": "content1", "partialReconciliationStatus": {"code":4, "name":"Overpaid"}}, 
          |{"partialName": "card2", "partialContent": "content2", "partialReconciliationStatus": {"code":5, "name":"Underpaid"}},
          |{"partialName": "card3", "partialContent": "content3", "partialReconciliationStatus": {"code":1, "name":"Balanced"}},
          |{"partialName": "card4", "partialContent": "content4", "partialReconciliationStatus": {"code":2, "name":"OpTolerance"}},
          |{"partialName": "card5", "partialContent": "content5", "partialReconciliationStatus": {"code":3, "name":"UpTolerance"}},
          |{"partialName": "card6", "partialContent": "content6", "partialReconciliationStatus": {"code":7, "name":"BalancedSA"}},
          |{"partialName": "card7", "partialContent": "content7", "partialReconciliationStatus": {"code":8, "name":"BalancedNoEmp"}},
          |{"partialName": "card8", "partialContent": "content8", "partialReconciliationStatus": {"code":-1, "name":"None"}}
          |]""".stripMargin
      val returnPartial: Seq[SummaryCardPartial] = Seq(
        SummaryCardPartial("card1", Html("content1"), Overpaid),
        SummaryCardPartial("card2", Html("content2"), Underpaid),
        SummaryCardPartial("card3", Html("content3"), Balanced),
        SummaryCardPartial("card4", Html("content4"), OverpaidWithinTolerance),
        SummaryCardPartial("card5", Html("content5"), UnderpaidWithinTolerance),
        SummaryCardPartial("card6", Html("content6"), BalancedSA),
        SummaryCardPartial("card7", Html("content7"), BalancedNoEmployment),
        SummaryCardPartial("card8", Html("content8"), NoReconciliationStatus)
      )
      val url                                    = s"http://localhost:${server.port()}/"
      server.stubFor(
        get(urlEqualTo("/")).willReturn(ok(response))
      )
      sut.loadPartialAsSeqSummaryCard[SummaryCardPartial](url).futureValue mustBe returnPartial
    }

    "throw a jsresultexception when there is an unknown reconciliation status" in {
      val response =
        """[
          |{"partialName": "card1", "partialContent": "content1", "partialReconciliationStatus": {"code":99, "name":"Somenewstatus"}}
          |]""".stripMargin

      val url = s"http://localhost:${server.port()}/"
      server.stubFor(
        get(urlEqualTo("/")).willReturn(ok(response))
      )

      recoverToSucceededIf[JsResultException] {
        sut.loadPartialAsSeqSummaryCard[SummaryCardPartial](url)
      }
    }

    "throw a jsresultexception when there is a missing reconciliation status code" in {
      val response =
        """[
          |{"partialName": "card1", "partialContent": "content1", "partialReconciliationStatus": {"name":"Somenewstatus"}}
          |]""".stripMargin

      val url = s"http://localhost:${server.port()}/"
      server.stubFor(
        get(urlEqualTo("/")).willReturn(ok(response))
      )

      recoverToSucceededIf[JsResultException] {
        sut.loadPartialAsSeqSummaryCard[SummaryCardPartial](url)
      }
    }

    "throw a jsresultexception when there is a missing partialReconciliationStatus field" in {
      val response =
        """[
          |{"partialName": "card1", "partialContent": "content1"}
          |]""".stripMargin

      val url = s"http://localhost:${server.port()}/"
      server.stubFor(
        get(urlEqualTo("/")).willReturn(ok(response))
      )

      recoverToSucceededIf[JsResultException] {
        sut.loadPartialAsSeqSummaryCard[SummaryCardPartial](url)
      }
    }

    "return an empty list and log the failure when 5xx response code returned" in {
      val url                            = s"http://localhost:${server.port()}/"
      server.stubFor(
        get(urlEqualTo("/")).willReturn(serverError().withBody("error"))
      )
      sut.loadPartialAsSeqSummaryCard[SummaryCardPartial](url).futureValue mustBe Nil
      val captor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      Mockito
        .verify(mockLogger, times(1))
        .error(captor.capture())(ArgumentMatchers.any())
      captor.getValue.contains("Failed to load partial")
    }

    "return an empty list when empty list returned" in {
      val response =
        """[]"""
      val url      = s"http://localhost:${server.port()}/"
      server.stubFor(
        get(urlEqualTo("/")).willReturn(ok(response))
      )
      sut.loadPartialAsSeqSummaryCard[SummaryCardPartial](url).futureValue mustBe Nil
    }

    "return an empty list when nothing returned" in {
      val response = ""
      val url      = s"http://localhost:${server.port()}/"
      server.stubFor(
        get(urlEqualTo("/")).willReturn(ok(response))
      )
      sut.loadPartialAsSeqSummaryCard[SummaryCardPartial](url).futureValue mustBe Nil
    }
  }
}

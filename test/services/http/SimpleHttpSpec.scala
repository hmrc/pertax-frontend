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

package services.http

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, post, put, urlEqualTo}
import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.concurrent.IntegrationPatience
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.http.Status._
import play.api.libs.json.Writes
import play.api.test.Injecting
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import testUtils.BaseSpec

import java.util.concurrent.LinkedBlockingQueue
import scala.concurrent.{ExecutionContext, Future}

class SimpleHttpSpec extends BaseSpec with WireMockHelper with Injecting with IntegrationPatience {

  lazy val simpleHttp = inject[SimpleHttp]
  lazy val url        = s"http://localhost:${server.port}"
  val magicErrorCode  = 123456789

  "Calling SimpleHttpSpec.get" must {
    List(OK, BAD_REQUEST, NOT_FOUND, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { httpStatus =>
      s"act as a pass through for a HttpResponse with status $httpStatus" in {
        server.stubFor(get(urlEqualTo("/")).willReturn(aResponse().withStatus(httpStatus)))

        val result: Future[Int] = simpleHttp.get(url)(
          onComplete = { r =>
            r.status
          },
          onError = { _ =>
            magicErrorCode
          }
        )

        result.futureValue mustBe httpStatus
      }
    }

    "calls the onError function if there is a fault" in {
      server.stubFor(get(urlEqualTo("/")).willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)))

      val result: Future[Int] = simpleHttp.get(url)(
        onComplete = { r =>
          r.status
        },
        onError = { _ =>
          magicErrorCode
        }
      )

      result.futureValue mustBe magicErrorCode
    }
  }

  "Calling SimpleHttpSpec.put" must {
    List(OK, BAD_REQUEST, NOT_FOUND, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { httpStatus =>
      s"act as a pass through for a HttpResponse with status $httpStatus" in {
        server.stubFor(put(urlEqualTo("/")).willReturn(aResponse().withStatus(httpStatus)))

        val result: Future[Int] = simpleHttp.put(url, "")(
          onComplete = { r =>
            r.status
          },
          onError = { _ =>
            magicErrorCode
          }
        )

        result.futureValue mustBe httpStatus
      }
    }

    "calls the onError function if there is a fault" in {
      server.stubFor(put(urlEqualTo("/")).willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)))

      val result: Future[Int] = simpleHttp.put(url, "")(
        onComplete = { r =>
          r.status
        },
        onError = { _ =>
          magicErrorCode
        }
      )

      result.futureValue mustBe magicErrorCode
    }
  }

  "Calling SimpleHttpSpec.post" must {
    List(OK, BAD_REQUEST, NOT_FOUND, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { httpStatus =>
      s"act as a pass through for a HttpResponse with status $httpStatus" in {
        server.stubFor(post(urlEqualTo("/")).willReturn(aResponse().withStatus(httpStatus)))

        val result: Future[Int] = simpleHttp.post(url, "")(
          onComplete = { r =>
            r.status
          },
          onError = { _ =>
            magicErrorCode
          }
        )

        result.futureValue mustBe httpStatus
      }
    }

    "calls the onError function if there is a fault" in {
      server.stubFor(put(urlEqualTo("/")).willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)))

      val result: Future[Int] = simpleHttp.put(url, "")(
        onComplete = { r =>
          r.status
        },
        onError = { _ =>
          magicErrorCode
        }
      )

      result.futureValue mustBe magicErrorCode
    }
  }
}

//Mock client for use in other tests
class FakeSimpleHttp(response: Either[HttpResponse, Exception])(implicit ec: ExecutionContext)
    extends SimpleHttp(mock[HttpClient]) {

  private val headerCarrierQueue = new LinkedBlockingQueue[HeaderCarrier]
  def getLastHeaderCarrier       = headerCarrierQueue.take

  override def get[T](
    url: String
  )(onComplete: HttpResponse => T, onError: Exception => T)(implicit hc: HeaderCarrier): Future[T] = {
    headerCarrierQueue.put(hc)
    Future.successful {
      response match {
        case Left(response) => onComplete(response)
        case Right(ex)      => onError(ex)
      }
    }
  }

  override def post[I, T](url: String, body: I)(onComplete: HttpResponse => T, onError: Exception => T)(implicit
    hc: HeaderCarrier,
    w: Writes[I]
  ): Future[T] = {
    headerCarrierQueue.put(hc)
    Future.successful {
      response match {
        case Left(response) => onComplete(response)
        case Right(ex)      => onError(ex)
      }
    }
  }

  override def put[I, T](url: String, body: I)(onComplete: HttpResponse => T, onError: Exception => T)(implicit
    hc: HeaderCarrier,
    w: Writes[I]
  ): Future[T] = {
    headerCarrierQueue.put(hc)
    Future.successful {
      response match {
        case Left(response) => onComplete(response)
        case Right(ex)      => onError(ex)
      }
    }
  }
}

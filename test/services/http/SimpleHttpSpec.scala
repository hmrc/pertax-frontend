/*
 * Copyright 2020 HM Revenue & Customs
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

import java.util.concurrent.LinkedBlockingQueue

import com.codahale.metrics.Timer
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Writes
import uk.gov.hmrc.http.{BadGatewayException, HeaderCarrier, HttpResponse}
import util.BaseSpec

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class SimpleHttpSpec extends BaseSpec {

  trait Setup {

    implicit val hc = HeaderCarrier()

    def httpResponse: Future[HttpResponse]

    lazy val timer = MockitoSugar.mock[Timer.Context]

    def makeDummyRequest(): Unit

    trait DummyCallbacks {
      def complete(r: HttpResponse): String
      def exception(e: Exception): String
    }

    lazy val dummyCallbacks = MockitoSugar.mock[DummyCallbacks]
    lazy val http = {
      val h = MockitoSugar.mock[WsAllMethods]
      when(h.GET[HttpResponse](any())(any(), any(), any())) thenReturn httpResponse
      h
    }

    lazy val simpleHttp = new SimpleHttp(http)
  }

  "Calling SimpleHttpSpec.get" should {

    trait LocalSetup extends Setup {

      override lazy val http = {
        val h = MockitoSugar.mock[WsAllMethods]
        when(h.GET[HttpResponse](any())(any(), any(), any())) thenReturn httpResponse
        h
      }

      def makeDummyRequest() = await(simpleHttp.get("/")(dummyCallbacks.complete, dummyCallbacks.exception))
    }

    "Call onSuccess if the http call returned successfully" in new LocalSetup {
      lazy val httpResponse = Future.successful(HttpResponse(200))
      makeDummyRequest
      verify(dummyCallbacks, times(0)).exception(any())
      verify(dummyCallbacks, times(1)).complete(any())
    }

    "Call onFail if the http call resulted in an exception" in new LocalSetup {
      lazy val httpResponse = Future.failed(new BadGatewayException("Bad Gateway"))
      makeDummyRequest
      verify(dummyCallbacks, times(1)).exception(any())
      verify(dummyCallbacks, times(0)).complete(any())
    }
  }

  "Calling SimpleHttpSpec.post" should {

    trait LocalSetup extends Setup {

      override lazy val http = {
        val h = MockitoSugar.mock[WsAllMethods]
        when(h.POST[String, HttpResponse](any(), any(), any())(any(), any(), any(), any())) thenReturn httpResponse
        h
      }

      def makeDummyRequest = await(simpleHttp.post("/", "Body")(dummyCallbacks.complete, dummyCallbacks.exception))
    }

    "Call onSuccess if the http call returned successfully" in new LocalSetup {
      lazy val httpResponse = Future.successful(HttpResponse(200))
      makeDummyRequest
      verify(dummyCallbacks, times(0)).exception(any())
      verify(dummyCallbacks, times(1)).complete(any())
    }

    "Call onFail if the http call resulted in an exception" in new LocalSetup {
      lazy val httpResponse = Future.failed(new BadGatewayException("Bad Gateway"))
      makeDummyRequest
      verify(dummyCallbacks, times(1)).exception(any())
      verify(dummyCallbacks, times(0)).complete(any())
    }
  }

  "Calling SimpleHttpSpec.put" should {

    trait LocalSetup extends Setup {

      override lazy val http = {
        val h = MockitoSugar.mock[WsAllMethods]
        when(h.PUT[String, HttpResponse](any(), any())(any(), any(), any(), any())) thenReturn httpResponse
        h
      }

      def makeDummyRequest = await(simpleHttp.put("/", "Body")(dummyCallbacks.complete, dummyCallbacks.exception))
    }

    "Call onSuccess if the http call returned successfully" in new LocalSetup {
      lazy val httpResponse = Future.successful(HttpResponse(200))
      makeDummyRequest
      verify(dummyCallbacks, times(0)).exception(any())
      verify(dummyCallbacks, times(1)).complete(any())
    }

    "Call onFail if the http call resulted in an exception" in new LocalSetup {
      lazy val httpResponse = Future.failed(new BadGatewayException("Bad Gateway"))
      makeDummyRequest
      verify(dummyCallbacks, times(1)).exception(any())
      verify(dummyCallbacks, times(0)).complete(any())
    }
  }
}

//Mock client for use in other tests
class FakeSimpleHttp(response: Either[HttpResponse, Exception]) extends SimpleHttp(MockitoSugar.mock[WsAllMethods]) {

  private val headerCarrierQueue = new LinkedBlockingQueue[HeaderCarrier]
  def getLastHeaderCarrier = headerCarrierQueue.take

  override def get[T](url: String)(onComplete: HttpResponse => T, onError: Exception => T)(
    implicit hc: HeaderCarrier): Future[T] = {
    headerCarrierQueue.put(hc)
    Future.successful {
      response match {
        case Left(response) => onComplete(response)
        case Right(ex)      => onError(ex)
      }
    }
  }

  override def post[I, T](url: String, body: I)(onComplete: HttpResponse => T, onError: Exception => T)(
    implicit hc: HeaderCarrier,
    w: Writes[I]): Future[T] = {
    headerCarrierQueue.put(hc)
    Future.successful {
      response match {
        case Left(response) => onComplete(response)
        case Right(ex)      => onError(ex)
      }
    }
  }

  override def put[I, T](url: String, body: I)(onComplete: HttpResponse => T, onError: Exception => T)(
    implicit hc: HeaderCarrier,
    w: Writes[I]): Future[T] = {
    headerCarrierQueue.put(hc)
    Future.successful {
      response match {
        case Left(response) => onComplete(response)
        case Right(ex)      => onError(ex)
      }
    }
  }
}

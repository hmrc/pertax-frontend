/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import play.api.libs.json.Writes
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
@Singleton
class SimpleHttp @Inject()(http: WsAllMethods) {

  implicit val r = new HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse): HttpResponse = response
  }

  def get[T](url: String)(onComplete: HttpResponse => T, onError: Exception => T)(
    implicit hc: HeaderCarrier): Future[T] =
    http.GET[HttpResponse](url) map { response =>
      onComplete(response)
    } recover {
      case e: Exception =>
        onError(e)
    }

  def post[I, T](url: String, body: I)(onComplete: HttpResponse => T, onError: Exception => T)(
    implicit hc: HeaderCarrier,
    w: Writes[I]): Future[T] =
    http.POST[I, HttpResponse](url, body) map { response =>
      onComplete(response)
    } recover {
      case e: Exception =>
        onError(e)
    }

  def put[I, T](url: String, body: I)(onComplete: HttpResponse => T, onError: Exception => T)(
    implicit hc: HeaderCarrier,
    w: Writes[I]): Future[T] =
    http.PUT[I, HttpResponse](url, body) map { response =>
      onComplete(response)
    } recover {
      case e: Exception =>
        onError(e)
    }

}

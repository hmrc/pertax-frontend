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

import com.google.inject.Inject
import play.api.Logging
import play.api.libs.json.{JsArray, Json, Reads}
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpException, StringContextOps}
import uk.gov.hmrc.play.partials.{HeaderCarrierForPartialsConverter, HtmlPartial}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class EnhancedPartialRetriever @Inject() (
  val httpClientV2: HttpClientV2,
  headerCarrierForPartialsConverter: HeaderCarrierForPartialsConverter
) extends Logging {

  private def requestBuilder(url: String, timeoutInMilliseconds: Int)(implicit
    request: RequestHeader
  ): RequestBuilder = {
    implicit val hc: HeaderCarrier = headerCarrierForPartialsConverter.fromRequestWithEncryptedCookie(request)
    val get                        = httpClientV2.get(url"$url")
    if (timeoutInMilliseconds == 0) {
      get
    } else {
      get.transform(_.withRequestTimeout(timeoutInMilliseconds.milliseconds))
    }
  }

  def loadPartial(url: String, timeoutInMilliseconds: Int = 0)(implicit
    request: RequestHeader,
    ec: ExecutionContext
  ): Future[HtmlPartial] =
    requestBuilder(url, timeoutInMilliseconds).execute[HtmlPartial].map {
      case partial: HtmlPartial.Success => partial
      case partial: HtmlPartial.Failure =>
        logger.error(s"Failed to load partial from $url, partial info: $partial")
        partial
    } recover {
      case ex: HttpException =>
        logger.error(s"Failed to load partial from $url, partial info. Exception: $ex")
        HtmlPartial.Failure(Some(ex.responseCode))
      case _                 =>
        HtmlPartial.Failure(None)
    }

  def loadPartialAsSeqSummaryCard[A](url: String, timeoutInMilliseconds: Int = 0)(implicit
    request: RequestHeader,
    ec: ExecutionContext,
    reads: Reads[A]
  ): Future[Seq[A]] =
    requestBuilder(url, timeoutInMilliseconds).execute[HtmlPartial].map {
      case partial: HtmlPartial.Success =>
        val response = partial.content.toString
        if (response.nonEmpty) {
          Json.parse(response).as[JsArray].value.map(_.as[A]).toSeq
        } else {
          Nil
        }
      case partial: HtmlPartial.Failure =>
        logger.error(s"Failed to load partial from $url, partial info: $partial")
        Nil
    } recover {
      case ex: HttpException =>
        logger.error(s"Failed to load partial from $url, partial info. Exception: $ex")
        Nil
      case _                 => Nil
    }
}

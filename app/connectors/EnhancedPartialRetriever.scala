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
import play.api.mvc.RequestHeader
import play.twirl.api.Html
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpException, StringContextOps}
import uk.gov.hmrc.play.partials.{HeaderCarrierForPartialsConverter, HtmlPartial}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class EnhancedPartialRetriever @Inject() (
  val httpClientV2: HttpClientV2,
  headerCarrierForPartialsConverter: HeaderCarrierForPartialsConverter
) extends Logging {

  def loadPartial(url: String, timeoutInMilliseconds: Int = 0)(implicit
    request: RequestHeader,
    ec: ExecutionContext
  ): Future[HtmlPartial] = {
    def requestBuilder: RequestBuilder = {
      implicit val hc: HeaderCarrier = headerCarrierForPartialsConverter.fromRequestWithEncryptedCookie(request)
      val get                        = httpClientV2.get(url"$url")
      if (timeoutInMilliseconds == 0) {
        get
      } else {
        get.transform(_.withRequestTimeout(timeoutInMilliseconds.milliseconds))
      }
    }

    requestBuilder.execute[HtmlPartial].map {
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
  }

  def loadPartialSeq(url: String, timeoutInMilliseconds: Int = 0)(implicit
    request: RequestHeader,
    ec: ExecutionContext
  ): Future[Seq[HtmlPartial]] = {
    def requestBuilder: RequestBuilder = {
      implicit val hc: HeaderCarrier = headerCarrierForPartialsConverter.fromRequestWithEncryptedCookie(request)
      val get                        = httpClientV2.get(url"$url")
      if (timeoutInMilliseconds == 0) {
        get
      } else {
        get.transform(_.withRequestTimeout(timeoutInMilliseconds.milliseconds))
      }
    }

    requestBuilder.execute[HtmlPartial].map { e =>
      println("\n\n*** RESPONSE FROM TAX CALC PARTIAL:" + e)

      /*
      ** RESPONSE FROM TAX CALC PARTIAL:Success(None,[{"partialName":"card1","partialContent":"\n  <div onclick=\"location.href='/tax-you-paid/2022-2023/paid-too-much';\" class=\"card active\"  data-journey-click=\"button - click:summary card - 2022 :overpaid\">\n    <h2 class=\"govuk-heading-s card-heading\">\n      \n\n\n\n    <a href=\"/tax-you-paid/2022-2023/paid-too-much\" class=\"govuk-link\"   >6 April 2022 to 5 April 2023</a>\n\n\n\n    </h2>\n\n    \n        \n<p  class=\"card-body owe_message\">HMRC owe you £84.23 .</p>\n\n        \n<p  class=\"card-body\">Get your refund paid online.</p>\n\n      \n  </div>"},{"partialName":"card2","partialContent":"\n  <div onclick=\"location.href='/tax-you-paid/2021-2022/paid-too-little';\" class=\"card active\" data-journey-click=\"button - click:summary card - 2021 :underpaid\">\n    <h2 class=\"govuk-heading-s card-heading govuk-!-margin-bottom-2\">\n      \n\n\n\n    <a href=\"/tax-you-paid/2021-2022/paid-too-little\" class=\"govuk-link\"   >6 April 2021 to 5 April 2022</a>\n\n\n\n    </h2>\n    \n      \n<p  class=\"card-body owe_message\">You owe £500 .</p>\n\n\n\n      \n          \n            \n<p  class=\"card-body\">You should have paid by 19 February 2018 but you can still make a payment now.</p>\n\n          \n        \n    \n  </div>"}])

       */

      Seq(HtmlPartial.Success(Some("test"), Html("")))
    }
  }
}

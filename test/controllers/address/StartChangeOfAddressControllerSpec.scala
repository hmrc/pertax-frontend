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

package controllers.address

import controllers.bindable.{PostalAddrType, ResidentialAddrType}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.mvc.Result
import play.api.test.Helpers._

import scala.concurrent.Future

class StartChangeOfAddressControllerSpec extends AddressBaseSpec {
  private lazy val controller: StartChangeOfAddressController = app.injector.instanceOf[StartChangeOfAddressController]

  private val startNowUrl = routes.DoYouLiveInTheUKController.onPageLoad.url

  "onPageLoad" must {
    "return 200 and correct content when passed ResidentialAddrType" in {
      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(currentRequest)
      status(result) mustBe OK
      val doc: Document          = Jsoup.parse(contentAsString(result))
      val bodyElements           = doc.getElementsByClass("govuk-body")
      doc
        .getElementsByClass("govuk-list")
        .eachText()
        .toString mustBe "[Child Benefit Income Tax National Insurance State Pension]"
      bodyElements
        .first()
        .toString
        .contains("Tell HMRC when your main address changes. This will update your details for:") mustBe true
      bodyElements.next().next().toString.contains("Wait until you've moved before updating your address.") mustBe true
      doc.getElementsByClass("govuk-button").attr("href").contains(startNowUrl) mustBe true
    }

    "return 200 and correct content when passed PostalAddrType" in {
      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(currentRequest)
      status(result) mustBe OK
      val doc: Document          = Jsoup.parse(contentAsString(result))
      val bodyElements           = doc.getElementsByClass("govuk-body")
      doc
        .getElementsByClass("govuk-list")
        .eachText()
        .toString mustBe "[Child Benefit Income Tax National Insurance State Pension]"
      bodyElements
        .first()
        .toString
        .contains("Tell HMRC when your postal address changes. All letters will be sent to this address.") mustBe true
      bodyElements.next().toString.contains("This will update your details for:") mustBe true
      doc.getElementsByClass("govuk-button").attr("href").contains(startNowUrl) mustBe true
    }

  }
}

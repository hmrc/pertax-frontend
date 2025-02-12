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

  "onPageLoad" must {
    "return 200 and correct content when passed ResidentialAddrType" in {
      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(currentRequest)
      status(result) mustBe OK
      val doc: Document          = Jsoup.parse(contentAsString(result))
      doc.getElementsByTag("h1").toString.contains("Change your main address") mustBe true
    }

    "return 200 and correct content when passed PostalAddrType" in {
      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(currentRequest)
      status(result) mustBe OK
      val doc: Document          = Jsoup.parse(contentAsString(result))
      doc.getElementsByTag("h1").toString.contains("Change your postal address") mustBe true
    }

  }
}

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

package views.html.personaldetails

import config.ConfigDecorator
import controllers.address.routes
import controllers.auth.requests.UserRequest
import controllers.bindable.{PostalAddrType, ResidentialAddrType}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.Application
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.UserRequestFixture.buildUserRequest
import views.html.ViewSpec

class StartChangeOfAddressViewSpec extends ViewSpec {
  override implicit lazy val app: Application = localGuiceApplicationBuilder().build()

  lazy val view: StartChangeOfAddressView = inject[StartChangeOfAddressView]

  implicit val configDecorator: ConfigDecorator                 = inject[ConfigDecorator]
  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
    buildUserRequest(request = FakeRequest())

  "render correct content when passed ResidentialAddrType" in {
    val result        = view(ResidentialAddrType).toString
    val doc: Document = Jsoup.parse(result)

    doc.getElementsByTag("title").toString.contains("Change your main address")
    doc.getElementsByTag("h1").toString.contains("Change your main address") mustBe true
    val bodyElements = doc.getElementsByClass("govuk-body")
    doc
      .getElementsByClass("govuk-list")
      .eachText()
      .toString mustBe "[Child Benefit Income Tax National Insurance State Pension]"
    bodyElements
      .first()
      .toString
      .contains("Tell HMRC when your main address changes. This will update your details for:") mustBe true
    bodyElements.next().next().toString.contains("Wait until you've moved before updating your address.") mustBe true
    doc
      .getElementsByClass("govuk-button")
      .attr("href")
      .contains(routes.DoYouLiveInTheUKController.onPageLoad.url) mustBe true
  }

  "render correct content when passed PostalAddrType" in {
    val result        = view(PostalAddrType).toString
    val doc: Document = Jsoup.parse(result)
    doc.getElementsByTag("title").toString.contains("Change your postal address")
    doc.getElementsByTag("h1").toString.contains("Change your postal address") mustBe true
    val bodyElements = doc.getElementsByClass("govuk-body")
    doc
      .getElementsByClass("govuk-list")
      .eachText()
      .toString mustBe "[Child Benefit Income Tax National Insurance State Pension]"
    bodyElements
      .first()
      .toString
      .contains("Tell HMRC when your postal address changes. All letters will be sent to this address.") mustBe true
    bodyElements.next().toString.contains("This will update your details for:") mustBe true
    doc
      .getElementsByClass("govuk-button")
      .attr("href")
      .contains(routes.PostalDoYouLiveInTheUKController.onPageLoad.url) mustBe true
  }
}

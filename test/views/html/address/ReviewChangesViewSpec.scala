/*
 * Copyright 2021 HM Revenue & Customs
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

package views.html.address

import config.ConfigDecorator
import controllers.address.routes
import controllers.bindable.{AddrType, PostalAddrType, PrimaryAddrType, SoleAddrType}
import models.dto.AddressDto
import play.api.test.FakeRequest
import util.UserRequestFixture.buildUserRequest
import views.html.ViewSpec
import views.html.personaldetails.ReviewChangesView

class ReviewChangesViewSpec extends ViewSpec {
  override implicit lazy val app = localGuiceApplicationBuilder().build()

  lazy val view = injected[ReviewChangesView]

  implicit val configDecorator: ConfigDecorator = injected[ConfigDecorator]
  implicit val userRequest = buildUserRequest(request = FakeRequest())
  val address =
    AddressDto("AddressLine1", "AddressLine2", None, None, None, Some("TestPostcode"), None, None)

  def result(addressType: AddrType) = asDocument(view(addressType, address, "yes.label", true, None, false).toString)

  "rendering ReviewChangesView" must {
    "when postal address has been changed display 'is your address in the uk'" in {

      assertContainsText(result(PostalAddrType), messages("label.is_your_postal_address_in_the_uk"))
      assertNotContainText(result(PostalAddrType), messages("label.do_you_live_in_the_uk"))
    }

    "when postal address has been changed display link to PostalInternationalAddressChoiceController" in {

      assertContainsLink(
        result(PostalAddrType),
        messages("label.change"),
        routes.PostalInternationalAddressChoiceController.onPageLoad().url)

      assertNotContainLink(
        result(PostalAddrType),
        messages("label.change"),
        routes.InternationalAddressChoiceController.onPageLoad(PostalAddrType).url)
    }

    "when sole address has been changed display 'do you live in the uk'" in {

      assertContainsText(result(SoleAddrType), messages("label.do_you_live_in_the_uk"))
      assertNotContainText(result(SoleAddrType), messages("label.is_your_postal_address_in_the_uk"))

    }

    "when sole address has been changed display link to InternationalPostalAddressChoiceController" in {

      assertContainsLink(
        result(SoleAddrType),
        messages("label.change"),
        routes.InternationalAddressChoiceController.onPageLoad(SoleAddrType).url)

      assertNotContainLink(
        result(SoleAddrType),
        messages("label.change"),
        routes.PostalInternationalAddressChoiceController.onPageLoad().url)
    }

    "when primary address has been changed display 'is your address in the uk'" in {

      assertContainsText(result(PrimaryAddrType), messages("label.do_you_live_in_the_uk"))
      assertNotContainText(result(PrimaryAddrType), messages("label.is_your_postal_address_in_the_uk"))

    }

    "when primary address has been changed display link to InternationalPostalAddressChoiceController" in {

      assertContainsLink(
        result(PrimaryAddrType),
        messages("label.change"),
        routes.InternationalAddressChoiceController.onPageLoad(PrimaryAddrType).url)

      assertNotContainLink(
        result(PrimaryAddrType),
        messages("label.change"),
        routes.PostalInternationalAddressChoiceController.onPageLoad().url)
    }
  }
}

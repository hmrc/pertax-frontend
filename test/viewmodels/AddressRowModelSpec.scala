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

package viewmodels

import play.twirl.api.HtmlFormat
import views.html.ViewSpec

class AddressRowModelSpec extends ViewSpec {

  val address = PersonalDetailsTableRowModel(
    "postal_address",
    "label.postal_address",
    HtmlFormat.empty,
    "label.change",
    "label.your.postal_address",
    Some("link")
  )

  "extraPostalAddressLink" must {
    "does not contain extra links when there is no main address" in {
      val addressRowModel = AddressRowModel(None, Some(address))
      addressRowModel.extraPostalAddressLink() mustBe None
    }

    "does not contain extra links when the postal address is the same as the main address" in {
      val sameAddress = PersonalDetailsTableRowModel(
        "postal_address",
        "label.postal_address",
        HtmlFormat.raw(messages("label.same_as_main_address")),
        "label.change",
        "label.your.postal_address",
        None
      )

      val addressRowModel = AddressRowModel(Some(address), Some(sameAddress))
      addressRowModel.extraPostalAddressLink() mustBe None
    }

    "does not contain extra links when the postal address has been removed" in {
      val sameAddress = PersonalDetailsTableRowModel(
        "postal_address",
        "label.postal_address",
        HtmlFormat.empty,
        "label.change",
        "label.your.postal_address",
        None
      )

      val addressRowModel = AddressRowModel(Some(address), Some(sameAddress))
      addressRowModel.extraPostalAddressLink() mustBe None
    }

    "contain extra links to close the postal address when there is a main address" in {
      val addressRowModel = AddressRowModel(Some(address), Some(address))
      addressRowModel.extraPostalAddressLink() mustBe Some(
        ExtraLinks(
          "label.remove",
          AddressRowModel.closePostalAddressUrl
        )
      )
    }
  }
}

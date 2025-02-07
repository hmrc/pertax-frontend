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

package viewmodels

import controllers.bindable.PostalAddrType
import viewmodels.AddressRowModel.closePostalAddressUrl

final case class ExtraLinks(linkTextMessage: String, linkUrl: String)

final case class AddressRowModel(
  mainAddress: Option[PersonalDetailsTableRowModel],
  postalAddress: Option[PersonalDetailsTableRowModel]
) {
  def extraPostalAddressLink(): Option[ExtraLinks] = {
    def showRemoveLink(address: PersonalDetailsTableRowModel) = {
      val hasSameAddress   = address.isPostalAddressSame
      val canRemoveAddress = address.linkUrl.isDefined

      !hasSameAddress && canRemoveAddress
    }

    postalAddress
      .filter(showRemoveLink)
      .flatMap(_ =>
        mainAddress
          .map(_ => ExtraLinks("label.remove", closePostalAddressUrl))
      )
  }
}

object AddressRowModel {
  def changeMainAddressUrl: String =
    controllers.address.routes.TaxCreditsChoiceController.onPageLoad.url

  val closePostalAddressUrl: String  = controllers.address.routes.ClosePostalAddressController.onPageLoad.url
  val changePostalAddressUrl: String =
    controllers.address.routes.StartChangeOfAddressController.onPageLoad(PostalAddrType).url
}

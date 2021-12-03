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

import config.ConfigDecorator
import play.api.i18n.Messages
import viewmodels.AddressRowModel.closePostalAddressUrl

final case class ExtraLinks(linkTextMessage: String, linkUrl: String)

final case class AddressRowModel(
  mainAddress: Option[PersonalDetailsTableRowModel],
  postalAddress: Option[PersonalDetailsTableRowModel]
) {
  def extraPostalAddressLink()(implicit messages: Messages): Option[ExtraLinks] = {
    def showRemoveLink(address: PersonalDetailsTableRowModel) = {
      val hasSameAddress = address.content.toString().contains(messages("label.same_as_main_address"))
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
  def changeMainAddressUrl(configDecorator: ConfigDecorator, taxCreditsAvailable: Boolean): String =
    if (configDecorator.taxCreditsEnabled && taxCreditsAvailable) {
      controllers.address.routes.TaxCreditsChoiceController.onPageLoad.url
    } else {
      controllers.address.routes.DoYouLiveInTheUKController.onPageLoad.url
    }

  val closePostalAddressUrl = controllers.address.routes.ClosePostalAddressController.onPageLoad.url
  val changePostalAddressUrl = controllers.address.routes.PostalDoYouLiveInTheUKController.onPageLoad.url
}

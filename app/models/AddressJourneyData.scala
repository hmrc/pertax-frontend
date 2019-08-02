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

package models

import models.dto._
import models.addresslookup.AddressRecord

case class AddressJourneyData(
  addressPageVisitedDto: Option[AddressPageVisitedDto],
  residencyChoiceDto: Option[ResidencyChoiceDto],
  addressFinderDto: Option[AddressFinderDto],
  selectedAddressRecord: Option[AddressRecord],
  submittedAddressDto: Option[AddressDto],
  subbmittedInternationalAddressChoiceDto: Option[InternationalAddressChoiceDto],
  submittedStartDateDto: Option[DateDto],
  addressLookupServiceDown: Boolean
) {
  def getAddressToDisplay: Option[AddressDto] =
    submittedAddressDto match {
      case Some(s) => Some(s)
      case None =>
        selectedAddressRecord match {
          case Some(y) => Some(AddressDto.fromAddressRecord(y))
          case None    => None
        }
    }

}

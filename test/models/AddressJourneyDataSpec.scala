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

package models

import models.dto.AddressDto
import testUtils.BaseSpec
import testUtils.Fixtures._

class AddressJourneyDataSpec extends BaseSpec {

  "Calling getAddressToDisplay " must {

    "return none when there is no address journey data" in {
      val journeyData =
        AddressJourneyData(None, None, None, None, None, None, None, None, addressLookupServiceDown = false)
      val result      = journeyData.getAddressToDisplay
      result mustBe None
    }

    "return selected Address record when there is only a selected Address in the journey data" in {
      val journeyData     =
        AddressJourneyData(
          None,
          None,
          None,
          None,
          Some(fakeStreetPafAddressRecord),
          None,
          None,
          None,
          addressLookupServiceDown = false
        )
      val result          = journeyData.getAddressToDisplay
      val selectedAddress = AddressDto.fromAddressRecord(fakeStreetPafAddressRecord)
      result mustBe Some(selectedAddress)
    }

    "return submitted Address record when there is only a submitted Address in the journey data" in {

      val journeyData      = AddressJourneyData(
        None,
        None,
        None,
        None,
        None,
        Some(AddressDto.fromAddressRecord(fakeStreetPafAddressRecord)),
        None,
        None,
        addressLookupServiceDown = false
      )
      val result           = journeyData.getAddressToDisplay
      val submittedAddress = AddressDto.fromAddressRecord(fakeStreetPafAddressRecord)
      result mustBe Some(submittedAddress)
    }

    "return submitted Address record when there is a selected and submitted Address in the journey data" in {

      val journeyData      = AddressJourneyData(
        None,
        None,
        None,
        None,
        Some(oneOtherPlacePafAddressRecord),
        Some(AddressDto.fromAddressRecord(fakeStreetPafAddressRecord)),
        None,
        None,
        addressLookupServiceDown = false
      )
      val result           = journeyData.getAddressToDisplay
      val submittedAddress = AddressDto.fromAddressRecord(fakeStreetPafAddressRecord)
      result mustBe Some(submittedAddress)
    }

  }
}

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

package models.addressLookup

import models.addresslookup.RecordSet
import play.api.libs.json.Json
import uk.gov.hmrc.play.test.UnitSpec
import util.Fixtures._

import scala.io.Source

class RecordSetSpec extends UnitSpec {

  "calling fromJsonAddressLookupService" should {
    "filter out all addresses with addressLines missing" in {
      val addressesWithMissingLinesJson: String =
        Source
          .fromInputStream(getClass.getResourceAsStream("/address-lookup/recordSetWithMissingAddressLines.json"))
          .mkString
      val result = RecordSet.fromJsonAddressLookupService(Json.parse(addressesWithMissingLinesJson))

      result shouldBe twoOtherPlaceRecordSet
    }

    "return all addresses with where the addressLines are present" in {
      val addressesWhichAllContainLinesJson: String =
        Source.fromInputStream(getClass.getResourceAsStream("/address-lookup/recordSet.json")).mkString
      val result = RecordSet.fromJsonAddressLookupService(Json.parse(addressesWhichAllContainLinesJson))

      result shouldBe oneAndTwoOtherPlacePafRecordSet
    }
  }
}

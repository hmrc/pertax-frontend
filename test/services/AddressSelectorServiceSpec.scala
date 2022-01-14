/*
 * Copyright 2022 HM Revenue & Customs
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

package services

import models.addresslookup.{Address, AddressRecord, Country, RecordSet}
import util.Addresses._
import util.BaseSpec

class AddressSelectorServiceSpec extends BaseSpec {

  val service = new AddressSelectorService

  "orderSet" must {
    "return a ordered seq of addresses" when {
      "a valid seq of English addresses is passed in" in {
        service.orderSet(englandRecordSet.addresses).flatMap(x => x.address.lines) mustBe expectedSimpleLines
      }
      "a valid seq of Scottish addresses is passed in" in {
        service.orderSet(scotlandRecordSet.addresses).flatMap(x => x.address.lines) mustBe expectedSimpleLines
      }

      "a complex unordered sequence of addresses is passed in" in {
        service.orderSet(complexRecordSet.addresses).flatMap(x => x.address.lines) mustBe expectedComplexLines
      }
    }

    "return empty list" when {
      "the record set passed in is empty" in {
        service.orderSet(Seq.empty) mustBe List()
      }

      "handle invalid addresses and return them last" when {
        "the record set has some invalid addresses" in {
          service.orderSet(badDataRecordSet.addresses).flatMap(x => x.address.lines) mustBe expectedBadDataLines
        }
      }
    }
  }
}

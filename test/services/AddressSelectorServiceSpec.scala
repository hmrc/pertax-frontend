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

package services

import models.addresslookup.{Address, AddressRecord, Country, RecordSet}
import util.BaseSpec

class AddressSelectorServiceSpec extends BaseSpec {

  val service = new AddressSelectorService

  val englandRecordSet = RecordSet(
    Seq(
      AddressRecord(
        "some id",
        Address(
          List("1 Too many addresses crescent"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-ENG", "England")),
          Country("eng", "England")
        ),
        "en"
      ),
      AddressRecord(
        "some id",
        Address(
          List("10 Too many addresses crescent"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-ENG", "England")),
          Country("eng", "England")
        ),
        "en"
      ),
      AddressRecord(
        "some id",
        Address(
          List("11 Too many addresses crescent"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-ENG", "England")),
          Country("eng", "England")
        ),
        "en"
      ),
      AddressRecord(
        "some id",
        Address(
          List("2 Too many addresses crescent"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-ENG", "England")),
          Country("eng", "England")
        ),
        "en"
      ),
      AddressRecord(
        "some id",
        Address(
          List("20 Too many addresses crescent"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-ENG", "England")),
          Country("eng", "England")
        ),
        "en"
      ),
      AddressRecord(
        "some id",
        Address(
          List("21 Too many addresses crescent"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-ENG", "England")),
          Country("eng", "England")
        ),
        "en"
      )
    )
  )

  val expecetdRecordSet: RecordSet = RecordSet(
    Seq(
      AddressRecord(
        "some id",
        Address(
          List("1 Too many addresses crescent"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-ENG", "England")),
          Country("eng", "England")
        ),
        "en"
      ),
      AddressRecord(
        "some id",
        Address(
          List("2 Too many addresses crescent"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-ENG", "England")),
          Country("eng", "England")
        ),
        "en"
      ),
      AddressRecord(
        "some id",
        Address(
          List("10 Too many addresses crescent"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-ENG", "England")),
          Country("eng", "England")
        ),
        "en"
      ),
      AddressRecord(
        "some id",
        Address(
          List("11 Too many addresses crescent"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-ENG", "England")),
          Country("eng", "England")
        ),
        "en"
      ),
      AddressRecord(
        "some id",
        Address(
          List("20 Too many addresses crescent"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-ENG", "England")),
          Country("eng", "England")
        ),
        "en"
      ),
      AddressRecord(
        "some id",
        Address(
          List("21 Too many addresses crescent"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-ENG", "England")),
          Country("eng", "England")
        ),
        "en"
      )
    )
  )

  "orderSet" must {
    "return a RecordSet" when {
      "a valid RecordSet is passed in" in {
        service.orderSet(englandRecordSet) mustBe expecetdRecordSet
      }
    }
//    "return None" when {
//      " when given an empty RecordSet" in {
//        service.orderSet() mustBe None
//      }
//    }
  }

}

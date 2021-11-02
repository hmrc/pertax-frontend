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

package util

import models.addresslookup.{Address, AddressRecord, Country, RecordSet}

object Addresses {

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

  val scotlandRecordSet = RecordSet(
    Seq(
      AddressRecord(
        "some id",
        Address(
          List("1 Too many addresses crescent"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-SCT", "Scotland")),
          Country("blah", "blah")
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
          Some(Country("GB-SCT", "Scotland")),
          Country("blah", "blah")
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
          Some(Country("GB-SCT", "Scotland")),
          Country("blah", "blah")
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
          Some(Country("GB-SCT", "Scotland")),
          Country("blah", "blah")
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
          Some(Country("GB-SCT", "Scotland")),
          Country("blah", "blah")
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
          Some(Country("GB-SCT", "Scotland")),
          Country("blah", "blah")
        ),
        "en"
      )
    )
  )

  val expectedSimpleLines = Seq(
    "1 Too many addresses crescent",
    "2 Too many addresses crescent",
    "10 Too many addresses crescent",
    "11 Too many addresses crescent",
    "20 Too many addresses crescent",
    "21 Too many addresses crescent"
  )

  val complexRecordSet: RecordSet = RecordSet(
    Seq(
      AddressRecord(
        "some id",
        Address(
          List("Flat 1 The Curtains Up Comeragh Road"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-SCT", "Scotland")),
          Country("blah", "blah")
        ),
        "en"
      ),
      AddressRecord(
        "some id",
        Address(
          List("Flat B 78 Comeragh Road"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-SCT", "Scotland")),
          Country("blah", "blah")
        ),
        "en"
      ),
      AddressRecord(
        "some id",
        Address(
          List("Flat 2 The Curtains Up Comeragh Road"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-SCT", "Scotland")),
          Country("blah", "blah")
        ),
        "en"
      ),
      AddressRecord(
        "some id",
        Address(
          List("Flat 2 74 Comeragh Road"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-SCT", "Scotland")),
          Country("blah", "blah")
        ),
        "en"
      ),
      AddressRecord(
        "some id",
        Address(
          List("Flat 1 70 Comeragh Road"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-SCT", "Scotland")),
          Country("blah", "blah")
        ),
        "en"
      ),
      AddressRecord(
        "some id",
        Address(
          List("Flat 1 74 Comeragh Road"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-SCT", "Scotland")),
          Country("blah", "blah")
        ),
        "en"
      ),
      AddressRecord(
        "some id",
        Address(
          List("72a Comeragh Road"),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-SCT", "Scotland")),
          Country("blah", "blah")
        ),
        "en"
      )
    )
  )

  val expectedComplexLines = Seq(
    "Flat 1 The Curtains Up Comeragh Road",
    "Flat 2 The Curtains Up Comeragh Road",
    "Flat 1 70 Comeragh Road",
    "72a Comeragh Road",
    "Flat 1 74 Comeragh Road",
    "Flat 2 74 Comeragh Road",
    "Flat B 78 Comeragh Road"
  )

  val badDataRecordSet = RecordSet(
    Seq(
      AddressRecord(
        "some id",
        Address(
          List(""),
          Some("Anytown"),
          None,
          "",
          Some(Country("GB-SCT", "Scotland")),
          Country("", "")
        ),
        "en"
      ),
      AddressRecord(
        "some id",
        Address(
          List("2 Too many addresses crescent"),
          Some("Anytown"),
          None,
          "",
          Some(Country("GB-SCT", "Scotland")),
          Country("blah", "blah")
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
          Some(Country("GB-SCT", "Scotland")),
          Country("", "")
        ),
        "en"
      ),
      AddressRecord(
        "some id",
        Address(
          List(""),
          Some("Anytown"),
          None,
          "FX2 7SS",
          Some(Country("GB-SCT", "Scotland")),
          Country("blah", "blah")
        ),
        "en"
      )
    )
  )

  val expectedBadDataLines = Seq("2 Too many addresses crescent", "10 Too many addresses crescent", "", "")
}

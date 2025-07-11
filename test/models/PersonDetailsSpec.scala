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

import play.api.libs.json.Json
import testUtils.{BaseSpec, CitizenDetailsFixtures, Fixtures}

class PersonDetailsSpec extends BaseSpec with CitizenDetailsFixtures {

  trait LocalSetup {
    def firstName: Option[String]

    def lastName: Option[String]

    lazy val pd: Person = Person(firstName, None, lastName, None, None, None, None, None, None)
  }

  "Calling PersonDetails.shortName" must {

    "Produce firstname lastname when someone has both" in new LocalSetup {

      val firstName: Option[String] = Some("Firstname")
      val lastName: Option[String]  = Some("Lastname")

      pd.shortName mustBe Some("Firstname Lastname")
    }
    "Produce empty string when someone has both but both empty" in new LocalSetup {

      val firstName: Option[String] = Some("")
      val lastName: Option[String]  = Some("")

      pd.shortName mustBe Some("")
    }

    "Produce nothing when only lastname is present" in new LocalSetup {

      val firstName: Option[String] = None
      val lastName: Option[String]  = Some("Lastname")

      pd.shortName mustBe None
    }

    "Produce nothing when only firstname is present" in new LocalSetup {

      val firstName: Option[String] = Some("Firstname")
      val lastName: Option[String]  = None

      pd.shortName mustBe None
    }

    "Produce nothing when no firstname or lastname is present" in new LocalSetup {

      val firstName: Option[String] = None
      val lastName: Option[String]  = None

      pd.shortName mustBe None
    }
  }

  "Converting json to a PersonDetails Object" must {

    "convert correctly when json is in the new citizen-details api format (string date)" in {
      Json
        .parse(s"""
                  |{
                  |  "etag" : "115",
                  |  "person" : {
                  |    "firstName" : "Firstname",
                  |    "middleName" : "Middlename",
                  |    "lastName" : "Lastname",
                  |    "initials" : "FML",
                  |    "title" : "Dr",
                  |    "honours" : "Phd.",
                  |    "sex" : "M",
                  |    "dateOfBirth" : "1945-03-18",
                  |    "nino" : "${Fixtures.fakeNino}"
                  |  },
                  |  "address" : {
                  |    "line1" : "1 Fake Street",
                  |    "line2" : "Fake Town",
                  |    "line3" : "Fake City",
                  |    "line4" : "Fake Region",
                  |    "postcode" : "AA1 1AA",
                  |    "startDate" : "2015-03-15",
                  |    "type" : "Residential"
                  |  }
                  |}
                  |
        """.stripMargin)
        .as[PersonDetails] mustBe buildPersonDetails
    }

  }

  "Converting a PersonDetails object to json" must {

    "convert to json in the correct format" in {

      val json = Json.toJson(buildPersonDetails)

      json mustBe Json.parse(s"""
                                |{
                                |  "person" : {
                                |    "firstName" : "Firstname",
                                |    "middleName" : "Middlename",
                                |    "lastName" : "Lastname",
                                |    "initials" : "FML",
                                |    "title" : "Dr",
                                |    "honours" : "Phd.",
                                |    "sex" : "M",
                                |    "dateOfBirth" : "1945-03-18",
                                |    "nino" : "${Fixtures.fakeNino}"
                                |  },
                                |  "address" : {
                                |    "line1" : "1 Fake Street",
                                |    "line2" : "Fake Town",
                                |    "line3" : "Fake City",
                                |    "line4" : "Fake Region",
                                |    "postcode" : "AA1 1AA",
                                |    "startDate" : "2015-03-15",
                                |    "type" : "Residential"
                                |  }
                                |}
                                |
        """.stripMargin)
    }

  }
  "Calling Address.isWelshLanguageUnit" must {
    "return false when the address doesn't match a Welsh Language Unit" in {

      val address =
        Address(None, None, None, None, postcode = Some("AA1 1AA"), None, None, None, None, isRls = false)

      address.isWelshLanguageUnit mustBe false
    }
    "return true when the address does match a Welsh Language Unit" in {

      val address =
        Address(None, None, None, None, postcode = Some("CF145Sh"), None, None, None, None, isRls = false)

      address.isWelshLanguageUnit mustBe true
    }
  }
}

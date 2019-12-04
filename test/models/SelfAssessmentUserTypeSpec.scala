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

import play.api.libs.json.{JsError, Json}
import uk.gov.hmrc.domain.SaUtr
import util.BaseSpec

class SelfAssessmentUserTypeSpec extends BaseSpec {

  "SelfAssessmentUserType" should {

    val utr = "123456789"

    val testList = List(
      ("ActivatedOnlineFilerSelfAssessmentUser", ActivatedOnlineFilerSelfAssessmentUser(SaUtr(utr))),
      ("NotYetActivatedOnlineFilerSelfAssessmentUser", NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr(utr))),
      ("WrongCredentialsSelfAssessmentUser", WrongCredentialsSelfAssessmentUser(SaUtr(utr))),
      ("NotEnrolledSelfAssessmentUser", NotEnrolledSelfAssessmentUser(SaUtr(utr)))
    )

    testList.foreach {
      case (key, obj) =>
        s"serialise and deserialise a $key" in {

          val converted = Json.toJson(obj)

          converted.as[SelfAssessmentUserType] shouldBe obj
        }
    }

    "serialise and deserialise a NonFilerSelfAssessmentUser" in {

      val converted = Json.toJson(NonFilerSelfAssessmentUser)

      converted.as[SelfAssessmentUserType] shouldBe NonFilerSelfAssessmentUser
    }

    "return a JsError" when {

      "reading from json fails" in {

        val json = Json.parse(s"""{"_type": "TestObject", "utr": "$utr"}""")

        json.validate[SelfAssessmentUserType] shouldBe JsError("Could not read SelfAssessmentUserType")
      }
    }
  }
}

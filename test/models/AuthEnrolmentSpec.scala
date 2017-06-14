/*
 * Copyright 2017 HM Revenue & Customs
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



class AuthEnrolmentSpec extends BaseSpec {

  "Reading an authenrolment from some json data" should {

    "parse correctly" in {

      def jsVal = Json.parse(
        """
         |[
         |  {
         |    "key": "IR-SA",
         |    "identifiers": [
         |      {
         |        "key": "UTR",
         |        "value": "1111111111"
         |      }
         |    ],
         |    "state": "Activated"
         |  }
         |]
       """.stripMargin)

      lazy val authEnrolment = Json.fromJson[AuthEnrolment](jsVal).get

      authEnrolment shouldBe AuthEnrolment("Activated", SaUtr("1111111111"))
      authEnrolment.isNotYetActivated shouldBe false
    }

  }

  "Reading an AuthEnrollment from a JsValue" should {

    trait LocalSetup {

      def saEnrolmentState: String
      def irSaEnrolmentPresent: Boolean

      lazy val jsVal = Json.arr(
        Json.obj(
          "key" -> (if(irSaEnrolmentPresent) "IR-SA" else "IR-CT"),
          "identifiers" -> Json.arr(Json.obj("key" -> "UTR", "value" -> "1111111111")),
          "state" -> saEnrolmentState
        ),
        Json.obj(
          "key" -> "IR-VAT",
          "identifiers" -> Json.arr(Json.obj("key" -> "UTR", "value" -> "1111111111")),
          "state" -> "Activated"
        )
      )

      lazy val jsResult = Json.fromJson[AuthEnrolment](jsVal)

      lazy val authEnrolment = jsResult.get
    }

    "should create an AuthEnrollment with a state of 'Activated'  correctly if there is an Active IR-SA enrolment present in Json" in new LocalSetup {
      val saEnrolmentState = "Activated"
      val irSaEnrolmentPresent = true

      authEnrolment shouldBe AuthEnrolment("Activated", SaUtr("1111111111"))
      authEnrolment.isNotYetActivated shouldBe false
    }

    "should create an AuthEnrollment with a state of 'NotYetActivated'  correctly if there is a NotYetActivated IR-SA enrolment present in Json" in new LocalSetup {
      val saEnrolmentState = "NotYetActivated"
      val irSaEnrolmentPresent = true

      authEnrolment shouldBe AuthEnrolment("NotYetActivated", SaUtr("1111111111"))
      authEnrolment.isNotYetActivated shouldBe true
    }

    "should not create an AuthEnrollment and produce a JsError when there is no IR-SA enrolment in the Json" in new LocalSetup {
      val saEnrolmentState = "Activated"
      val irSaEnrolmentPresent = false

      jsResult shouldBe JsError("Couldn't extract an IR-SA enrolment record from AuthEnrolment JSON")
    }

    "should not create an AuthEnrollment and produce a JSError when there is no enrolments at all in the Json" in new LocalSetup {
      val saEnrolmentState = "Activated"
      val irSaEnrolmentPresent = false
      override lazy val jsVal = Json.arr()

      jsResult shouldBe JsError("Couldn't extract an IR-SA enrolment record from AuthEnrolment JSON")
    }
  }
}

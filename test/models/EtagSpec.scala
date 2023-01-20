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

import play.api.libs.json.{JsResultException, JsValue, Json}
import testUtils.BaseSpec

class EtagSpec extends BaseSpec {

  val etag = "123"

  "Etag" must {

    "be instantiated" when {

      "json is the correct format" in {

        val json: JsValue = Json.parse(s"""
                                          |{
                                          |   "etag":"$etag"
                                          |}
    """.stripMargin)

        json.as[ETag] mustBe ETag(etag)
      }
    }

    "not be instantiated" when {

      "json is incorrect format" in {

        val json: JsValue = Json.parse(s"""
                                          |{
                                          |   "ftag":"$etag"
                                          |}
    """.stripMargin)

        a[JsResultException] mustBe thrownBy(json.as[ETag])
      }
    }
  }

}

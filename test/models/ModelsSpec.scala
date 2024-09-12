/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.libs.json.{JsPath, JsString, Json}
import testUtils.BaseSpec

class ModelsSpec extends BaseSpec {

  "setObject" must {
    "successfully set a value at the given path" in {
      val jsObject = Json.obj()
      val path     = JsPath \ "key1"
      val value    = JsString("value1")

      val result = jsObject.setObject(path, value)

      result.isSuccess mustBe true
      result.get mustBe Json.obj("key1" -> "value1")
    }

    "fail to set a value with an empty path" in {
      val jsObject = Json.obj("key1" -> "value1")
      val path     = JsPath
      val value    = JsString("value2")

      val result = jsObject.setObject(path, value)

      result.isError mustBe true
    }
  }

  "removeObject" must {
    "successfully remove a value at the given path" in {
      val jsObject = Json.obj("key1" -> "value1", "key2" -> "value2")
      val path     = JsPath \ "key2"

      val result = jsObject.removeObject(path)

      result.isSuccess mustBe true
      result.get mustBe Json.obj("key1" -> "value1")
    }

    "not throw an error if the path doesn't exist" in {
      val jsObject = Json.obj("key1" -> "value1")
      val path     = JsPath \ "key2"

      val result = jsObject.removeObject(path)

      result.isSuccess mustBe true
      result.get mustBe jsObject
    }

    "return a JsError if path is empty" in {
      val jsObject = Json.obj("key1" -> "value1")
      val path     = JsPath
      val result   = jsObject.removeObject(path)

      result.isError mustBe true
    }
  }
}

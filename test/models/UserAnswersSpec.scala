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

import play.api.libs.json._
import routePages.QuestionPage
import testUtils.BaseSpec

import java.time.Instant

class UserAnswersSpec extends BaseSpec {
  case object TestPage extends QuestionPage[JsValue] {
    override def path: JsPath = JsPath \ "test"
  }

  case object EmptyPathPage extends QuestionPage[JsValue] {
    override def path: JsPath = JsPath
  }

  private val id       = "id"
  private val testData = Json.obj("key" -> "value")

  "set" must {
    "successfully set a value" in {
      val userAnswers    = UserAnswers(id)
      val updatedAnswers = userAnswers.set(TestPage, testData)

      val result = updatedAnswers.get
      result.get(TestPage) mustBe Some(testData)
    }

    "fail to set a value when page path is empty" in {
      val invalidData = Json.toJson(Instant.now())
      val userAnswers = UserAnswers(id)

      val result = userAnswers.set(EmptyPathPage, invalidData)
      result.isFailure mustBe true
    }
  }

  "setOrException" must {
    "successfully set a value and not throw an exception" in {
      val userAnswers    = UserAnswers(id)
      val updatedAnswers = userAnswers.setOrException(TestPage, testData)
      updatedAnswers.get(TestPage) mustBe Some(testData)
    }

    "throw an exception if setting fails for empty path" in {
      val invalidValue = Json.toJson(Instant.now())
      val userAnswers  = UserAnswers(id)

      lazy val result = userAnswers.setOrException(EmptyPathPage, invalidValue)
      a[JsResultException] mustBe thrownBy(result)
    }
  }

  "get" must {
    "return None when the page does not exist" in {
      val userAnswers = UserAnswers(id)
      userAnswers.get(TestPage) mustBe None
    }

    "return the value when it has been set using set method" in {
      val userAnswers = UserAnswers(id).setOrException(TestPage, testData)

      val result = userAnswers.get(TestPage)
      result mustBe Some(testData)
    }
  }

  "remove" must {
    "successfully remove a page" in {
      val userAnswers = UserAnswers(id)
        .setOrException(TestPage, testData)

      val result = userAnswers.remove(TestPage).get
      result.get(TestPage) mustBe None
    }

    "throw a RuntimeException when removal fails" in {
      val userAnswers = UserAnswers(id)

      val exception = intercept[RuntimeException] {
        userAnswers.remove(EmptyPathPage)
      }

      exception.getMessage must include("Unable to remove page")
    }
  }

  "isDefined" must {
    "return true if the value is defined" in {
      val userAnswers = UserAnswers(id)
        .setOrException(TestPage, testData)
      userAnswers.isDefined(TestPage) mustBe true
    }

    "return false if the value is not defined" in {
      val userAnswers = UserAnswers(id)
      userAnswers.isDefined(TestPage) mustBe false
    }
  }
}

/*
 * Copyright 2025 HM Revenue & Customs
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

import config.ConfigDecorator
import views.html.ViewSpec

class NameChangeHelperSpec extends ViewSpec {

  private val mockConfigDecorator = mock[ConfigDecorator]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConfigDecorator)

  }

  "conditionalMessage" must {
    "return false content when feature switched off" in {
      when(mockConfigDecorator.featureNameChangeMtdItSaToMtdIt).thenReturn(false)
      NameChangeHelper.conditionalMessage(
        "first",
        "second"
      )(
        mockConfigDecorator,
        messages
      ) mustBe "first"
    }
    "return empty string when feature switched off and string empty" in {
      when(mockConfigDecorator.featureNameChangeMtdItSaToMtdIt).thenReturn(false)
      NameChangeHelper.conditionalMessage(
        "",
        "second"
      )(
        mockConfigDecorator,
        messages
      ) mustBe ""
    }
    "return true content when feature switched on" in {
      when(mockConfigDecorator.featureNameChangeMtdItSaToMtdIt).thenReturn(true)
      NameChangeHelper.conditionalMessage(
        "first",
        "second"
      )(
        mockConfigDecorator,
        messages
      ) mustBe "second"
    }
    "return empty string when feature switched on and string empty" in {
      when(mockConfigDecorator.featureNameChangeMtdItSaToMtdIt).thenReturn(true)
      NameChangeHelper.conditionalMessage(
        "first",
        ""
      )(
        mockConfigDecorator,
        messages
      ) mustBe ""
    }

  }

}

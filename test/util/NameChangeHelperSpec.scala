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

  "correctName" must {
    "make no change when feature switched on and language is English" in {
      when(mockConfigDecorator.featureNameChangeMtdItSaToMtdIt).thenReturn(true)
      NameChangeHelper.correctName(
        "Bla Making Tax Digital for Income Tax Bla",
        mockConfigDecorator,
        messages
      ) mustBe "Bla Making Tax Digital for Income Tax Bla"
    }
    "add Self Assessment to end of name when feature switched off and language is English" in {
      when(mockConfigDecorator.featureNameChangeMtdItSaToMtdIt).thenReturn(false)
      NameChangeHelper.correctName(
        "Bla Making Tax Digital for Income Tax Bla",
        mockConfigDecorator,
        messages
      ) mustBe "Bla Making Tax Digital for Income Tax Self Assessment Bla"
    }
    "make no change when feature switched on and language is Welsh" in {
      when(mockConfigDecorator.featureNameChangeMtdItSaToMtdIt).thenReturn(true)
      NameChangeHelper.correctName(
        "Bla Troi Treth yn Ddigidol ar gyfer Treth Incwm Bla",
        mockConfigDecorator,
        welshMessages
      ) mustBe "Bla Troi Treth yn Ddigidol ar gyfer Treth Incwm Bla"
    }

    "Add Welsh for Self Assessment to end of name when feature switched off and language is Welsh" in {
      when(mockConfigDecorator.featureNameChangeMtdItSaToMtdIt).thenReturn(false)

      NameChangeHelper.correctName(
        "Bla Troi Treth yn Ddigidol ar gyfer Treth Incwm Bla",
        mockConfigDecorator,
        welshMessages
      ) mustBe "Bla Troi Treth yn Ddigidol ar gyfer Hunanasesiad Treth Incwm Bla"
    }
  }

}

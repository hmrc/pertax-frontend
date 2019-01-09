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

package views.html.ambiguousjourney

import config.ConfigDecorator
import models.PertaxContext
import models.dto.AmbiguousUserFlowDto
import org.jsoup.Jsoup
import play.api.i18n.Messages
import play.api.test.FakeRequest
import util.{BaseSpec, Fixtures}

class usedUtrToRegisterChoiceSpec extends BaseSpec {

  implicit val messages = Messages.Implicits.applicationMessages

  "usedUtrToRegisterChoice view" should {
    "check page contents" in {
      val pertaxUser = Fixtures.buildFakePertaxUser(isGovernmentGateway = true, isHighGG = true)
      val form  =  AmbiguousUserFlowDto.form
      val document = Jsoup.parse(views.html.ambiguousjourney.usedUtrToRegisterChoice(form)(PertaxContext(FakeRequest("GET", "/test"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(pertaxUser)), messages).toString)
      document.getElementsByTag("h1").text shouldBe Messages("label.have_you_used_your_utr_to_enrol")
      document.getElementsContainingOwnText(Messages("label.your_utr_is_a_ten_digit_number_we_sent_by_post")).hasText shouldBe true
    }
  }

}

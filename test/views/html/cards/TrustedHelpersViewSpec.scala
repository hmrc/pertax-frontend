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

package views.html.cards

import config.ConfigDecorator
import play.api.i18n.Messages
import views.html.ViewSpec
import views.html.cards.home.TrustedHelpersView

class TrustedHelpersViewSpec extends ViewSpec {

  lazy val trustedHelpersView: TrustedHelpersView    = inject[TrustedHelpersView]
  implicit lazy val configDecorator: ConfigDecorator = inject[ConfigDecorator]

  "Trusted Helpers card" must {
    val doc = asDocument(trustedHelpersView().toString)

    "render the given heading correctly" in {
      doc.text() must include(Messages("label.trusted_helpers"))
    }

    "render the given content correctly" in {
      doc.text() must include(Messages("label.trusted_helpers_content"))
    }

    "have the expected card id" in {
      doc.getElementById("trusted-helpers-card") must not be null
    }

    "link to manage trusted helpers URL from config" in {
      doc.toString must include(configDecorator.manageTrustedHelpersUrl)
    }
  }
}

/*
 * Copyright 2022 HM Revenue & Customs
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
import views.html.cards.home.ChildBenefitSingleAccountView

class ChildBenefitSingleAccountViewSpec extends ViewSpec {

  val childBenefitSingleAccountView: ChildBenefitSingleAccountView = injected[ChildBenefitSingleAccountView]
  implicit val configDecorator: ConfigDecorator                    = injected[ConfigDecorator]

  val nextDeadlineTaxYear = "2021"

  "Child Benefit Single Account card" must {
    val doc =
      asDocument(
        childBenefitSingleAccountView().toString
      )

    "render the given heading correctly" in {
      doc.text() must include(
        Messages("label.child_benefit")
      )
    }

    "render the given content correctly" in {
      doc.text() must include(
        Messages("label.a_payment_to_help_with_the_cost_of_bringing_up_children")
      )
    }
  }
}

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

package views.html.cards

import org.jsoup.nodes.Document
import views.html.ViewSpec
import views.html.cards.home.NISPView

class NISPViewSpec extends ViewSpec {

  val nispView: NISPView = inject[NISPView]

  val doc: Document = asDocument(nispView().toString)

  "NISPView" must {
    "render the correct heading" in {
      doc.text() must include(
        messages("label.national_insurance_and_state_pension")
      )
    }

    "render the correct content" in {
      doc.text() must include(
        messages("label.view_national_insurance")
      )

      doc.text() must include(
        messages("label.view_state_pension")
      )
    }
  }
}

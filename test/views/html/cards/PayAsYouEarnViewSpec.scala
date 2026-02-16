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
import controllers.auth.requests.UserRequest
import org.mockito.Mockito.*
import play.api
import play.api.Application
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import repositories.JourneyCacheRepository
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.domain.Nino
import views.html.ViewSpec
import views.html.cards.home.PayAsYouEarnView

class PayAsYouEarnViewSpec extends ViewSpec {

  lazy val payAsYouEarnView: PayAsYouEarnView = inject[PayAsYouEarnView]

  "paye card" must {
    "render PAYE card with redirect-to-paye link" in {
      val doc = asDocument(payAsYouEarnView().toString)

      doc
        .getElementById("paye-card")
        .getElementsByClass("card__link")
        .attr("href") mustBe controllers.routes.RedirectToPayeController.redirectToPaye.url
    }

    "render correct heading" in {
      val doc = asDocument(payAsYouEarnView().toString)

      doc
        .getElementById("paye-card")
        .text() must include(messages("label.pay_as_you_earn_paye"))
    }

    "render correct description" in {
      val doc = asDocument(payAsYouEarnView().toString)

      doc
        .getElementById("paye-card")
        .text() must include(messages("label.your_income_from_employers_and_private_pensions_"))
    }
  }
}

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

  lazy val payAsYouEarnView: PayAsYouEarnView       = inject[PayAsYouEarnView]
  implicit val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      api.inject.bind[ConfigDecorator].toInstance(mockConfigDecorator),
      api.inject.bind[JourneyCacheRepository].toInstance(mock[JourneyCacheRepository])
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConfigDecorator)
    when(mockConfigDecorator.taiHost).thenReturn("")
    when(mockConfigDecorator.payeToPegaRedirectUrl).thenReturn("http://paye-to-pega-redirect-url")
  }

  "paye card" must {

    "point to check-income-tax when toggle is OFF (shouldUsePegaRouting = false)" in {
      when(mockConfigDecorator.payeToPegaRedirectList).thenReturn(Seq(5))

      val nino                                             = "AA000055A"
      val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(request = FakeRequest(), authNino = Nino(nino))

      val doc = asDocument(payAsYouEarnView(shouldUsePegaRouting = false)(implicitly, userRequest).toString)

      doc
        .getElementById("paye-card")
        .getElementsByClass("card-link")
        .attr("href") mustBe "/check-income-tax/what-do-you-want-to-do"
    }

    "point to check-income-tax when toggle is ON but NINO does not match" in {
      when(mockConfigDecorator.payeToPegaRedirectList).thenReturn(Seq(5))

      val nino                                             = "AA000003A"
      val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(request = FakeRequest(), authNino = Nino(nino))

      val doc = asDocument(payAsYouEarnView(shouldUsePegaRouting = true)(implicitly, userRequest).toString)

      doc
        .getElementById("paye-card")
        .getElementsByClass("card-link")
        .attr("href") mustBe "/check-income-tax/what-do-you-want-to-do"
    }

    "point to pega when toggle is ON and NINO matches" in {
      when(mockConfigDecorator.payeToPegaRedirectList).thenReturn(Seq(5))

      val nino                                             = "AA000055A"
      val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(request = FakeRequest(), authNino = Nino(nino))

      val doc = asDocument(payAsYouEarnView(shouldUsePegaRouting = true)(implicitly, userRequest).toString)

      doc
        .getElementById("paye-card")
        .getElementsByClass("card-link")
        .attr("href") mustBe "http://paye-to-pega-redirect-url"
    }
    "point to check-income-tax when toggle is ON and NINO matches but user helping (trusted helpers)" in {
      when(mockConfigDecorator.payeToPegaRedirectList).thenReturn(Seq(5))

      val nino                                             = "AA000055A"
      val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          request = FakeRequest(),
          authNino = Nino(nino),
          trustedHelper = Some(TrustedHelper("", "", "", Some(nino)))
        )

      val doc = asDocument(payAsYouEarnView(shouldUsePegaRouting = true)(implicitly, userRequest).toString)

      doc
        .getElementById("paye-card")
        .getElementsByClass("card-link")
        .attr("href") mustBe "/check-income-tax/what-do-you-want-to-do"
    }
  }
}

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

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.bindable.Origin
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api
import play.api.Application
import play.api.i18n.Messages
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import repositories.JourneyCacheRepository
import testUtils.Fixtures
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.domain.Nino
import views.html.ViewSpec
import views.html.cards.home.{ChildBenefitSingleAccountView, PayAsYouEarnView}

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
    when(mockConfigDecorator.defaultOrigin).thenReturn(Origin("PERTAX"))
    when(mockConfigDecorator.personalAccount).thenReturn("/personal-account")
    when(mockConfigDecorator.getFeedbackSurveyUrl(any())).thenReturn("/feedback/url")
    when(mockConfigDecorator.taiHost).thenReturn("")
  }

  val nextDeadlineTaxYear = "2021"

  "paye point to check-income-tax when pega redirect is empty" in {
    when(mockConfigDecorator.payeToPegaRedirectUrl).thenReturn("http://paye-to-pega-redirect-url")
    when(mockConfigDecorator.payeToPegaRedirectList).thenReturn(Seq.empty)

    val userRequest: UserRequest[AnyContentAsEmpty.type] =
      buildUserRequest(request = FakeRequest())

    val doc =
      asDocument(
        payAsYouEarnView()(implicitly, userRequest).toString
      )

    doc
      .getElementById("paye-card")
      .getElementsByClass("card-link")
      .attr("href") mustBe "/check-income-tax/what-do-you-want-to-do"

  }

  "paye point to pega when nino is ending with 50" in {
    when(mockConfigDecorator.payeToPegaRedirectUrl).thenReturn("http://paye-to-pega-redirect-url")
    when(mockConfigDecorator.payeToPegaRedirectList).thenReturn(Seq(5))

    val nino                                             = Fixtures.fakeNino.withoutSuffix.take(6)
    val userRequest: UserRequest[AnyContentAsEmpty.type] =
      buildUserRequest(request = FakeRequest(), authNino = Nino(nino + "50A"))

    val doc =
      asDocument(
        payAsYouEarnView()(implicitly, userRequest).toString
      )

    doc
      .getElementById("paye-card")
      .getElementsByClass("card-link")
      .attr("href") mustBe "http://paye-to-pega-redirect-url"

  }

  "paye point to check-income-tax when nino is not ending with 50" in {
    when(mockConfigDecorator.payeToPegaRedirectUrl).thenReturn("http://paye-to-pega-redirect-url")
    when(mockConfigDecorator.payeToPegaRedirectList).thenReturn(Seq(5))

    val nino                                             = Fixtures.fakeNino.withoutSuffix.take(6)
    val userRequest: UserRequest[AnyContentAsEmpty.type] =
      buildUserRequest(request = FakeRequest(), authNino = Nino(nino + "30A"))

    val doc =
      asDocument(
        payAsYouEarnView()(implicitly, userRequest).toString
      )

    doc
      .getElementById("paye-card")
      .getElementsByClass("card-link")
      .attr("href") mustBe "/check-income-tax/what-do-you-want-to-do"

  }
}

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

package views.html.interstitial

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.domain.Nino
import viewmodels.AlertBannerViewModel
import views.html.ViewSpec

class ViewNISPViewSpec extends ViewSpec {

  lazy val view: ViewNISPView = inject[ViewNISPView]

  lazy implicit val configDecorator: ConfigDecorator            = inject[ConfigDecorator]
  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
    buildUserRequest(request = FakeRequest())

  val emptyBannerViewModel: AlertBannerViewModel = AlertBannerViewModel(Nil)

  "Rendering ViewNISPView.scala.html" must {

    "render alert banner content if present" in {
      val bannerHtml      = Html("<div class='govuk-notification-banner'>Voluntary Contributions Banner</div>")
      val bannerViewModel = AlertBannerViewModel(List(bannerHtml))

      val document = asDocument(view(Html(""), None, bannerViewModel).toString)
      document.body().toString must include("Voluntary Contributions Banner")
    }

    "display the expected page header" in {
      val document = asDocument(view(Html(""), None, emptyBannerViewModel).toString)
      document.body().toString must include("Your National Insurance and State Pension")
    }

    "display the expected reason for paying National Insurance contributions" in {
      val document = asDocument(view(Html(""), None, emptyBannerViewModel).toString)
      document.body().toString must include(
        "You pay National Insurance contributions to qualify for certain benefits and your State Pension."
      )
    }

    "display the expected State Pension/National Insurance header" in {
      val document = asDocument(view(Html(""), None, emptyBannerViewModel).toString)
      document.body().toString must include("Your State Pension summary and National Insurance record")
    }

    "display the expected benefits of viewing the State Pension Summary" in {
      val document = asDocument(view(Html(""), None, emptyBannerViewModel).toString)
      document.body().toString must include("View your State Pension summary to find out:")
      document.body().toString must include("when you can get your State Pension")
      document.body().toString must include("how much you can get")
      document.body().toString must include("if you can increase it")
    }

    "display the expected benefits of viewing the National Insurance Record" in {
      val document = asDocument(view(Html(""), None, emptyBannerViewModel).toString)
      document.body().toString must include("View your National Insurance record to find out:")
      document.body().toString must include("what you’ve paid up to the current tax year")
      document.body().toString must include("if you’ve received any National Insurance credits")
      document.body().toString must include("if you can increase it")
    }

    "display the expected way of accessing the State pension summary" in {
      val document = asDocument(view(Html(""), None, emptyBannerViewModel).toString)

      val hyperLinkElement = document.select("#viewStatePensionSummary")
      hyperLinkElement.text() mustBe "View your State Pension summary"
      hyperLinkElement.attr("href") mustBe configDecorator.statePensionSummary
    }

    "display the expected way of accessing the National Insurance Record" in {
      val document = asDocument(view(Html(""), None, emptyBannerViewModel).toString)

      val hyperLinkElement = document.select("#viewNationalInsuranceSummary")
      hyperLinkElement.text() mustBe "View your National Insurance record"
      hyperLinkElement.attr("href") mustBe configDecorator.nationalInsuranceRecordUrl
    }

    "display the expected National Insurance Number header" in {
      val document = asDocument(view(Html(""), None, emptyBannerViewModel).toString)
      document.body().toString must include("View and save your National Insurance number")
    }

    "display the expected National Insurance Number value" in {
      val nino        = new Nino("CS700100A")
      val document    = asDocument(view(Html(""), Some(nino), emptyBannerViewModel).toString)
      val ninoElement = document.select(".nino")
      ninoElement.text().trim mustBe nino.value
      document.body().toString must include("Your National Insurance number is")
    }

    "display the National Insurance number from trustedHelper when available" in {
      val trustedHelperNino            = new Nino("CS700100A")
      val trustedHelper                =
        Some(TrustedHelper("principalName", "attorneyName", "returnLinkUrl", Some(trustedHelperNino.nino)))
      val userRequestWithTrustedHelper = buildUserRequest(trustedHelper = trustedHelper, request = FakeRequest())
      val document                     = asDocument(
        view(Html(""), None, emptyBannerViewModel)(userRequestWithTrustedHelper, configDecorator, messages).toString
      )

      document.body().toString must include("Your National Insurance number is")
      val ninoElement = document.select(".nino")
      ninoElement.text().trim() mustBe trustedHelperNino.value
    }

    "display the National Insurance number from nino when trustedHelper is absent" in {
      val nino                            = new Nino("CS700100A")
      val userRequestWithoutTrustedHelper = buildUserRequest(trustedHelper = None, request = FakeRequest())
      val document                        =
        asDocument(
          view(Html(""), Some(nino), emptyBannerViewModel)(
            userRequestWithoutTrustedHelper,
            configDecorator,
            messages
          ).toString
        )

      document.body().toString must include("Your National Insurance number is")
      val ninoElement = document.select(".nino")
      ninoElement.text().trim mustBe nino.value
    }

    "display the National Insurance number from nino when trustedHelper has no principalNino" in {
      val nino                               = new Nino("CS700100A")
      val trustedHelper                      = Some(TrustedHelper("principalName", "attorneyName", "returnLinkUrl", None))
      val userRequestWithTrustedHelperNoNino = buildUserRequest(trustedHelper = trustedHelper, request = FakeRequest())
      val document                           =
        asDocument(
          view(Html(""), Some(nino), emptyBannerViewModel)(
            userRequestWithTrustedHelperNoNino,
            configDecorator,
            messages
          ).toString
        )

      document.body().toString must include("Your National Insurance number is")
      val ninoElement = document.select(".nino")
      ninoElement.text().trim mustBe nino.value
    }

    "not display the National Insurance number when both trustedHelper and nino are absent" in {
      val userRequestWithoutTrustedHelper = buildUserRequest(trustedHelper = None, request = FakeRequest())
      val document                        =
        asDocument(
          view(Html(""), None, emptyBannerViewModel)(
            userRequestWithoutTrustedHelper,
            configDecorator,
            messages
          ).toString
        )

      document.body().toString must not include "Your National Insurance number is"
    }

    "display the expected National Insurance Number use cases" in {
      val document = asDocument(view(Html(""), None, emptyBannerViewModel).toString)
      document.body().toString must include(
        "You will need your National Insurance number for employment, applying for a student loan and to claim certain benefits."
      )
    }

    "display the expected reason for saving the National Insurance Number" in {
      val document = asDocument(view(Html(""), None, emptyBannerViewModel).toString)
      document.body().toString must include("So that you have your number when you need it, you can:")
    }

    "display the expected ways of saving the National Insurance Number" in {
      val document = asDocument(view(Html(""), None, emptyBannerViewModel).toString)
      document.body().toString must include("view and get a copy of your National Insurance number confirmation letter")
      document.body().toString must include(
        "save your National Insurance number to the wallet on your smartphone, or smartwatch"
      )
    }

    "display the expected National Insurance Number information links" in {
      val document = asDocument(view(Html(""), None, emptyBannerViewModel).toString)

      val viewNinoHyperLinkElement = document.select("#viewNationalInsuranceNumber")
      viewNinoHyperLinkElement.text() mustBe "View and save your National Insurance number"
      viewNinoHyperLinkElement.attr("href") mustBe configDecorator.ptaNinoSaveUrl

      val findOutAboutNinoHyperLinkElement = document.select("#findOutAboutNationalInsuranceNumber")
      findOutAboutNinoHyperLinkElement.text() mustBe "Find out more about National Insurance (opens in a new tab)"
      findOutAboutNinoHyperLinkElement.attr("href") mustBe "https://www.gov.uk/national-insurance"
    }
  }
}

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

package views.html.interstitial

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.domain.Nino
import views.html.ViewSpec
import views.html.tags.formattedNino

class ViewNISPViewSpec extends ViewSpec {

  lazy val view: ViewNISPView = inject[ViewNISPView]

  lazy implicit val configDecorator: ConfigDecorator            = inject[ConfigDecorator]
  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())

  "Rendering ViewNISPView.scala.html" must {

    "display the expected page header" in {
      val document = asDocument(view(Html(""), "", None).toString)
      document.body().toString must include("Your National Insurance and State Pension")
    }

    "display the expected reason for paying National Insurance contributions" in {
      val document = asDocument(view(Html(""), "", None).toString)
      document.body().toString must include(
        "You pay National Insurance contributions to qualify for certain benefits and your State Pension."
      )
    }

    "display the expected State Pension/National Insurance header" in {
      val document = asDocument(view(Html(""), "", None).toString)
      document.body().toString must include("Your State Pension summary and National Insurance record")
    }

    "display the expected benefits of viewing the State Pension Summary" in {
      val document = asDocument(view(Html(""), "", None).toString)
      document.body().toString must include("View your State Pension summary to find out:")
      document.body().toString must include("when you can get your State Pension")
      document.body().toString must include("how much you can get")
      document.body().toString must include("if you can increase it")
    }

    "display the expected benefits of viewing the National Insurance Record" in {
      val document = asDocument(view(Html(""), "", None).toString)
      document.body().toString must include("View your National Insurance record to find out:")
      document.body().toString must include("what you’ve paid up to the current tax year")
      document.body().toString must include("if you’ve received any National Insurance credits")
      document.body().toString must include("if you can increase it")
    }

    "display the expected way of accessing the National Insurance Record" in {
      val document = asDocument(view(Html(""), "", None).toString)
      document.body().toString must include(
        "You can access your National Insurance record from your State Pension summary."
      )

      val hyperLinkElement = document.select("#viewStatePensionSummary")
      hyperLinkElement.text()       shouldBe "View your State Pension summary"
      hyperLinkElement.attr("href") shouldBe configDecorator.statePensionSummary
    }

    "display the expected National Insurance Number header" in {
      val document = asDocument(view(Html(""), "", None).toString)
      document.body().toString must include("View and save your National Insurance number")
    }

    "display the expected National Insurance Number value" in {
      val nino        = new Nino("CS700100A")
      val document    = asDocument(view(Html(""), "", Some(nino)).toString)
      val ninoElement = document.select(".nino")
      ninoElement.text().trim shouldBe formattedNino(nino).toString()
    }

    "display the expected National Insurance Number use cases" in {
      val document = asDocument(view(Html(""), "", None).toString)
      document.body().toString must include(
        "You will need your National Insurance number for employment, applying for a student loan and to claim certain benefits."
      )
    }

    "display the expected reason for saving the National Insurance Number" in {
      val document = asDocument(view(Html(""), "", None).toString)
      document.body().toString must include("So that you have your number when you need it, you can:")
    }

    "display the expected ways of saving the National Insurance Number" in {
      val document = asDocument(view(Html(""), "", None).toString)
      document.body().toString must include("view and get a copy of your National Insurance number confirmation letter")
      document.body().toString must include(
        "save your National Insurance number to the wallet on your smartphone, or smartwatch"
      )
    }

    "display the expected National Insurance Number information links" in {
      val document = asDocument(view(Html(""), "", None).toString)

      val viewNinoHyperLinkElement = document.select("#viewNationalInsuranceNumber")
      viewNinoHyperLinkElement.text()       shouldBe "View and save your National Insurance number"
      viewNinoHyperLinkElement.attr("href") shouldBe configDecorator.ptaNinoSaveUrl

      val findOutAboutNinoHyperLinkElement = document.select("#findOutAboutNationalInsuranceNumber")
      findOutAboutNinoHyperLinkElement.text() shouldBe "Find out more about National Insurance"
      findOutAboutNinoHyperLinkElement.attr("href") shouldBe "https://www.gov.uk/national-insurance" //TODO: Should we keep this hard-coded ?
    }

    "display the expected National Insurance forms header" in {
      val document = asDocument(view(Html(""), "", None).toString)
      document.body().toString must include("National Insurance forms")
    }

    "display the expected National Insurance form links" in {
      val document = asDocument(view(Html(""), "", None).toString)

      val homeResponsibilitiesHyperLinkElement = document.select("#homeResponsibilitiesProtection")
      homeResponsibilitiesHyperLinkElement.text()       shouldBe "Apply for Home Responsibilities Protection"
      homeResponsibilitiesHyperLinkElement.attr("href") shouldBe
        configDecorator.nationalInsuranceHomeResponsibilitiesProtection

      val nationalInsuranceClass3HyperLinkElement = document.select("#nationalInsuranceClass3Credits")
      nationalInsuranceClass3HyperLinkElement.text()       shouldBe "Apply for National Insurance class 3 credits"
      nationalInsuranceClass3HyperLinkElement.attr("href") shouldBe
        configDecorator.nationalInsuranceClassThreeCredits

      val parentsCarersCreditsHyperLinkElement = document.select("#parentsCarersCredits")
      parentsCarersCreditsHyperLinkElement.text()       shouldBe "Apply for credits for parents and carers"
      parentsCarersCreditsHyperLinkElement.attr("href") shouldBe configDecorator.creditsForParentsAndCarers

      val nationalInsuranceClass2HyperLinkElement = document.select("#class2NationalInsuranceContributions")
      nationalInsuranceClass2HyperLinkElement
        .text()                                            shouldBe "Apply for refund of Class 2 National Insurance contributions"
      nationalInsuranceClass2HyperLinkElement.attr("href") shouldBe
        configDecorator.nationalInsuranceClassTwoContributions

      val nationalInsuranceClass4HyperLinkElement = document.select("#class4NationalInsuranceContributions")
      nationalInsuranceClass4HyperLinkElement
        .text()                                            shouldBe "Apply for refund of Class 4 National Insurance contributions"
      nationalInsuranceClass4HyperLinkElement.attr("href") shouldBe
        configDecorator.nationalInsuranceClassFourContributions

      val nationalInsuranceClass1HyperLinkElement = document.select("#class1NationalInsuranceContributions")
      nationalInsuranceClass1HyperLinkElement
        .text()                                            shouldBe "Apply for deferment of payment of Class 1 National Insurance contributions"
      nationalInsuranceClass1HyperLinkElement.attr("href") shouldBe
        configDecorator.nationalInsuranceClassOneContributions

      val europeanHealthCareCertificateHyperLinkElement = document.select("#europeanHealthCareCertificate")
      europeanHealthCareCertificateHyperLinkElement.text()       shouldBe "Apply for a European Healthcare certificate"
      europeanHealthCareCertificateHyperLinkElement.attr("href") shouldBe
        configDecorator.applyForEuropeanHealthcareCertificate

      val workInEEUCountryHyperLinkElement = document.select("#workInAnotherEEUCountry")
      workInEEUCountryHyperLinkElement.text()       shouldBe
        "Find out about self-employment and temporary work in another country within the European Economic Area (EEU)"
      workInEEUCountryHyperLinkElement.attr("href") shouldBe
        configDecorator.selfEmploymentAndTemporaryWorkInAbroad
    }
  }
}

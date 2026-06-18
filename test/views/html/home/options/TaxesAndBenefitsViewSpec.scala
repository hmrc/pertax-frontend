/*
 * Copyright 2026 HM Revenue & Customs
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

package views.html.home.options

import config.ConfigDecorator
import controllers.bindable.Origin
import models.{MyService, OtherService}
import org.jsoup.nodes.Document
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api
import play.api.Application
import repositories.JourneyCacheRepository
import views.html.ViewSpec

class TaxesAndBenefitsViewSpec extends ViewSpec {

  implicit val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]

  lazy val page: TaxesAndBenefitsView = inject[TaxesAndBenefitsView]

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
  }

  private val payeService = MyService(
    "Pay As You Earn (PAYE)",
    Some("/paye"),
    None,
    id = Some("paye")
  )

  private val nationalInsuranceService = MyService(
    "National Insurance and State Pension",
    Some("/nisp"),
    None,
    id = Some("state-pension")
  )

  private val childBenefitService = OtherService(
    "Child Benefit",
    "/child-benefit",
    id = Some("child-benefit")
  )

  private val marriageAllowanceService = OtherService(
    "Marriage Allowance",
    "/marriage-allowance",
    id = Some("marriage-allowance")
  )

  "TaxesAndBenefitsView" must {

    "render the 'Your current HMRC Online taxes and benefits' heading when myServices are present" in {
      val document: Document = asDocument(page(Seq(payeService), Seq.empty).toString)
      document.select("h2#my-services-heading").text() mustBe messages("label.taxes_and_benefits_subheading")
    }

    "render the 'Other taxes and benefits' heading when otherServices are present" in {
      val document: Document = asDocument(page(Seq.empty, Seq(childBenefitService)).toString)
      document.select("h2#other-services-heading").text() mustBe messages("label.other_taxes_and_benefits_heading")
    }

    "render both section headings when both myServices and otherServices are present" in {
      val document: Document = asDocument(page(Seq(payeService), Seq(childBenefitService)).toString)
      document.select("h2#my-services-heading").text() mustBe messages("label.taxes_and_benefits_subheading")
      document.select("h2#other-services-heading").text() mustBe messages("label.other_taxes_and_benefits_heading")
    }

    "render myService cards with correct links" in {
      val document: Document = asDocument(page(Seq(payeService, nationalInsuranceService), Seq.empty).toString)
      val cards = document.select("div.hmrc-card")
      cards.size mustBe 2
      assertContainsLink(document, "Pay As You Earn (PAYE)", "/paye")
      assertContainsLink(document, "National Insurance and State Pension", "/nisp")
    }

    "render otherService cards with correct links" in {
      val document: Document = asDocument(page(Seq.empty, Seq(childBenefitService, marriageAllowanceService)).toString)
      val cards = document.select("div.hmrc-card")
      cards.size mustBe 2
      assertContainsLink(document, "Child Benefit", "/child-benefit")
      assertContainsLink(document, "Marriage Allowance", "/marriage-allowance")
    }

    "not render 'my services' heading when myServices is empty" in {
      val document: Document = asDocument(page(Seq.empty, Seq(childBenefitService)).toString)
      document.select("h2#my-services-heading").size() mustBe 0
    }

    "not render 'other services' heading when otherServices is empty" in {
      val document: Document = asDocument(page(Seq(payeService), Seq.empty).toString)
      document.select("h2#other-services-heading").size() mustBe 0
    }

    "render no cards and no headings when both services are empty" in {
      val document: Document = asDocument(page(Seq.empty, Seq.empty).toString)
      document.select("h2").size() mustBe 0
      document.select("div.hmrc-card").size() mustBe 0
    }

    "skip myService entries that have no link" in {
      val noLinkService = MyService("No Link Service", None, None)
      val document: Document = asDocument(page(Seq(noLinkService, payeService), Seq.empty).toString)
      val cards = document.select("div.hmrc-card")
      cards.size mustBe 1
      assertContainsLink(document, "Pay As You Earn (PAYE)", "/paye")
    }

    "render Welsh section headings when viewed in Welsh" in {
      val welshDoc: Document = asDocument(page(Seq(payeService), Seq(childBenefitService))(welshMessages).toString)
      welshDoc.select("h2#my-services-heading").text() mustBe welshMessages("label.taxes_and_benefits_subheading")
      welshDoc.select("h2#other-services-heading").text() mustBe welshMessages("label.other_taxes_and_benefits_heading")
    }

    "use aria-labelledby on card containers referencing section headings" in {
      val document: Document = asDocument(
        page(Seq(payeService, nationalInsuranceService), Seq(childBenefitService, marriageAllowanceService)).toString
      )
      val containers = document.select("ul.hmrc-card__container")
      containers.get(0).attr("aria-labelledby") mustBe "my-services-heading"
      containers.get(1).attr("aria-labelledby") mustBe "other-services-heading"
    }
  }
}

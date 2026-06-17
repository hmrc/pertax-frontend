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
import org.jsoup.nodes.Document
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api
import play.api.Application
import repositories.JourneyCacheRepository
import views.html.ViewSpec

import scala.jdk.CollectionConverters.*

class SupportViewSpec extends ViewSpec {

  implicit val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]

  lazy val page: SupportView = inject[SupportView]

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

  "Rendering SupportView.scala.html" must {

    lazy val document: Document = asDocument(page().toString)

    "render the section header" in {
      document.select("h2.govuk-heading-m").text() mustBe messages("ptap.support.tab.cards.header")
    }

    "render all 7 support cards" in {
      document.select("div.hmrc-card").size mustBe 7
    }

    "render all card headings in English" in {
      val headings = document.select("h3.hmrc-card__heading").asScala.map(_.text).toSeq

      headings must contain(messages("ptap.support.tab.card.hmrc.online.heading"))
      headings must contain(messages("ptap.support.tab.card.paye.heading"))
      headings must contain(messages("ptap.support.tab.card.self.assessment.heading"))
      headings must contain(messages("ptap.support.tab.card.child.benefit.heading"))
      headings must contain(messages("ptap.support.tab.card.mariage.allowance.heading"))
      headings must contain(messages("ptap.support.tab.card.annual.tax.summary.heading"))
      headings must contain(messages("ptap.support.tab.card.insurance.state.pension.heading"))
    }

    "render all card headings in Welsh" in {
      val welshDoc = asDocument(page()(welshMessages).toString)
      val headings = welshDoc.select("h3.hmrc-card__heading").asScala.map(_.text).toSeq

      headings must contain(welshMessages("ptap.support.tab.card.hmrc.online.heading"))
      headings must contain(welshMessages("ptap.support.tab.card.paye.heading"))
      headings must contain(welshMessages("ptap.support.tab.card.self.assessment.heading"))
      headings must contain(welshMessages("ptap.support.tab.card.child.benefit.heading"))
      headings must contain(welshMessages("ptap.support.tab.card.mariage.allowance.heading"))
      headings must contain(welshMessages("ptap.support.tab.card.annual.tax.summary.heading"))
      headings must contain(welshMessages("ptap.support.tab.card.insurance.state.pension.heading"))
    }

    "render 17 links in total (1 internal + 16 external)" in {
      document.select("a.govuk-link").size mustBe 17
    }

    "render the internal 'understanding account' link without target=_blank" in {
      val link = document.select("a[href='/support/understanding-your-account']")
      link.size mustBe 1
      link.hasAttr("target") mustBe false
    }

    "render all external links with target=_blank" in {
      document.select("a.govuk-link[target=_blank]").size mustBe 16
    }

    "render HMRC Online card link text and hrefs" in {
      assertContainsLink(
        document,
        messages("ptap.support.tab.card.hmrc.online.link.understanding.account"),
        "/support/understanding-your-account"
      )
      assertContainsLink(
        document,
        messages("ptap.support.tab.card.hmrc.online.link.extra.support"),
        "https://www.gov.uk/get-help-hmrc-extra-support"
      )
      assertContainsLink(
        document,
        messages("ptap.support.tab.card.hmrc.online.link.technical.support"),
        "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/online-services-helpdesk"
      )
      assertContainsLink(
        document,
        messages("ptap.support.tab.card.hmrc.online.link.help.friends"),
        "https://www.gov.uk/help-friends-family-tax"
      )
      assertContainsLink(
        document,
        messages("ptap.support.tab.card.hmrc.online.link.chat"),
        "https://www.tax.service.gov.uk/ask-hmrc/chat/online-services-helpdesk"
      )
      assertContainsLink(
        document,
        messages("ptap.support.tab.card.hmrc.online.link.change.details"),
        "https://www.gov.uk/tell-hmrc-change-of-details"
      )
    }

    "render PAYE card link text and hrefs" in {
      assertContainsLink(
        document,
        messages("ptap.support.tab.card.paye.link.guidance"),
        "https://www.gov.uk/browse/tax/income-tax"
      )
      assertContainsLink(
        document,
        messages("ptap.support.tab.card.paye.link.enquiries"),
        "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/income-tax-enquiries-for-individuals-pensioners-and-employees"
      )
    }

    "render Self Assessment card link text and hrefs" in {
      assertContainsLink(
        document,
        messages("ptap.support.tab.card.self.assessment.link.guidance"),
        "https://www.gov.uk/browse/tax/self-assessment"
      )
      assertContainsLink(
        document,
        messages("ptap.support.tab.card.self.assessment.link.enquiries"),
        "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/self-assessment"
      )
    }

    "render Child Benefit card link text and hrefs" in {
      assertContainsLink(
        document,
        messages("ptap.support.tab.card.child.benefit.link.guidance"),
        "https://www.gov.uk/child-benefit"
      )
      assertContainsLink(
        document,
        messages("ptap.support.tab.card.child.benefit.link.enquiries"),
        "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/child-benefit"
      )
    }

    "render Marriage Allowance card link text and hrefs" in {
      assertContainsLink(
        document,
        messages("ptap.support.tab.card.mariage.allowance.link.guidance"),
        "https://www.gov.uk/marriage-allowance"
      )
      assertContainsLink(
        document,
        messages("ptap.support.tab.card.mariage.allowance.link.enquiries"),
        "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/income-tax-enquiries-for-individuals-pensioners-and-employees"
      )
    }

    "render Annual Tax Summary card link text and href" in
      assertContainsLink(
        document,
        messages("ptap.support.tab.card.annual.tax.summary.link.guidance"),
        "https://www.gov.uk/guidance/annual-tax-summary"
      )

    "render National Insurance and State Pension card link text and hrefs" in {
      assertContainsLink(
        document,
        messages("ptap.support.tab.card.insurance.state.pension.link.ni"),
        "https://www.gov.uk/national-insurance"
      )
      assertContainsLink(
        document,
        messages("ptap.support.tab.card.insurance.state.pension.link.enquiries"),
        "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/income-tax-enquiries-for-individuals-pensioners-and-employees"
      )
    }

    /*    "render link text from messages in Welsh" in {
      val welshDoc = asDocument(page()(welshMessages).toString)
      assertContainsLink(
        welshDoc,
        welshMessages("ptap.support.tab.card.hmrc.online.link.understanding.account"),
        "/support/understanding-your-account"
      )
      assertContainsLink(
        welshDoc,
        welshMessages("ptap.support.tab.card.paye.link.guidance"),
        "https://www.gov.uk/browse/tax/income-tax"
      )
    }*/

    "not hardcode English link text when rendering in Welsh" in {
      val welshDoc = asDocument(page()(welshMessages).toString)
      assertNotContainText(welshDoc, "Understanding your HMRC Online account")
      assertNotContainText(welshDoc, "Income Tax guidance")
    }

    "not escape HTML characters in card bodies" in {
      document.html must not contain "&amp;"
      document.html must not contain "&lt;"
      document.html must not contain "&gt;"
    }
  }
}

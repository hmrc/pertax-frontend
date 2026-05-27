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

package views

import play.twirl.api.Html
import testUtils.A11ySpec
import uk.gov.hmrc.scalatestaccessibilitylinter.domain.OutputFormat
import viewmodels.{CardContainerModel, HMRCCardModel}

class CardContainerA11ySpec extends A11ySpec {

  private lazy val cardContainer = app.injector.instanceOf[views.html.tags.CardContainer]

  private val emptyView: Html =
    Html("""<p class="govuk-body">No cards available.</p>""")

  private val payeCard: HMRCCardModel =
    HMRCCardModel(
      Html(
        """<h3 class="hmrc-card__heading">
          |  <a href="/pay-as-you-earn">Pay As You Earn (PAYE)<span class="hmrc-card__chevron" aria-hidden="true"></span></a>
          |</h3>
          |<p class="govuk-body">View and update your PAYE details.</p>""".stripMargin
      )
    )

  private val selfAssessmentCard: HMRCCardModel =
    HMRCCardModel(
      Html(
        """<h3 class="hmrc-card__heading">
          |  <a href="/self-assessment">Self Assessment<span class="hmrc-card__chevron" aria-hidden="true"></span></a>
          |</h3>
          |<p class="govuk-body">Check your Self Assessment tax return and payments.</p>""".stripMargin
      )
    )

  private def fullPage(componentHtml: Html, beforeComponent: Html = Html("")): String =
    s"""<!doctype html>
       |<html lang="en">
       |  <head>
       |    <title>Card container accessibility test</title>
       |  </head>
       |  <body>
       |    <main>
       |      <h1>Card container accessibility test</h1>
       |      ${beforeComponent.body}
       |      ${componentHtml.body}
       |    </main>
       |  </body>
       |</html>""".stripMargin

  "CardContainer" must {
    "pass accessibility checks for the empty state" in {
      val html = fullPage(
        cardContainer(
          CardContainerModel(
            emptyView = emptyView,
            header = Some("Empty state"),
            headerId = Some("empty-card-container-heading")
          )
        )
      )

      html must passAccessibilityChecks(OutputFormat.Verbose)
    }

    "pass accessibility checks for multiple cards labelled by a visible header" in {
      val html = fullPage(
        cardContainer(
          CardContainerModel(
            emptyView = emptyView,
            header = Some("PAYE services"),
            headerId = Some("paye-services-heading"),
            cards = Seq(payeCard, selfAssessmentCard)
          )
        )
      )

      html must passAccessibilityChecks(OutputFormat.Verbose)
    }

    "pass accessibility checks for a single card" in {
      val html = fullPage(
        cardContainer(
          CardContainerModel(
            emptyView = emptyView,
            header = Some("PAYE service"),
            headerId = Some("paye-service-heading"),
            cards = Seq(payeCard)
          )
        )
      )

      html must passAccessibilityChecks(OutputFormat.Verbose)
    }

    "pass accessibility checks for multiple cards labelled by aria-label" in {
      val html = fullPage(
        cardContainer(
          CardContainerModel(
            emptyView = emptyView,
            listAriaLabel = Some("Tax service cards"),
            cards = Seq(payeCard, selfAssessmentCard)
          )
        ),
        beforeComponent = Html("""<h2>Tax services</h2>""")
      )

      html must passAccessibilityChecks(OutputFormat.Verbose)
    }
  }
}

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

package views.html.components

import org.jsoup.nodes.Document
import org.scalatest.matchers.must.Matchers
import views.html.ViewSpec
import viewmodels.{SecondaryNavModel, TabModel}

class SecondaryNavSpec extends ViewSpec with Matchers {

  "SecondaryNav component" must {

    "render the correct HTML structure" in {
      val model = SecondaryNavModel(
        items = Seq(
          TabModel(text = "Overview", href = "/overview", current = true),
          TabModel(text = "Staff list", href = "/staff"),
          TabModel(text = "Projects", href = "/projects")
        )
      )

      val doc = asDocument(views.html.components.SecondaryNav(model).toString)

      doc.select("nav.x-govuk-secondary-navigation").size() mustBe 1
      doc.select("ul.x-govuk-secondary-navigation__list").size() mustBe 1
      doc.select("li.x-govuk-secondary-navigation__list-item").size() mustBe 3
    }

    "render the correct text for each navigation item" in {
      val model = SecondaryNavModel(
        items = Seq(
          TabModel(text = "Overview", href = "/overview", current = true),
          TabModel(text = "Staff list", href = "/staff"),
          TabModel(text = "Projects", href = "/projects")
        )
      )

      val doc = asDocument(views.html.components.SecondaryNav(model).toString)

      doc.text() must include("Overview")
      doc.text() must include("Staff list")
      doc.text() must include("Projects")
    }

    "render the correct href for each navigation item" in {
      val model = SecondaryNavModel(
        items = Seq(
          TabModel(text = "Overview", href = "/overview", current = true),
          TabModel(text = "Staff list", href = "/staff"),
          TabModel(text = "Projects", href = "/projects")
        )
      )

      val doc = asDocument(views.html.components.SecondaryNav(model).toString)

      doc.select("a[href='/overview']").size() mustBe 1
      doc.select("a[href='/staff']").size() mustBe 1
      doc.select("a[href='/projects']").size() mustBe 1
    }

    "add the current class to the current navigation item" in {
      val model = SecondaryNavModel(
        items = Seq(
          TabModel(text = "Overview", href = "/overview", current = true),
          TabModel(text = "Staff list", href = "/staff"),
          TabModel(text = "Projects", href = "/projects")
        )
      )

      val doc = asDocument(views.html.components.SecondaryNav(model).toString)

      doc.select("li.x-govuk-secondary-navigation__list-item--current").size() mustBe 1
      doc.select("li.x-govuk-secondary-navigation__list-item--current").text() must include("Overview")
    }

    "add aria-current='page' to the current navigation link" in {
      val model = SecondaryNavModel(
        items = Seq(
          TabModel(text = "Overview", href = "/overview", current = true),
          TabModel(text = "Staff list", href = "/staff"),
          TabModel(text = "Projects", href = "/projects")
        )
      )

      val doc = asDocument(views.html.components.SecondaryNav(model).toString)

      val currentLink = doc.select("li.x-govuk-secondary-navigation__list-item--current a").first()
      currentLink.attr("aria-current") mustBe "page"
    }

    "not add aria-current to non-current navigation links" in {
      val model = SecondaryNavModel(
        items = Seq(
          TabModel(text = "Overview", href = "/overview", current = true),
          TabModel(text = "Staff list", href = "/staff"),
          TabModel(text = "Projects", href = "/projects")
        )
      )

      val doc = asDocument(views.html.components.SecondaryNav(model).toString)

      val nonCurrentLinks = doc.select("li:not(.x-govuk-secondary-navigation__list-item--current) a")
      nonCurrentLinks.forEach { link =>
        link.hasAttr("aria-current") mustBe false
      }
    }

    "display notification count when present" in {
      val model = SecondaryNavModel(
        items = Seq(
          TabModel(text = "Your tasks", href = "/tasks", current = true, notificationCount = Some(1)),
          TabModel(text = "Recent activity", href = "/activity"),
          TabModel(text = "Taxes and benefits", href = "/taxes")
        )
      )

      val doc = asDocument(views.html.components.SecondaryNav(model).toString)

      doc.select(".x-govuk-secondary-navigation__badge").size() mustBe 1
      doc.select(".x-govuk-secondary-navigation__badge").text() mustBe "1"
    }

    "not display notification count when not present" in {
      val model = SecondaryNavModel(
        items = Seq(
          TabModel(text = "Your tasks", href = "/tasks", current = true),
          TabModel(text = "Recent activity", href = "/activity"),
          TabModel(text = "Taxes and benefits", href = "/taxes")
        )
      )

      val doc = asDocument(views.html.components.SecondaryNav(model).toString)

      doc.select(".x-govuk-secondary-navigation__badge").size() mustBe 0
    }

    "style notification count with correct CSS class" in {
      val model = SecondaryNavModel(
        items = Seq(
          TabModel(text = "Your tasks", href = "/tasks", current = true, notificationCount = Some(1))
        )
      )

      val doc = asDocument(views.html.components.SecondaryNav(model).toString)

      val notificationCount = doc.select(".x-govuk-secondary-navigation__badge").first()
      notificationCount.hasClass("x-govuk-secondary-navigation__badge") mustBe true
    }

    "notification badge displays the count correctly" in {
      val model = SecondaryNavModel(
        items = Seq(
          TabModel(text = "Your tasks", href = "/tasks", current = true, notificationCount = Some(5))
        )
      )

      val doc = asDocument(views.html.components.SecondaryNav(model).toString)

      val notificationCount = doc.select(".x-govuk-secondary-navigation__badge").first()
      notificationCount.text() mustBe "5"
    }

    "use aria-label when labelledBy is not provided" in {
      val model = SecondaryNavModel(
        items = Seq(
          TabModel(text = "Overview", href = "/overview", current = true)
        ),
        visuallyHiddenTitle = "Secondary menu"
      )

      val doc = asDocument(views.html.components.SecondaryNav(model).toString)

      doc.select("nav").first().attr("aria-label") mustBe "Secondary menu"
      doc.select("nav").first().hasAttr("aria-labelledby") mustBe false
    }

    "use aria-labelledby when labelledBy is provided" in {
      val model = SecondaryNavModel(
        items = Seq(
          TabModel(text = "Overview", href = "/overview", current = true)
        ),
        labelledBy = Some("section-heading")
      )

      val doc = asDocument(views.html.components.SecondaryNav(model).toString)

      doc.select("nav").first().attr("aria-labelledby") mustBe "section-heading"
      doc.select("nav").first().hasAttr("aria-label") mustBe false
    }

    "add custom classes to the navigation container" in {
      val model = SecondaryNavModel(
        items = Seq(
          TabModel(text = "Overview", href = "/overview", current = true)
        ),
        classes = Some("custom-class another-class")
      )

      val doc = asDocument(views.html.components.SecondaryNav(model).toString)

      val nav = doc.select("nav").first()
      nav.attr("class") must include("custom-class")
      nav.attr("class") must include("another-class")
    }

    "add custom attributes to the navigation container" in {
      val model = SecondaryNavModel(
        items = Seq(
          TabModel(text = "Overview", href = "/overview", current = true)
        ),
        attributes = Map("data-test" -> "value", "data-id" -> "123")
      )

      val doc = asDocument(views.html.components.SecondaryNav(model).toString)

      val nav = doc.select("nav").first()
      nav.attr("data-test") mustBe "value"
      nav.attr("data-id") mustBe "123"
    }

    "escape custom attribute values to prevent XSS" in {
      val model = SecondaryNavModel(
        items = Seq(
          TabModel(text = "Overview", href = "/overview", current = true)
        ),
        attributes = Map("data-test" -> "value\" onclick=\"alert(1)")
      )

      val doc  = asDocument(views.html.components.SecondaryNav(model).toString)
      val html = doc.select("nav").first().outerHtml()

      // Verify the quotes are escaped as HTML entities in the HTML source
      html must include("&quot;")

      // Verify the attribute value is properly escaped when retrieved via JSoup
      // JSoup unescapes entities when reading attributes, so we check the raw HTML
      html must include("data-test=\"value&quot; onclick=&quot;alert(1)\"")

      // Verify the attribute value does NOT contain unescaped quotes that would allow XSS
      html must not include "data-test=\"value\" onclick=\"alert(1)\""
    }

    "add custom classes to individual navigation items" in {
      val model = SecondaryNavModel(
        items = Seq(
          TabModel(text = "Overview", href = "/overview", current = true, classes = Some("custom-item-class"))
        )
      )

      val doc = asDocument(views.html.components.SecondaryNav(model).toString)

      val listItem = doc.select("li.x-govuk-secondary-navigation__list-item").first()
      listItem.attr("class") must include("custom-item-class")
    }

    "render multiple notification counts on different items" in {
      val model = SecondaryNavModel(
        items = Seq(
          TabModel(text = "Your tasks", href = "/tasks", current = true, notificationCount = Some(1)),
          TabModel(text = "Messages", href = "/messages", notificationCount = Some(5)),
          TabModel(text = "Recent activity", href = "/activity")
        )
      )

      val doc = asDocument(views.html.components.SecondaryNav(model).toString)

      doc.select(".x-govuk-secondary-navigation__badge").size() mustBe 2
    }
  }
}

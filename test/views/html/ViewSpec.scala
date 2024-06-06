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

package views.html

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.Assertion
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n._
import testUtils.BaseSpec

trait ViewSpec extends BaseSpec with GuiceOneAppPerSuite {

  def hasLink(document: Document, content: String): Assertion =
    document.getElementsMatchingText(content).hasAttr("href") mustBe true

  implicit lazy val messageProvider: MessagesProvider = inject[MessagesProvider]
  implicit lazy val messages: Messages                = MessagesImpl(Lang("en"), messagesApi)
  lazy val welshMessages: Messages                    = MessagesImpl(Lang("cy"), messagesApi)

  def assertContainsText(doc: Document, text: String): Assertion =
    assert(doc.toString.contains(text), "\n\ntext " + text + " was not rendered on the page.\n")

  def assertNotContainText(doc: Document, text: String): Assertion =
    assert(!doc.toString.contains(text), "\n\ntext " + text + " was rendered on the page.\n")

  def assertContainsLink(doc: Document, text: String, href: String): Assertion =
    assert(
      doc.getElementsContainingText(text).attr("href").contains(href),
      s"\n\nLink $href was not rendered on the page\n"
    )

  def assertNotContainLink(doc: Document, text: String, href: String): Assertion =
    assert(
      !doc.getElementsContainingText(text).attr("href").contains(href),
      s"\n\nLink $href was rendered on the page\n"
    )

  def asDocument(page: String): Document = Jsoup.parse(page)

}

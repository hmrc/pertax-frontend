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

package models

import play.twirl.api.Html

enum CardType:
  case BasicCard
  case BasicCardWithDueDate
  case SectionCard
  case NoLinkCard
  case NewTabCard

class Heading(val text: String, val url: Option[String])
class Body(val content: Html)
class Tag(val content: String, val classes: String)
class CardHint(val content: Option[Html], val tag: Option[Tag]) {
  def render_tag: Html =
    this.tag match
      case Some(tag) => Html(s"""
        <span class="${tag.classes}">
          ${tag.content}
        </span>
        """)
      case None      => Html("")
}
trait hasBody {
  val body: Body
}
trait hasHint {
  val hint: CardHint
}
sealed abstract class HmrcCardModel(val heading: Heading) {
  def render: Html
}
case class BasicCard(override val heading: Heading) extends HmrcCardModel(heading) {
  def render: Html =
    Html(s"""
        <div class="hmrc-card">
          <div class="hmrc-card__heading">
            <a href='${this.heading.url.getOrElse("")}'>
              ${this.heading.text}
              <span class="hmrc-card__chevron" aria-hidden="true"></span>
            </a>
          </div>
        </div>""")
}
case class BasicCardWithDueDate(override val heading: Heading, hint: CardHint) extends HmrcCardModel(heading), hasHint {
  def render: Html =
    Html(s"""
        <div class="hmrc-card">
          <h3 class="hmrc-card__heading">
            <a href='${this.heading.url.getOrElse("")}'>
              ${this.heading.text}
              <span class="hmrc-card__chevron" aria-hidden="true"></span>
            </a>
          </h3>
          <p class="govuk-body">
            ${this.hint.tag match {
        case Some(_) => this.hint.render_tag
        case None    => Html("")
      }}
          </p>
        </div>""")
}
case class SectionCard(override val heading: Heading, body: Body, hint: CardHint)
    extends HmrcCardModel(heading),
      hasBody,
      hasHint {
  def render: Html =
    Html(s"""
        <div class="hmrc-card">
          <h3 class="hmrc-card__heading">
            <a href='${this.heading.url.getOrElse("")}'>
              ${this.heading.text}
              <span class="hmrc-card__chevron" aria-hidden="true"></span>
            </a>
          </h3>
          ${this.body.content}
          ${this.hint.content match {
        case Some(value) => value
        case None        => Html("")
      }}
        </div>""")
}
case class NoLinkCard(override val heading: Heading, body: Body) extends HmrcCardModel(heading), hasBody {
  def render: Html =
    Html(s"""
        <div class="hmrc-card">
          <h3 class="hmrc-card__heading">
            ${this.heading.text}
          </h3>
            ${this.body.content} 
        </div>""")
}

case class NewTabCard(override val heading: Heading) extends HmrcCardModel(heading) {
  def render: Html =
    Html(s"""
        <div class="hmrc-card">
          <h3 class="hmrc-card__heading">
            <a href='${this.heading.url.getOrElse("")}'>
              ${this.heading.text}
              <span class="hmrc-card__chevron" aria-hidden="true"></span><br/>
              <span class="govuk-!-font-weight-regular">(opens in new tab)</span>
            </a>
          </h3>
        </div>""")
}

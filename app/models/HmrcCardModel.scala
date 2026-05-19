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
class CardHint(val content: Option[String], val tag: Option[Tag]) {
  def render_tag: Html =
    this.tag match
      case Some(tag) => Html(s"""
        <strong class="${tag.classes}">
          ${tag.content}
        </strong>
        """)
      case None      => Html("")
}

case class HmrcCardModel(
  cardType: CardType,
  heading: Heading,
  body: Option[Body],
  hint: Option[CardHint]
) {
  def render: Html =
    this.cardType match
      case CardType.BasicCard            =>
        Html(s"""
        <div class="hmrc-card">
          <div class="hmrc-card__heading">
            <a href='${this.heading.url.getOrElse("")}'>
              ${this.heading.text}
              <span class="hmrc-card__chevron" aria-hidden="true"></span>
            </a>
          </div>
        </div>""")
      case CardType.BasicCardWithDueDate =>
        Html(s"""
        <div class="hmrc-card">
          <h3 class="hmrc-card__heading">
            <a href='${this.heading.url.getOrElse("")}'>
              ${this.heading.text}
              <span class="hmrc-card__chevron" aria-hidden="true"></span>
            </a>
          </h3>
          <p class="govuk-body">
          ${this.hint match
            case Some(h) => h.render_tag
            case None    => Html("")
          }
          </p>
        </div>""")
      case CardType.SectionCard          =>
        Html(s"""
        <div class="hmrc-card">
          <h3 class="hmrc-card__heading">
            <a href='${this.heading.url.getOrElse("")}'>
              ${this.heading.text}
              <span class="hmrc-card__chevron" aria-hidden="true"></span>
            </a>
          </h3>
          ${this.body match
            case Some(body) => body.content
            case None       => Html("")
          }
          ${this.hint match
            case Some(x) => x.render_tag
            case None    => Html("")
          }
        </div>""")
      case CardType.NoLinkCard           =>
        Html(s"""
        <div class="hmrc-card">
          <h3 class="hmrc-card__heading">
            ${this.heading.text}
          </h3>
            ${this.body match
            case Some(body) => body.content
            case None       => Html("")
          }
        </div>""")
      case CardType.NewTabCard           =>
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

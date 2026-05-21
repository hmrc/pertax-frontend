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

class Heading(val text: String, val url: Option[String], val opensNewTab: Boolean)
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
case class HmrcCardModel(cardType: CardType, heading: Heading, body: Option[Body], hint: Option[CardHint])

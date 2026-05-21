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

class Heading(val text: Html, val url: Option[String], val opensNewTab: Boolean)
class Body(val content: Html)
class Tag(val content: Html, val classes: String) {
  val valid_classes: List[String] = List[String](
    "govuk-tag",
    "govuk-tag--grey",
    "govuk-tag--green",
    "govuk-tag--teal",
    "govuk-tag--blue",
    "govuk-tag--purple",
    "govuk-tag--magenta",
    "govuk-tag--red",
    "govuk-tag--orange",
    "govuk-tag--yellow"
  )
  if (!classes.split("\\s+").forall(valid_classes.contains)) {
    throw new Exception("Invalid or malformed class supplied to tag. Please see GDS for defined tags.")
  }
}
class CardHint(val content: Option[Html], val tag: Option[Tag])

case class HmrcCardModel(cardType: CardType, heading: Heading, body: Option[Body], hint: Option[CardHint])

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

enum TagColour(val style: String):
  case Default extends TagColour("govuk-tag")
  case Grey extends TagColour("govuk-tag govuk-tag--grey")
  case Green extends TagColour("govuk-tag govuk-tag--green")
  case Teal extends TagColour("govuk-tag govuk-tag--teal")
  case Blue extends TagColour("govuk-tag govuk-tag--blue")
  case Purple extends TagColour("govuk-tag govuk-tag--purple")
  case Magenta extends TagColour("govuk-tag govuk-tag--magenta")
  case Red extends TagColour("govuk-tag govuk-tag--red")
  case Orange extends TagColour("govuk-tag govuk-tag--orange")
  case Yellow extends TagColour("govuk-tag govuk-tag--yellow")

class CardHeading(val text: String, val url: Option[String], val opensNewTab: Boolean = false)
class CardBody(val content: Html)
class CardHint(val content: Html, val tag_colour: Option[TagColour])

case class HmrcCardModel(cardType: CardType, heading: CardHeading, body: Option[CardBody], hint: Option[CardHint]) {
  cardType match
    case CardType.BasicCard            =>
      if (!heading.url.isDefined || body.isDefined || hint.isDefined) {
        throw new Exception("Invalid Parameters: BasicCard requires CardType and Heading with a url only.")
      }
    case CardType.BasicCardWithDueDate =>
      if (!heading.url.isDefined || body.isDefined || !hint.isDefined || !hint.get.tag_colour.isDefined) {
        throw new Exception(
          "Invalid Parameters: BasicCardWithDueDate requires CardType Heading and CardHint only."
        )
      }
    case CardType.SectionCard          =>
      if (!heading.url.isDefined || !body.isDefined || !hint.isDefined) {
        throw new Exception("Invalid Parameters: SectionCard requires CardType, Body and CardHint.")
      }
    case CardType.NoLinkCard           =>
      if (heading.url.isDefined || !body.isDefined || hint.isDefined) {
        throw new Exception("Invalid Parameters: NoLink Card requres CardType, Heading without a url and Body only.")
      }
    case CardType.NewTabCard           =>
      if (!heading.url.isDefined || body.isDefined || hint.isDefined) {
        throw new Exception("Invalid Parameters: NewTabCard requires CardType and Heading with a url only.")
      }

}

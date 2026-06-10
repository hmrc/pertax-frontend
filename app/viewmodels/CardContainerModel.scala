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

package viewmodels

import models.HmrcCardModel
import play.twirl.api.{Html, HtmlFormat}

final case class CardContainerModel(
  emptyView: Html,
  header: Option[CardContainerModel.Header] = None,
  cards: Seq[HmrcCardModel] = Seq.empty,
  headingLevel: String = "h2",
  listAriaLabel: Option[String] = None,
  headerId: Option[String] = None
) {
  val normalizedHeadingLevel: String          = headingLevel.trim.toLowerCase
  val normalizedHeader: Option[Html]          = header.flatMap(CardContainerModel.normalizeHeader)
  val normalizedListAriaLabel: Option[String] = listAriaLabel.map(_.trim).filter(_.nonEmpty)
  val normalizedHeaderId: Option[String]      = headerId.map(_.trim).filter(_.nonEmpty)

  require(
    CardContainerModel.ValidHeadingLevels.contains(normalizedHeadingLevel),
    s"Invalid heading level: $headingLevel. Must be h1-h6."
  )

  require(
    cards.length < 2 || normalizedHeader.isDefined || normalizedListAriaLabel.isDefined,
    "CardContainerModel with 2+ cards must have either header or listAriaLabel for accessibility"
  )
}

object CardContainerModel {
  type Header = String | Html

  private val ValidHeadingLevels = Set("h1", "h2", "h3", "h4", "h5", "h6")

  private def normalizeHeader(header: Header): Option[Html] =
    header match {
      case text: String if text.trim.nonEmpty    => Some(HtmlFormat.escape(text.trim))
      case html: Html if html.body.trim.nonEmpty => Some(html)
      case _                                     => None
    }
}

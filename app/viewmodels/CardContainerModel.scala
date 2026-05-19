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

import play.twirl.api.Html

final case class CardContainerModel(
  emptyView: Html,
  header: Option[String] = None,
  cards: Seq[HMRCCardModel] = Seq.empty,
  headingLevel: String = "h2",
  listAriaLabel: Option[String] = None,
  headerId: Option[String] = None
) {
  val normalizedHeadingLevel: String          = headingLevel.trim.toLowerCase
  val normalizedHeader: Option[String]        = header.map(_.trim).filter(_.nonEmpty)
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
  private val ValidHeadingLevels = Set("h1", "h2", "h3", "h4", "h5", "h6")
}

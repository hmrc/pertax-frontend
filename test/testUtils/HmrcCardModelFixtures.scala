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

package testUtils

import models.{CardHeading, CardType, HmrcCardModel}

object HmrcCardModelFixtures {

  val taskCard1: HmrcCardModel = HmrcCardModel(
    cardType = CardType.BasicCard,
    heading = CardHeading(text = "You owe tax for 2023-24", url = Some("/tax-calc"), opensNewTab = false),
    body = None,
    hint = None
  )

  val taskCard2: HmrcCardModel = HmrcCardModel(
    cardType = CardType.BasicCard,
    heading = CardHeading(text = "HMRC owes you a refund for 2022-23", url = Some("/tax-calc"), opensNewTab = false),
    body = None,
    hint = None
  )

  val activityCard1: HmrcCardModel = HmrcCardModel(
    cardType = CardType.BasicCard,
    heading = CardHeading(text = "Tax code change", url = Some("/check-income-tax"), opensNewTab = false),
    body = None,
    hint = None
  )

  val activityCard2: HmrcCardModel = HmrcCardModel(
    cardType = CardType.BasicCard,
    heading = CardHeading(text = "Payment received", url = Some("/check-income-tax"), opensNewTab = false),
    body = None,
    hint = None
  )

  val taskCards: Seq[HmrcCardModel] = Seq(taskCard1, taskCard2)

  val activityCards: Seq[HmrcCardModel] = Seq(activityCard1, activityCard2)
}

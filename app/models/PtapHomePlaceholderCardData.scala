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

object PtapHomePlaceholderCardData {

  val taskCards: List[HmrcCardModel] = List(
    HmrcCardModel(
      cardType = CardType.BasicCard,
      heading = CardHeading(
        text = "Check your PAYE Income Tax estimate",
        url = Some(controllers.routes.RedirectToPayeController.redirectToPaye.url)
      ),
      body = None,
      hint = None
    ),
    HmrcCardModel(
      cardType = CardType.BasicCard,
      heading = CardHeading(
        text = "Complete your Self Assessment tax return",
        url = Some(controllers.routes.SelfAssessmentController.handleSelfAssessment.url)
      ),
      body = None,
      hint = None
    ),
    HmrcCardModel(
      cardType = CardType.BasicCard,
      heading = CardHeading(
        text = "Update your personal details",
        url = Some(controllers.address.routes.PersonalDetailsController.onPageLoad.url)
      ),
      body = None,
      hint = None
    )
  )

  val activityCards: List[HmrcCardModel] = List(
    HmrcCardModel(
      cardType = CardType.BasicCard,
      heading = CardHeading(
        text = "View your National Insurance record",
        url = Some(controllers.interstitials.routes.InterstitialController.displayNationalInsurance.url)
      ),
      body = None,
      hint = None
    ),
    HmrcCardModel(
      cardType = CardType.BasicCard,
      heading = CardHeading(
        text = "Check your State Pension",
        url = Some(controllers.interstitials.routes.InterstitialController.displayNISP.url)
      ),
      body = None,
      hint = None
    )
  )

  val emptyCards: List[HmrcCardModel] = List.empty
}

/*
 * Copyright 2025 HM Revenue & Customs
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

package models.admin

import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlagName

object AllFeatureFlags {
  val list: List[FeatureFlagName] = List(
    AddressTaxCreditsBrokerCallToggle,
    TaxcalcToggle,
    TaxComponentsToggle,
    RlsInterruptToggle,
    PaperlessInterruptToggle,
    TaxSummariesTileToggle,
    ShowOutageBannerToggle,
    AgentClientAuthorisationToggle,
    BreathingSpaceIndicatorToggle,
    AlertBannerPaperlessStatusToggle,
    GetPersonFromCitizenDetailsToggle,
    DfsDigitalFormFrontendAvailableToggle,
    AddressChangeAllowedToggle,
    VoluntaryContributionsAlertToggle
  )
}

case object AddressTaxCreditsBrokerCallToggle extends FeatureFlagName {
  override val name: String                = "address-tax-credits-broker-call"
  override val description: Option[String] = Some(
    "If enabled do not ask tax credits question but call tax-credits-broker instead"
  )
}

case object TaxcalcToggle extends FeatureFlagName {
  override val name: String                = "taxcalc"
  override val description: Option[String] = Some("Enable/disable the tile for payments and repayments")
}

case object TaxComponentsToggle extends FeatureFlagName {
  override val name: String                = "tax-components"
  override val description: Option[String] = Some("Check your income tax")
}

case object RlsInterruptToggle extends FeatureFlagName {
  override val name: String                = "rls-interrupt-toggle"
  override val description: Option[String] = Some("Enable/disable the interrupt for return letter to sender (RLS)")
}

case object PaperlessInterruptToggle extends FeatureFlagName {
  override val name: String                = "enforce-paperless-preference"
  override val description: Option[String] = Some("Enable/disable the interrupt for paperless setting")
}

case object TaxSummariesTileToggle extends FeatureFlagName {
  override val name: String                = "tax-summaries-tile"
  override val description: Option[String] = Some("Enable/disable the tile for annual tax summary")
}

case object ShowOutageBannerToggle extends FeatureFlagName {
  override val name: String                = "show-outage-banner-toggle"
  override val description: Option[String] = Some("Enable/disable the show outage banner")
}

case object AgentClientAuthorisationToggle extends FeatureFlagName {
  override val name: String                = "agent-client-authorisation-toggle"
  override val description: Option[String] = Some(
    "Enable/disable calls to agent-client-authorisation service from Profile and Setting page"
  )
}

case object BreathingSpaceIndicatorToggle extends FeatureFlagName {
  override val name: String                = "breathing-space-indicator-toggle"
  override val description: Option[String] = Some(
    "Enable/disable calls to Breathing space"
  )
}

case object GetPersonFromCitizenDetailsToggle extends FeatureFlagName {
  override val name: String                = "get-person-from-citizen-details-toggle"
  override val description: Option[String] = Some(
    "Enable/disable call to designatory-details"
  )
}

case object DfsDigitalFormFrontendAvailableToggle extends FeatureFlagName {
  override val name: String                = "dfs-digital-forms-frontend-available-toggle"
  override val description: Option[String] = Some(
    "Enable/disable indicating whether the dfs digital forms frontend is available or shuttered"
  )
}

case object AddressChangeAllowedToggle extends FeatureFlagName {
  override val name: String                = "address-change-allowed-toggle"
  override val description: Option[String] = Some(
    "Enable/disable indicating whether the address change is allowed in profile and settings page"
  )
}

case object AlertBannerPaperlessStatusToggle extends FeatureFlagName {
  override val name: String                = "alert-banner-paperless-status-toggle"
  override val description: Option[String] = Some(
    "Enable/disable paperless alerts in alert banner on the home page"
  )
}

case object VoluntaryContributionsAlertToggle extends FeatureFlagName {
  override val name: String                = "voluntary-contributions-alert-toggle"
  override val description: Option[String] = Some(
    "Enable/disable the alert banner for voluntary National Insurance contributions information"
  )
}

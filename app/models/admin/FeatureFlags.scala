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

import uk.gov.hmrc.mongoFeatureToggles.model.Environment.Environment
import uk.gov.hmrc.mongoFeatureToggles.model.{Environment, FeatureFlagName}

object AllFeatureFlags {
  val list: List[FeatureFlagName] = List(
    ShowTaxCalcTileToggle,
    TaxComponentsRetrievalToggle,
    RlsInterruptToggle,
    EnforcePaperlessPreferenceToggle,
    TaxSummariesTileToggle,
    ShowPlannedOutageBannerToggle,
    AgentClientRelationshipsToggle,
    BreathingSpaceIndicatorToggle,
    AlertBannerPaperlessStatusToggle,
    DfsFormsFrontendAvailabilityToggle,
    AddressChangeAllowedToggle,
    VoluntaryContributionsAlertToggle,
    PeakDemandBannerToggle,
    GetPersonFromCitizenDetailsToggle,
    PayeToPegaRedirectToggle,
    MDTITAdvertToggle,
    MTDUserStatusToggle,
    GetMatchingFromCitizenDetailsToggle
  )
}

case object ShowTaxCalcTileToggle extends FeatureFlagName {
  override val name: String                = "show-taxcalc-tile-toggle"
  override val description: Option[String] = Some(
    "Toggle the display of the Payments and Repayments tile (NPS/ETMP data) on the home page"
  )
  override val defaultState: Boolean       = true
}

case object TaxComponentsRetrievalToggle extends FeatureFlagName {
  override val name: String                = "tax-components-retrieval-toggle"
  override val description: Option[String] = Some(
    "Enable retrieval of tax components from TAI (NPS), used for displaying Marriage Allowance and High Income Child Benefit Charge"
  )
  override val defaultState: Boolean       = true
}

case object RlsInterruptToggle extends FeatureFlagName {
  override val name: String                = "rls-interrupt-toggle"
  override val description: Option[String] = Some(
    "Show interrupt page if a residential or correspondence address has RLS status and has not been updated"
  )
  override val defaultState: Boolean       = true
}

case object EnforcePaperlessPreferenceToggle extends FeatureFlagName {
  override val name: String                = "enforce-paperless-preference"
  override val description: Option[String] = Some(
    "Show an interrupt to prompt users to set or confirm their paperless communication preference"
  )
  override val defaultState: Boolean       = true
}

case object TaxSummariesTileToggle extends FeatureFlagName {
  override val name: String                = "tax-summaries-tile"
  override val description: Option[String] = Some(
    "Enable/disable the display of the Annual Tax Summary tile on the home page"
  )
  override val defaultState: Boolean       = true
}

case object ShowPlannedOutageBannerToggle extends FeatureFlagName {
  override val name: String                = "show-outage-banner-toggle"
  override val description: Option[String] = Some(
    "Show or hide the planned outage banner on the PTA home page. Banner content is configured via app-config and code changes"
  )
  override val lockedEnvironments          =
    Seq(Environment.Local, Environment.Staging, Environment.Qa, Environment.Production)
  override val defaultState: Boolean       = false
}

case object AgentClientRelationshipsToggle extends FeatureFlagName {
  override val name: String                = "agent-client-relationships-toggle"
  override val description: Option[String] = Some(
    "Enable/disable the check for agent-client relationships on the Profile and Settings page"
  )
  override val defaultState: Boolean       = true
}

case object BreathingSpaceIndicatorToggle extends FeatureFlagName {
  override val name: String                = "breathing-space-indicator-toggle"
  override val description: Option[String] = Some(
    "Enable/disable the call to Breathing Space service to determine protected debt period status for the user"
  )
  override val defaultState: Boolean       = true
}

case object DfsFormsFrontendAvailabilityToggle extends FeatureFlagName {
  override val name: String                = "dfs-digital-forms-frontend-available-toggle"
  override val description: Option[String] = Some(
    "Enable/disable the use of DFS digital forms frontend partials for Self Assessment, NINO, and NISP pages"
  )
  override val defaultState: Boolean       = true
}

case object AddressChangeAllowedToggle extends FeatureFlagName {
  override val name: String                = "address-change-allowed-toggle"
  override val description: Option[String] = Some(
    "Enable or disable the ability to update residential and postal addresses via the Profile and Settings pages (NPS)"
  )
  override val defaultState: Boolean       = true
}

case object AlertBannerPaperlessStatusToggle extends FeatureFlagName {
  override val name: String                = "alert-banner-paperless-status-toggle"
  override val description: Option[String] = Some(
    "Enable or disable alert banners for users with bounced or unverified email addresses based on paperless preferences"
  )
  override val defaultState: Boolean       = true
}

case object VoluntaryContributionsAlertToggle extends FeatureFlagName {
  override val name: String                         = "voluntary-contributions-alert-toggle"
  override val description: Option[String]          = Some(
    "Enable/disable the alert banner providing users with information about voluntary National Insurance contributions"
  )
  override val lockedEnvironments: Seq[Environment] = Seq(Environment.Production, Environment.Staging)
  override val defaultState: Boolean                = true
}

case object PeakDemandBannerToggle extends FeatureFlagName {
  override val name: String = "peak-demand-banner-toggle"

  override val description: Option[String] = Some(
    "Enable/disable the banner on PTA home page informing users about high-demand periods or failures affecting service availability"
  )
}

case object GetPersonFromCitizenDetailsToggle extends FeatureFlagName {
  override val name: String                = "get-person-from-citizen-details-toggle"
  override val description: Option[String] = Some(
    "Enable/disable retrieving person details from designatory-details in Citizen Details (via NPS)"
  )
  override val defaultState: Boolean       = true
}

case object GetMatchingFromCitizenDetailsToggle extends FeatureFlagName {
  override val name: String                = "get-matching-from-citizen-details-toggle"
  override val description: Option[String] = Some(
    "Enable/disable retrieving matching data from Citizen Details (via datacache/cid)"
  )
  override val defaultState: Boolean       = true
}

case object PayeToPegaRedirectToggle extends FeatureFlagName {
  override val name: String                         = "paye-pega-redirect-toggle"
  override val description: Option[String]          = Some(
    "Enable/disable redirecting PAYE tile traffic to PEGA. Used in conjunction of the config `paye.to.pega.redirect.list`"
  )
  override val lockedEnvironments: Seq[Environment] =
    Seq(Environment.Local, Environment.Staging, Environment.Qa, Environment.Production)
}

case object MDTITAdvertToggle extends FeatureFlagName {
  override val name: String                         = "mdt-it-advert-toggle"
  override val description: Option[String]          = Some(
    "Enable/disable Making Tax Digital for Income Tax Advertisement tile"
  )
  override val lockedEnvironments: Seq[Environment] =
    Seq(Environment.Local, Environment.Staging, Environment.Qa, Environment.Production)
}

case object MTDUserStatusToggle extends FeatureFlagName {
  override val name: String                         = "mdt-user-status-toggle"
  override val description: Option[String]          = Some(
    "Enable/disable calls to EACD to determine MTD user status"
  )
  override val lockedEnvironments: Seq[Environment] =
    Seq(Environment.Local, Environment.Staging, Environment.Qa, Environment.Production)
}

case object FandFBannerToggle extends FeatureFlagName {
  override val name: String                         = "fandf-banner-toggle"
  override val description: Option[String]          = Some(
    "Enable/disable the fandf expiry banner"
  )
  override val lockedEnvironments: Seq[Environment] =
    Seq(Environment.Local, Environment.Staging, Environment.Qa, Environment.Production)
}

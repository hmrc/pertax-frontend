/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.libs.json._
import play.api.mvc.PathBindable

case class FeatureFlag(
  name: FeatureFlagName,
  isEnabled: Boolean,
  description: Option[String] = None
)

object FeatureFlag {
  implicit val format: OFormat[FeatureFlag] = Json.format[FeatureFlag]
}

sealed trait FeatureFlagName {
  val description: Option[String] = None
}

object FeatureFlagName {
  implicit val writes: Writes[FeatureFlagName] = (o: FeatureFlagName) => JsString(o.toString)

  implicit val reads: Reads[FeatureFlagName] = new Reads[FeatureFlagName] {
    override def reads(json: JsValue): JsResult[FeatureFlagName] =
      allFeatureFlags
        .find(flag => JsString(flag.toString) == json)
        .map(JsSuccess(_))
        .getOrElse(JsError(s"Unknown FeatureFlagName `${json.toString}`"))
  }

  val mongoReads: Reads[FeatureFlagName] = new Reads[FeatureFlagName] {
    override def reads(json: JsValue): JsResult[FeatureFlagName] =
      allFeatureFlags
        .find(flag => JsString(flag.toString) == json)
        .map(JsSuccess(_))
        .getOrElse(JsSuccess(DeletedToggle(json.as[String])))
  }

  implicit val formats: Format[FeatureFlagName] =
    Format(mongoReads, writes)

  implicit def pathBindable: PathBindable[FeatureFlagName] = new PathBindable[FeatureFlagName] {

    override def bind(key: String, value: String): Either[String, FeatureFlagName] =
      JsString(value).validate[FeatureFlagName] match {
        case JsSuccess(name, _) =>
          Right(name)
        case _                  =>
          Left(s"The feature flag `$value` does not exist")
      }

    override def unbind(key: String, value: FeatureFlagName): String =
      value.toString
  }

  val allFeatureFlags =
    List(
      AddressTaxCreditsBrokerCallToggle,
      ItsAdvertisementMessageToggle,
      NationalInsuranceTileToggle,
      PaperlessInterruptToggle,
      RlsInterruptToggle,
      SingleAccountCheckToggle,
      TaxcalcMakePaymentLinkToggle,
      TaxcalcToggle,
      TaxComponentsToggle,
      TaxSummariesTileToggle,
      AppleSaveAndViewNIToggle,
      NpsShutteringToggle,
      NpsOutageToggle,
      PertaxBackendToggle,
      SCAWrapperToggle,
      HmrcAccountToggle,
      AlertBannerPaperlessStatusToggle
    )
}

case object AddressTaxCreditsBrokerCallToggle extends FeatureFlagName {
  override def toString: String = "address-tax-credits-broker-call"

  override val description: Option[String] = Some(
    "If enabled do not ask tax credits question but call tax-credits-broker instead"
  )
}

case object TaxcalcToggle extends FeatureFlagName {
  override def toString: String = "taxcalc"

  override val description: Option[String] = Some("Enable/disable the tile for payments and repayments")
}

case object NationalInsuranceTileToggle extends FeatureFlagName {
  override def toString: String = "national-insurance-tile"

  override val description: Option[String] = Some("Enable/disable the tile for check your National Insurance")
}

case object ItsAdvertisementMessageToggle extends FeatureFlagName {
  override def toString: String = "itsa-advertisement-message"

  override val description: Option[String] = Some("Enable/disable the advertisement message for ITSA")
}

case object TaxComponentsToggle extends FeatureFlagName {
  override def toString: String            = "tax-components"
  override val description: Option[String] = Some("Check your income tax")
}

case object RlsInterruptToggle extends FeatureFlagName {
  override def toString: String            = "rls-interrupt-toggle"
  override val description: Option[String] = Some("Enable/disable the interrupt for return letter to sender (RLS)")
}

case object TaxSummariesTileToggle extends FeatureFlagName {
  override def toString: String            = "tax-summaries-tile"
  override val description: Option[String] = Some("Enable/disable the tile for annual tax summary")
}

case object SingleAccountCheckToggle extends FeatureFlagName {
  override def toString: String            = "single-account-check"
  override val description: Option[String] = Some("Enable/disable single account check")
}

case class DeletedToggle(name: String) extends FeatureFlagName {
  override def toString: String = name
}

case object TaxcalcMakePaymentLinkToggle extends FeatureFlagName {
  override def toString: String = "taxcalc-make-payment-link-toggle"

  override val description: Option[String] = Some("Enable/disable direct link to make a payment on taxcalc tile")
}

case object PertaxBackendToggle extends FeatureFlagName {
  override def toString: String = "pertax-backend-toggle"

  override val description: Option[String] = Some(
    "Enable/disable pertax backend during auth"
  )
}

case object NpsShutteringToggle extends FeatureFlagName {
  override def toString: String            = "nps-shuttering-toggle"
  override val description: Option[String] = Some("Enable/disable the nps shuttering banner and subpage")
}

case object AppleSaveAndViewNIToggle extends FeatureFlagName {
  override def toString: String = "apple-save-view-ni-toggle"

  override val description: Option[String] = Some("Enable/disable the new content for the Apple Save and View NI")
}

case object NpsOutageToggle extends FeatureFlagName {
  override def toString: String = "nps-outage-toggle"

  override val description: Option[String] = Some(
    "Enable/disable calls to NPS so as not to cause errors during an NPS outage"
  )
}

case object SCAWrapperToggle extends FeatureFlagName {
  override def toString: String            = "sca-wrapper-toggle"
  override val description: Option[String] = Some(
    "Enable/disable the SCA Wrapper layout"
  )
}

case object HmrcAccountToggle extends FeatureFlagName {
  override def toString: String = "hmrc-account-toggle"

  override val description: Option[String] = Some(
    "Enable/disable the hmrc-account service replacing the Profile and Settings page"
  )
}

case object PaperlessInterruptToggle extends FeatureFlagName {
  override def toString: String            = "enforce-paperless-preference"
  override val description: Option[String] = Some("Enable/disable the interrupt for paperless setting")
}

case object AlertBannerPaperlessStatusToggle extends FeatureFlagName {
  override def toString: String = "alert-banner-paperless-status-toggle"

  override val description: Option[String] = Some(
    "Enable/disable paperless alerts in alert banner on the home page"
  )
}

object FeatureFlagMongoFormats {
  implicit val formats: Format[FeatureFlag] =
    Json.format[FeatureFlag]
}

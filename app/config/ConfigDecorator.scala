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

package config

import com.google.inject.{Inject, Singleton}
import config.BannerTcsServiceClosure.BannerTcsServiceClosure
import controllers.bindable.Origin
import controllers.routes
import play.api.Configuration
import play.api.i18n.Lang
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.{URL, URLEncoder}
import java.time.LocalDate

@Singleton
class ConfigDecorator @Inject() (
  runModeConfiguration: Configuration,
  servicesConfig: ServicesConfig
) {

  lazy val internalAuthResourceType: String =
    runModeConfiguration.getOptional[String]("internal-auth.resource-type").getOrElse("ddcn-live-admin-frontend")

  val defaultOrigin: Origin = Origin("PERTAX")

  val authProviderKey = "AuthProvider"
  val authProviderGG  = "GovernmentGateway"

  def currentLocalDate: LocalDate = LocalDate.now()

  private val defaultSessionCacheTtl = 15
  val sessionCacheTtl: Int           =
    runModeConfiguration.getOptional[Int]("feature.session-cache.ttl").getOrElse(defaultSessionCacheTtl)

  private val defaultSessionCacheTtlInSec = 900
  val sessionTimeoutInSeconds: Int        =
    runModeConfiguration.getOptional[Int]("feature.session-cache.timeoutInSec").getOrElse(defaultSessionCacheTtlInSec)

  def seissUrl: String = servicesConfig.baseUrl("self-employed-income-support")

  private lazy val formFrontendService       = servicesConfig.baseUrl("dfs-digital-forms-frontend")
  private lazy val taxCalcFrontendService    = servicesConfig.baseUrl("taxcalc-frontend")
  lazy val businessTaxAccountService: String = servicesConfig.baseUrl("business-tax-account")
  lazy val tcsBrokerHost: String             = servicesConfig.baseUrl("tcs-broker")

  private lazy val payApiUrl = servicesConfig.baseUrl("pay-api")

  private lazy val enrolmentStoreProxyService = servicesConfig.baseUrl("enrolment-store-proxy")

  lazy val addTaxesFrontendUrl: String = servicesConfig.baseUrl("add-taxes-frontend")
  lazy val addTaxesPtaOrigin: String   = "pta-sa"

  lazy val pertaxUrl: String = servicesConfig.baseUrl("pertax")

  private def getExternalUrl(key: String): Option[String] =
    runModeConfiguration.getOptional[String](s"external-url.$key")

  //These hosts should be empty for Prod like environments, all frontend services run on the same host so e.g. localhost:9030/tai in local should be /tai in prod
  lazy val seissFrontendHost: String                       = getExternalUrl(s"self-employed-income-support-frontend.host").getOrElse("")
  private lazy val incomeTaxViewChangeFrontendHost: String =
    getExternalUrl(s"income-tax-view-change-frontend.host").getOrElse("")
  lazy val preferencesFrontendService: String              = getExternalUrl(s"preferences-frontend").getOrElse("")
  lazy val taiHost: String                                 = getExternalUrl(s"tai-frontend.host").getOrElse("")
  private lazy val saveYourNationalInsuranceNumberHost     =
    getExternalUrl(s"save-your-national-insurance-number.host").getOrElse("")

  lazy val basGatewayFrontendHost: String = getExternalUrl(s"bas-gateway-frontend.host").getOrElse("")
  lazy val ggSignInUrl: String            = s"$basGatewayFrontendHost/bas-gateway/sign-in"

  lazy val pertaxFrontendHost: String                        = getExternalUrl(s"pertax-frontend.host").getOrElse("")
  lazy val pertaxFrontendForAuthHost: String                 = getExternalUrl(s"pertax-frontend.auth-host").getOrElse("")
  private lazy val feedbackSurveyFrontendHost: String        = getExternalUrl(s"feedback-survey-frontend.host").getOrElse("")
  private lazy val tcsFrontendHost: String                   = getExternalUrl(s"tcs-frontend.host").getOrElse("")
  private lazy val nispFrontendHost: String                  = getExternalUrl(s"nisp-frontend.host").getOrElse("")
  private lazy val dfsFrontendHost: String                   = getExternalUrl(s"dfs-digital-forms-frontend.host").getOrElse("")
  private lazy val fandfFrontendHost: String                 = getExternalUrl(s"fandf-frontend.host").getOrElse("")
  private lazy val agentClientManagementFrontendHost: String =
    getExternalUrl("agent-client-management-frontend.host").getOrElse("")

  private lazy val saFrontendHost                               = getExternalUrl(s"sa-frontend.host").getOrElse("")
  private lazy val governmentGatewayLostCredentialsFrontendHost =
    getExternalUrl(s"government-gateway-lost-credentials-frontend.host").getOrElse("")

  private lazy val enrolmentManagementFrontendHost: String =
    getExternalUrl(s"enrolment-management-frontend.host").getOrElse("")
  private lazy val annualTaxSummariesUrl: String           = getExternalUrl("tax-summaries-frontend.host").getOrElse("")
  lazy val isNewsAndUpdatesTileEnabled: Boolean            =
    runModeConfiguration.get[String]("feature.news.enabled").toBoolean
  lazy val annualTaxSaSummariesTileLink                    = s"$annualTaxSummariesUrl/annual-tax-summary"
  lazy val annualTaxPayeSummariesTileLink                  = s"$annualTaxSummariesUrl/annual-tax-summary/paye/main"

  lazy val isSeissTileEnabled: Boolean =
    runModeConfiguration.get[String]("feature.self-employed-income-support.enabled").toBoolean

  lazy val portalBaseUrl: String     = runModeConfiguration.get[String]("external-url.sso-portal.host")
  def toPortalUrl(path: String): URL = new URL(portalBaseUrl + path)

  def transformUrlForSso(url: URL): String =
    s"$basGatewayFrontendHost/bas-gateway/ssoout/non-digital?continue=" + URLEncoder.encode(url.toString, "UTF-8")

  def sa302Url(saUtr: String, taxYear: String): String =
    s"/self-assessment-file/$taxYear/ind/$saUtr/return/viewYourCalculation/reviewYourFullCalculation"

  def displayNewsAndUpdatesUrl(newsSectionId: String): String =
    s"/personal-account/news/$newsSectionId"

  def completeYourTaxReturnUrl(saUtr: String, taxYear: String, lang: Lang): String =
    s"$saFrontendHost/self-assessment-file/$taxYear/ind/$saUtr/return?lang=" + (
      if (lang.code equals "en") { "eng" }
      else { "cym" }
    )
  lazy val ssoToActivateSaEnrolmentPinUrl                                          =
    s"$enrolmentManagementFrontendHost/enrolment-management-frontend/IR-SA/get-access-tax-scheme?continue=/personal-account"
  lazy val ssoToRegisterForSaEnrolment: String                                     = transformUrlForSso(toPortalUrl("/home/services/enroll"))
  lazy val ssoToRegistration: String                                               = transformUrlForSso(toPortalUrl("/registration"))
  def ssoToSaAccountSummaryUrl(saUtr: String, taxYear: String): String             =
    transformUrlForSso(toPortalUrl(s"/self-assessment/ind/$saUtr/taxreturn/$taxYear/options"))
  def viewSaPaymentsUrl(saUtr: String, lang: Lang): String                         =
    s"/self-assessment/ind/$saUtr/account/payments?lang=" + (
      if (lang.code equals "en") { "eng" }
      else { "cym" }
    )

  lazy val contactHmrcUrl = "https://www.gov.uk/contact-hmrc"

  lazy val makeAPaymentUrl = s"$payApiUrl/pay-api/pta/sa/journey/start"

  private lazy val accessibilityBaseUrl: String = servicesConfig.getString("accessibility-statement.baseUrl")
  lazy private val accessibilityRedirectUrl     =
    servicesConfig.getString("accessibility-statement.redirectUrl")

  private val enc: String => String                       = URLEncoder.encode(_: String, "UTF-8")
  def accessibilityStatementUrl(referrer: String): String =
    s"$accessibilityBaseUrl/accessibility-statement$accessibilityRedirectUrl?referrerUrl=${enc(accessibilityBaseUrl + referrer)}"

  lazy val notShownSaRecoverYourUserId =
    s"$governmentGatewayLostCredentialsFrontendHost/government-gateway-lost-credentials-frontend/choose-your-account-access?origin=${enc(defaultOrigin.toString)}"

  lazy val onlineServicesHelpdeskUrl =
    "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/online-services-helpdesk"

  lazy val selfAssessmentEnrolUrl   =
    s"$enrolmentManagementFrontendHost/enrolment-management-frontend/IR-SA/request-access-tax-scheme?continue=/personal-account"
  lazy val selfAssessmentContactUrl =
    "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/self-assessment"

  lazy val origin: String =
    runModeConfiguration
      .getOptional[String]("sosOrigin")
      .orElse(runModeConfiguration.getOptional[String]("appName"))
      .getOrElse("undefined")

  lazy val hmrcProblemsSigningIn = "https://www.gov.uk/log-in-register-hmrc-online-services/problems-signing-in"
  lazy val generalQueriesUrl     = "https://www.gov.uk/contact-hmrc"

  def makingTaxDigitalForIncomeTaxUrl(lang: Lang): String =
    if (lang.code equals "en") { "https://www.gov.uk/guidance/using-making-tax-digital-for-income-tax" }
    else { "https://www.gov.uk/guidance/using-making-tax-digital-for-income-tax.cy" }

  lazy val nationalInsuranceFormPartialLinkUrl =
    s"$formFrontendService/digital-forms/forms/personal-tax/national-insurance/catalogue"
  lazy val selfAssessmentFormPartialLinkUrl    =
    s"$formFrontendService/digital-forms/forms/personal-tax/self-assessment/catalogue"
  lazy val taxCalcPartialLinkUrl               = s"$taxCalcFrontendService/tax-you-paid/summary-card-partials"

  lazy val tcsChangeAddressUrl       = s"$tcsFrontendHost/tax-credits-service/personal/change-address"
  lazy val tcsServiceRouterUrl       = s"$tcsFrontendHost/tax-credits-service/renewals/service-router"
  lazy val updateAddressShortFormUrl = "https://www.tax.service.gov.uk/shortforms/form/PAYENICoC"
  lazy val changeNameLinkUrl         =
    s"$dfsFrontendHost/digital-forms/form/notification-of-a-change-in-personal-details/draft/guide"
  lazy val changePersonalDetailsUrl  =
    "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/change-your-personal-details"
  lazy val scottishRateIncomeTaxUrl  = "https://www.gov.uk/scottish-rate-income-tax/how-it-works"

  lazy val serviceIdentityCheckFailedUrl = "/personal-account/identity-check-failed"
  lazy val personalAccountYourAddress    = "/personal-account/your-address"
  lazy val personalAccount               = "/personal-account"

  lazy val claimChildBenefits: String = "https://www.gov.uk/child-benefit/how-to-claim"

  lazy val claimChildBenefitsWelsh: String = "https://www.gov.uk/budd-dal-plant/sut-i-hawlio"

  lazy val childBenefit: String = "https://www.gov.uk/child-benefit"

  lazy val childBenefitWelsh: String = "https://www.gov.uk/budd-dal-plant"

  lazy val reportChangesChildBenefit: String = "https://www.gov.uk/report-changes-child-benefit"

  lazy val reportChangesChildBenefitWelsh: String = "https://www.gov.uk/rhoi-gwybod-am-newidiadau-budd-dal-plant"

  lazy val changeBankDetails: String =
    runModeConfiguration.get[String]("external-url.child-benefits.change-bank-account-location")

  lazy val viewPaymentHistory: String =
    runModeConfiguration.get[String]("external-url.child-benefits.view-payment-history-location")

  lazy val viewProofEntitlement: String =
    runModeConfiguration.get[String]("external-url.child-benefits.view-proof-entitlement-location")

  lazy val pegaSaRegistrationUrl: String = runModeConfiguration.get[String]("external-url.pegaSaRegistration.url")

  lazy val childBenefitTaxCharge: String = "https://www.gov.uk/child-benefit-tax-charge"

  lazy val childBenefitTaxChargeWelsh: String = "https://www.gov.uk/tal-treth-budd-dal-plant"

  lazy val p85Link: String = "https://www.gov.uk/tax-right-retire-abroad-return-to-uk"

  lazy val nationalInsuranceRecordUrl = s"$nispFrontendHost/check-your-state-pension/account/nirecord/pta"

  lazy val statePensionSummary = s"$nispFrontendHost/check-your-state-pension/account"

  lazy val enrolmentStoreProxyUrl = s"$enrolmentStoreProxyService/enrolment-store-proxy"

  lazy val usersGroupsSearchBaseURL: String =
    s"${servicesConfig.baseUrl("users-groups-search")}/users-groups-search"

  // Links back to pertax
  lazy val pertaxFrontendHomeUrl: String  = pertaxFrontendHost + routes.HomeController.index.url
  lazy val pertaxFrontendBackLink: String = runModeConfiguration
    .get[String]("external-url.pertax-frontend.host") + routes.HomeController.index.url

  lazy val updateInternationalAddressInPta: Boolean =
    runModeConfiguration
      .getOptional[String]("feature.update-international-address-form.enabled")
      .getOrElse("false")
      .toBoolean

  lazy val partialUpgradeEnabled: Boolean =
    runModeConfiguration.getOptional[Boolean]("feature.partial-upgraded-required.enabled").getOrElse(false)

  lazy val itsaViewUrl = s"$incomeTaxViewChangeFrontendHost/report-quarterly/income-and-expenses/view?origin=PTA"

  def getFeedbackSurveyUrl(origin: Origin): String =
    feedbackSurveyFrontendHost + "/feedback/" + enc(origin.origin)

  def getBasGatewayFrontendSignOutUrl(continueUrl: String): String =
    basGatewayFrontendHost + s"/bas-gateway/sign-out-without-state?continue=$continueUrl"

  lazy val editAddressTtl: Int = runModeConfiguration.getOptional[Int]("mongodb.editAddressTtl").getOrElse(0)

  lazy val saPartialReturnLinkText = "Back to account home"

  lazy val manageTrustedHelpersUrl                  = s"$fandfFrontendHost/trusted-helpers/select-a-service"
  lazy val seissClaimsUrl                           = s"$seissFrontendHost/self-employment-support/claim/your-claims"
  def manageTaxAgentsUrl(returnUrl: String): String =
    s"$agentClientManagementFrontendHost/manage-your-tax-agents?source=PTA&returnUrl=$returnUrl"

  lazy val shutterBannerParagraphEn: String =
    runModeConfiguration.getOptional[String]("feature.alert-shuttering.banner.paragraph.en").getOrElse("")
  lazy val shutterBannerParagraphCy: String =
    runModeConfiguration.getOptional[String]("feature.alert-shuttering.banner.paragraph.cy").getOrElse("")
  lazy val shutterBannerLinkTextEn: String  =
    runModeConfiguration.getOptional[String]("feature.alert-shuttering.banner.linkText.en").getOrElse("")
  lazy val shutterBannerLinkTextCy: String  =
    runModeConfiguration.getOptional[String]("feature.alert-shuttering.banner.linkText.cy").getOrElse("")

  lazy val shutterPageParagraphEn: String =
    runModeConfiguration.getOptional[String]("feature.alert-shuttering.page.paragraph.en").getOrElse("")
  lazy val shutterPageParagraphCy: String =
    runModeConfiguration.getOptional[String]("feature.alert-shuttering.page.paragraph.cy").getOrElse("")

  lazy val breathingSpaceBaseUrl: String                 = servicesConfig.baseUrl("breathing-space-if-proxy")
  lazy val breathingSpaceTimeoutInMilliseconds: Int      =
    servicesConfig.getInt("microservice.services.breathing-space-if-proxy.timeoutInMilliseconds")
  lazy val citizenDetailsTimeoutInMilliseconds: Int      =
    servicesConfig.getInt("microservice.services.citizen-details.timeoutInMilliseconds")
  lazy val tcsBrokerTimeoutInMilliseconds: Int           =
    servicesConfig.getInt("microservice.services.tcs-broker.timeoutInMilliseconds")
  lazy val taiTimeoutInMilliseconds: Int                 =
    servicesConfig.getInt("microservice.services.tai.timeoutInMilliseconds")
  lazy val dfsPartialTimeoutInMilliseconds: Int          =
    servicesConfig.getInt("microservice.services.dfs-digital-forms-frontend.timeoutInMilliseconds")
  lazy val taxCalcPartialTimeoutInMilliseconds: Int      =
    servicesConfig.getInt("microservice.services.taxcalc-frontend.timeoutInMilliseconds")
  lazy val preferenceFrontendTimeoutInSec: Int           =
    servicesConfig.getInt("feature.preferences-frontend.timeoutInSec")
  lazy val enrolmentStoreProxyTimeoutInMilliseconds: Int =
    servicesConfig.getInt("microservice.services.enrolment-store-proxy.timeoutInMilliseconds")
  lazy val ptaNinoSaveUrl: String                        = saveYourNationalInsuranceNumberHost + "/save-your-national-insurance-number"
  lazy val guidanceForWhenYourChildTurnsSixteen          = "https://www.gov.uk/child-benefit-16-19"

  lazy val guidanceForWhenYourChildTurnsSixteenWelsh = "https://www.gov.uk/budd-dal-plant-16-19"

  lazy val extendYourPaymentWhileYourChildStaysInEducation: String =
    runModeConfiguration.get[String]("external-url.child-benefits.extend-payments-location")

  lazy val addressLookupTimeoutInSec: Int =
    servicesConfig.getInt("feature.address-lookup.timeoutInSec")

  lazy val pegaSaRegistrationEnabled: Boolean =
    servicesConfig.getBoolean("feature.pegaSaRegistration.enabled")

  def featureBannerTcsServiceClosure: BannerTcsServiceClosure =
    runModeConfiguration.get[String]("feature.bannerTcsServiceClosure").toLowerCase match {
      case "enabled"    => BannerTcsServiceClosure.Enabled
      case "disabled"   => BannerTcsServiceClosure.Disabled
      case "dont-check" => BannerTcsServiceClosure.DontCheck
      case other        => throw new IllegalArgumentException(s"Invalid value for feature.bannerTcsServiceClosure§: $other")
    }
}

object BannerTcsServiceClosure extends Enumeration {
  type BannerTcsServiceClosure = Value
  val Enabled, Disabled, DontCheck = Value
}

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
import controllers.bindable.Origin
import controllers.routes
import play.api.Configuration
import play.api.i18n.{Lang, Langs}
import uk.gov.hmrc.play.bootstrap.binders.SafeRedirectUrl
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.{URL, URLEncoder}
import java.time.LocalDate

@Singleton
class ConfigDecorator @Inject() (
  runModeConfiguration: Configuration,
  langs: Langs,
  servicesConfig: ServicesConfig
) extends TaxcalcUrls {

  lazy val authProviderChoice: String = runModeConfiguration.get[String](s"external-url.auth-provider-choice.host")

  lazy val internalAuthResourceType: String =
    runModeConfiguration.getOptional[String]("internal-auth.resource-type").getOrElse("ddcn-live-admin-frontend")

  val defaultOrigin: Origin = Origin("PERTAX")

  val authProviderKey = "AuthProvider"
  val authProviderGG  = "GovernmentGateway"

  def currentLocalDate: LocalDate = LocalDate.now()

  val sessionCacheTtl: Int = runModeConfiguration.getOptional[Int]("feature.session-cache.ttl").getOrElse(15)

  def seissUrl: String = servicesConfig.baseUrl("self-employed-income-support")

  private lazy val contactFrontendService    = servicesConfig.baseUrl("contact-frontend")
  private lazy val formFrontendService       = servicesConfig.baseUrl("dfs-digital-forms-frontend")
  lazy val pertaxFrontendService: String     = servicesConfig.baseUrl("pertax-frontend")
  lazy val businessTaxAccountService: String = servicesConfig.baseUrl("business-tax-account")
  lazy val tcsBrokerHost: String             = servicesConfig.baseUrl("tcs-broker")

  private lazy val payApiUrl = servicesConfig.baseUrl("pay-api")

  private lazy val enrolmentStoreProxyService = servicesConfig.baseUrl("enrolment-store-proxy")

  lazy val addTaxesFrontendUrl: String = servicesConfig.baseUrl("add-taxes-frontend")
  lazy val addTaxesPtaOrigin: String   = "pta-sa"

  private def getExternalUrl(key: String): Option[String] =
    runModeConfiguration.getOptional[String](s"external-url.$key")

  //These hosts should be empty for Prod like environments, all frontend services run on the same host so e.g localhost:9030/tai in local should be /tai in prod
  lazy val seissFrontendHost: String               = getExternalUrl(s"self-employed-income-support-frontend.host").getOrElse("")
  lazy val incomeTaxViewChangeFrontendHost: String =
    getExternalUrl(s"income-tax-view-change-frontend.host").getOrElse("")
  lazy val preferencesFrontendService: String      = getExternalUrl(s"preferences-frontend").getOrElse("")
  lazy val contactHost: String                     = getExternalUrl(s"contact-frontend.host").getOrElse("")
  lazy val taiHost: String                         = getExternalUrl(s"tai-frontend.host").getOrElse("")
  lazy val formTrackingHost: String                = getExternalUrl(s"tracking-frontend.host").getOrElse("")

  lazy val identityVerificationHost: String           = getExternalUrl(s"identity-verification.host").getOrElse("")
  lazy val identityVerificationPrefix: String         = getExternalUrl(s"identity-verification.prefix").getOrElse("mdtp")
  lazy val basGatewayFrontendHost: String             = getExternalUrl(s"bas-gateway-frontend.host").getOrElse("")
  lazy val taxEnrolmentAssignmentFrontendHost: String =
    getExternalUrl(s"tax-enrolment-assignment-frontend.host").getOrElse("")
  lazy val pertaxFrontendHost: String                 = getExternalUrl(s"pertax-frontend.host").getOrElse("")
  lazy val pertaxFrontendForAuthHost: String          = getExternalUrl(s"pertax-frontend.auth-host").getOrElse("")
  lazy val feedbackSurveyFrontendHost: String         = getExternalUrl(s"feedback-survey-frontend.host").getOrElse("")
  lazy val tcsFrontendHost: String                    = getExternalUrl(s"tcs-frontend.host").getOrElse("")
  lazy val nispFrontendHost: String                   = getExternalUrl(s"nisp-frontend.host").getOrElse("")
  lazy val taxCalcFrontendHost: String                = getExternalUrl(s"taxcalc-frontend.host").getOrElse("")
  lazy val dfsFrontendHost: String                    = getExternalUrl(s"dfs-digital-forms-frontend.host").getOrElse("")
  lazy val fandfFrontendHost: String                  = getExternalUrl(s"fandf-frontend.host").getOrElse("")
  lazy val agentClientManagementFrontendHost: String  =
    getExternalUrl("agent-client-management-frontend.host").getOrElse("")

  lazy val saFrontendHost                               = getExternalUrl(s"sa-frontend.host").getOrElse("")
  lazy val childBenefitViewFrontend: String             = getExternalUrl(s"child-benefit-view-frontend.host").getOrElse("")
  lazy val governmentGatewayLostCredentialsFrontendHost =
    getExternalUrl(s"government-gateway-lost-credentials-frontend.host").getOrElse("")

  lazy val enrolmentManagementFrontendHost: String   = getExternalUrl(s"enrolment-management-frontend.host").getOrElse("")
  lazy val ssoUrl: Option[String]                    = getExternalUrl("sso-portal.host")
  lazy val annualTaxSummariesUrl: String             = getExternalUrl("tax-summaries-frontend.host").getOrElse("")
  lazy val isNewsAndUpdatesTileEnabled: Boolean      =
    runModeConfiguration.get[String]("feature.news-and-updates-tile.enabled").toBoolean
  lazy val isBreathingSpaceIndicatorEnabled: Boolean =
    servicesConfig.getBoolean("feature.breathing-space-indicator.enabled")
  lazy val annualTaxSaSummariesTileLink              = s"$annualTaxSummariesUrl/annual-tax-summary"
  lazy val annualTaxPayeSummariesTileLink            = s"$annualTaxSummariesUrl/annual-tax-summary/paye/main"

  lazy val childBenefitLinkUrl         = Some(
    "https://docs.google.com/forms/d/e/1FAIpQLSegbiz4ClGW0XkC1pY3B02ltiY1V79V7ha0jZinECIz_FvSyg/viewform"
  )
  lazy val isSeissTileEnabled: Boolean =
    runModeConfiguration.get[String]("feature.self-employed-income-support.enabled").toBoolean

  lazy val portalBaseUrl: String = runModeConfiguration.get[String]("external-url.sso-portal.host")
  def toPortalUrl(path: String)  = new URL(portalBaseUrl + path)

  def transformUrlForSso(url: URL): String =
    s"$basGatewayFrontendHost/bas-gateway/ssoout/non-digital?continue=" + URLEncoder.encode(url.toString, "UTF-8")

  def sa302Url(saUtr: String, taxYear: String) =
    s"/self-assessment-file/$taxYear/ind/$saUtr/return/viewYourCalculation/reviewYourFullCalculation"

  def displayNewsAndUpdatesUrl(newsSectionId: String) =
    s"/personal-account/news/$newsSectionId"

  def completeYourTaxReturnUrl(saUtr: String, taxYear: String, lang: Lang): String =
    s"$saFrontendHost/self-assessment-file/$taxYear/ind/$saUtr/return?lang=" + (if (lang.code equals "en") "eng"
                                                                                else "cym")
  lazy val ssoToActivateSaEnrolmentPinUrl                                          =
    s"$enrolmentManagementFrontendHost/enrolment-management-frontend/IR-SA/get-access-tax-scheme?continue=/personal-account"
  lazy val ssoToRegisterForSaEnrolment: String                                     = transformUrlForSso(toPortalUrl("/home/services/enroll"))
  lazy val ssoToRegistration: String                                               = transformUrlForSso(toPortalUrl("/registration"))
  def ssoToSaAccountSummaryUrl(saUtr: String, taxYear: String): String             =
    transformUrlForSso(toPortalUrl(s"/self-assessment/ind/$saUtr/taxreturn/$taxYear/options"))
  def viewSaPaymentsUrl(saUtr: String, lang: Lang): String                         =
    s"/self-assessment/ind/$saUtr/account/payments?lang=" + (if (lang.code equals "en") "eng"
                                                             else "cym")

  def betaFeedbackUnauthenticatedUrl(aDeskproToken: String) =
    s"$contactHost/contact/beta-feedback-unauthenticated?service=$aDeskproToken"

  lazy val contactHmrcUrl = "https://www.gov.uk/contact-hmrc"

  lazy val reportAProblemPartialUrl = s"$contactFrontendService/contact/problem_reports"

  lazy val makeAPaymentUrl = s"$payApiUrl/pay-api/pta/sa/journey/start"
  lazy val deskproToken    = "PTA"

  lazy val accessibilityStatementToggle: Boolean =
    runModeConfiguration.getOptional[Boolean](s"accessibility-statement.toggle").getOrElse(false)
  lazy val accessibilityBaseUrl: String          = servicesConfig.getString("accessibility-statement.baseUrl")
  lazy private val accessibilityRedirectUrl      =
    servicesConfig.getString("accessibility-statement.redirectUrl")

  def accessibilityStatementUrl(referrer: String) =
    s"$accessibilityBaseUrl/accessibility-statement$accessibilityRedirectUrl?referrerUrl=${SafeRedirectUrl(accessibilityBaseUrl + referrer).encodedUrl}"

  lazy val formTrackingServiceUrl = s"$formTrackingHost/track"

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

  val ehCacheTtlInSeconds: Int = runModeConfiguration.getOptional[Int]("ehCache.ttlInSeconds").getOrElse(600)

  lazy val hmrcProblemsSigningIn = "https://www.gov.uk/log-in-register-hmrc-online-services/problems-signing-in"
  lazy val generalQueriesUrl     = "https://www.gov.uk/contact-hmrc"

  lazy val healthAndSocialCareLevyUrl = "https://www.gov.uk/guidance/prepare-for-the-health-and-social-care-levy"

  def makingTaxDigitalForIncomeTaxUrl(lang: Lang): String =
    if (lang.code equals "en") { "https://www.gov.uk/guidance/using-making-tax-digital-for-income-tax" }
    else { "https://www.gov.uk/guidance/using-making-tax-digital-for-income-tax.cy" }

  def taxEnrolmentDeniedRedirect(url: String): String =
    s"$taxEnrolmentAssignmentFrontendHost/protect-tax-info?redirectUrl=${SafeRedirectUrl(url).encodedUrl}"

  lazy val nationalInsuranceFormPartialLinkUrl =
    s"$formFrontendService/digital-forms/forms/personal-tax/national-insurance/catalogue"
  lazy val selfAssessmentFormPartialLinkUrl    =
    s"$formFrontendService/digital-forms/forms/personal-tax/self-assessment/catalogue"

  lazy val identityVerificationUpliftUrl      = s"$identityVerificationHost/$identityVerificationPrefix/uplift"
  lazy val multiFactorAuthenticationUpliftUrl = s"$basGatewayFrontendHost/bas-gateway/uplift-mfa"
  lazy val tcsChangeAddressUrl                = s"$tcsFrontendHost/tax-credits-service/personal/change-address"
  lazy val tcsServiceRouterUrl                = s"$tcsFrontendHost/tax-credits-service/renewals/service-router"
  lazy val updateAddressShortFormUrl          = "https://www.tax.service.gov.uk/shortforms/form/PAYENICoC"
  lazy val changeNameLinkUrl                  =
    s"$dfsFrontendHost/digital-forms/form/notification-of-a-change-in-personal-details/draft/guide"
  lazy val changePersonalDetailsUrl           =
    "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/change-your-personal-details"
  lazy val scottishRateIncomeTaxUrl           = "https://www.gov.uk/scottish-rate-income-tax/how-it-works"
  lazy val personalAccountYourAddress         = "/personal-account/your-address"
  lazy val personalAccount                    = "/personal-account"

  lazy val childBenefitsStaysInEducation            =
    s"$dfsFrontendHost/digital-forms/form/Tell-Child-Benefit-about-your-child-staying-in-non-advanced-education-or-approved-training/draft/guide"
  lazy val childBenefitsLaterLeavesEducation        =
    s"$dfsFrontendHost/digital-forms/form/Tell-Child-Benefit-about-your-child-leaving-non-advanced-education-or-approved-training/draft/guide"
  lazy val childBenefitsHasAnyChangeInCircumstances =
    s"$dfsFrontendHost/digital-forms/form/child-benefit-child-change-of-circumstances/draft/guide"
  lazy val childBenefitsApplyForExtension           =
    s"$dfsFrontendHost/digital-forms/form/Application-for-extension-of-Child-Benefit/draft/guide"
  lazy val childBenefitsReportChange                =
    s"$dfsFrontendHost/digital-forms/form/Child-Benefit-Claimant-Change-of-Circumstances/draft/guide"
  lazy val childBenefitsAuthoriseTaxAdvisor         =
    s"$dfsFrontendHost/digital-forms/form/authorise-a-tax-adviser-for-high-income-child-benefit-charge-matters/draft/guide"
  lazy val childBenefitsStopOrRestart               =
    s"$dfsFrontendHost/digital-forms/form/high-income-child-benefit-tax-charge/draft/guide"

  lazy val childBenefitsCheckIfYouCanClaim: String = "https://www.gov.uk/child-benefit/overview"

  lazy val claimChildBenefits: String = "https://www.gov.uk/child-benefit/how-to-claim"

  lazy val claimChildBenefitsWelsh: String = "https://www.gov.uk/budd-dal-plant/sut-i-hawlio"

  lazy val reportChangesChildBenefit: String = "https://www.gov.uk/report-changes-child-benefit"

  lazy val reportChangesChildBenefitWelsh: String = "https://www.gov.uk/rhoi-gwybod-am-newidiadau-budd-dal-plant"

  lazy val viewPaymentHistory: String = s"$childBenefitViewFrontend/child-benefit/view-payment-history"

  lazy val viewProofEntitlement: String = s"$childBenefitViewFrontend/child-benefit/view-proof-entitlement"

  lazy val childBenefitTaxCharge: String = "https://www.gov.uk/child-benefit-tax-charge"

  lazy val childBenefitTaxChargeWelsh: String = "https://www.gov.uk/tal-treth-budd-dal-plant"

  lazy val nationalInsuranceRecordUrl = s"$nispFrontendHost/check-your-state-pension/account/nirecord/pta"

  lazy val enrolmentStoreProxyUrl = s"$enrolmentStoreProxyService/enrolment-store-proxy"

  //TODO : To be replaced with original url once SCA team completes the work
  lazy val viewAndSaveNINumberUrl: String = s""

  // Links back to pertax
  lazy val pertaxFrontendHomeUrl: String  = pertaxFrontendHost + routes.HomeController.index.url
  lazy val pertaxFrontendBackLink: String = runModeConfiguration
    .get[String]("external-url.pertax-frontend.host") + routes.HomeController.index.url

  lazy val welshLangEnabled: Boolean  = langs.availables.exists(l => l.code == "cy")
  lazy val taxCreditsEnabled: Boolean =
    runModeConfiguration.getOptional[String]("feature.tax-credits.enabled").getOrElse("true").toBoolean

  // Only used in HomeControllerSpec
  lazy val allowLowConfidenceSAEnabled: Boolean  =
    runModeConfiguration.getOptional[String]("feature.allow-low-confidence-sa.enabled").getOrElse("false").toBoolean
  lazy val allowSaPreview: Boolean               =
    runModeConfiguration.getOptional[String]("feature.allow-sa-preview.enabled").getOrElse("false").toBoolean
  lazy val taxCreditsPaymentLinkEnabled: Boolean =
    runModeConfiguration.getOptional[String]("feature.tax-credits-payment-link.enabled").getOrElse("true").toBoolean

  lazy val nispEnabled: Boolean =
    runModeConfiguration.getOptional[String]("feature.nisp.enabled").getOrElse("true").toBoolean

  lazy val saveNiLetterAsPdfLinkEnabled: Boolean =
    runModeConfiguration.getOptional[String]("feature.save-ni-letter-as-pdf.enabled").getOrElse("false").toBoolean

  lazy val personDetailsMessageCountEnabled =
    runModeConfiguration.getOptional[String]("feature.person-details-message-count.enabled").getOrElse("true").toBoolean

  lazy val updateInternationalAddressInPta: Boolean =
    runModeConfiguration
      .getOptional[String]("feature.update-international-address-form.enabled")
      .getOrElse("false")
      .toBoolean
  lazy val closePostalAddressEnabled: Boolean       =
    runModeConfiguration.getOptional[String]("feature.close-postal-address.enabled").getOrElse("false").toBoolean

  lazy val getNinoFromCID: Boolean =
    runModeConfiguration.getOptional[Boolean]("feature.get-nino-from-cid.enabled").getOrElse(false)

  lazy val partialUpgradeEnabled: Boolean =
    runModeConfiguration.getOptional[Boolean]("feature.partial-upgraded-required.enabled").getOrElse(false)

  val enc: String => String = URLEncoder.encode(_: String, "UTF-8")

  lazy val sessionTimeoutInSeconds: Int   = runModeConfiguration.getOptional[Int]("ptaSession.timeout").getOrElse(900)
  lazy val sessionTimeoutInMinutes: Int   = sessionTimeoutInSeconds / 60
  lazy val sessionCountdownInSeconds: Int = runModeConfiguration.getOptional[Int]("ptaSession.countdown").getOrElse(120)

  lazy val itsaViewUrl = s"$incomeTaxViewChangeFrontendHost/report-quarterly/income-and-expenses/view?origin=PTA"

  def getFeedbackSurveyUrl(origin: Origin): String =
    feedbackSurveyFrontendHost + "/feedback/" + enc(origin.origin)

  def getBasGatewayFrontendSignOutUrl(continueUrl: String): String =
    basGatewayFrontendHost + s"/bas-gateway/sign-out-without-state?continue=$continueUrl"

  lazy val editAddressTtl: Int = runModeConfiguration.getOptional[Int]("mongodb.editAddressTtl").getOrElse(0)

  lazy val saPartialReturnLinkText = "Back to account home"

  lazy val manageTrustedHelpersUrl = s"$fandfFrontendHost/trusted-helpers/select-a-service"
  lazy val seissClaimsUrl          = s"$seissFrontendHost/self-employment-support/claim/your-claims"
  lazy val manageTaxAgentsUrl      = s"$agentClientManagementFrontendHost/manage-your-tax-agents"

  lazy val bannerHomePageIsEnabled: Boolean =
    runModeConfiguration.getOptional[Boolean]("feature.banner.home.enabled").getOrElse(false)
  lazy val bannerHomePageHeadingEn: String  =
    runModeConfiguration.getOptional[String]("feature.banner.home.heading.en").getOrElse("")
  lazy val bannerHomePageLinkTextEn: String =
    runModeConfiguration.getOptional[String]("feature.banner.home.link.text.en").getOrElse("")
  lazy val bannerHomePageHeadingCy: String  =
    runModeConfiguration.getOptional[String]("feature.banner.home.heading.cy").getOrElse("")
  lazy val bannerHomePageLinkTextCy: String =
    runModeConfiguration.getOptional[String]("feature.banner.home.link.text.cy").getOrElse("")
  lazy val bannerHomePageLinkUrl: String    =
    runModeConfiguration.getOptional[String]("feature.banner.home.link.url").getOrElse("")

  lazy val breathingSpcaeBaseUrl: String   = servicesConfig.baseUrl("breathing-space-if-proxy")
  lazy val breathingSpaceAppName           = "breathing-space-if-proxy"
  lazy val breathingSpaceTimeoutInSec: Int =
    servicesConfig.getInt("feature.breathing-space-indicator.timeoutInSec")
  lazy val preferenceFrontendTimeoutInSec  =
    servicesConfig.getInt("feature.preferences-frontend.timeoutInSec")

  def numberOfCallsToTriggerStateChange(serviceName: String): Int = servicesConfig.getInt(
    s"microservice.services.$serviceName.circuitBreaker.numberOfCallsToTriggerStateChange"
  )
  def unavailablePeriodDuration(serviceName: String): Int         = servicesConfig.getInt(
    s"microservice.services.$serviceName.circuitBreaker.unavailablePeriodDurationInMillis"
  )
  def unstablePeriodDuration(serviceName: String): Int            = servicesConfig.getInt(
    s"microservice.services.$serviceName.circuitBreaker.unstablePeriodDurationInMillis"
  )
}

trait TaxcalcUrls {
  self: ConfigDecorator =>

  def underpaidUrlReasons(taxYear: Int) =
    s"${self.taxCalcFrontendHost}/tax-you-paid/$taxYear-${taxYear + 1}/paid-too-little/reasons"
  def overpaidUrlReasons(taxYear: Int)  =
    s"${self.taxCalcFrontendHost}/tax-you-paid/$taxYear-${taxYear + 1}/paid-too-much/reasons"

  def underpaidUrl(taxYear: Int) = s"${self.taxCalcFrontendHost}/tax-you-paid/$taxYear-${taxYear + 1}/paid-too-little"
  def overpaidUrl(taxYear: Int)  = s"${self.taxCalcFrontendHost}/tax-you-paid/$taxYear-${taxYear + 1}/paid-too-much"

  def rightAmountUrl(taxYear: Int)   = s"${self.taxCalcFrontendHost}/tax-you-paid/$taxYear-${taxYear + 1}/right-amount"
  def notEmployedUrl(taxYear: Int)   = s"${self.taxCalcFrontendHost}/tax-you-paid/$taxYear-${taxYear + 1}/not-employed"
  def notCalculatedUrl(taxYear: Int) =
    s"${self.taxCalcFrontendHost}/tax-you-paid/$taxYear-${taxYear + 1}/not-yet-calculated"

  lazy val taxPaidUrl = s"${self.taxCalcFrontendHost}/tax-you-paid/status"

  val makePaymentUrl = "https://www.gov.uk/simple-assessment"

}

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
import controllers.routes
import play.api.Configuration
import play.api.i18n.{Lang, Langs}
import controllers.bindable.Origin
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
  lazy val authProviderChoice = runModeConfiguration.get[String](s"external-url.auth-provider-choice.host")

  lazy val internalAuthResourceType: String =
    runModeConfiguration.getOptional[String]("internal-auth.resource-type").getOrElse("ddcn-live-admin-frontend")

  val defaultOrigin = Origin("PERTAX")

  val authProviderKey = "AuthProvider"
  val authProviderGG  = "GovernmentGateway"

  def currentLocalDate: LocalDate = LocalDate.now()

  val sessionCacheTtl = runModeConfiguration.getOptional[Int]("feature.session-cache.ttl").getOrElse(15)

  def seissUrl = servicesConfig.baseUrl("self-employed-income-support")

  private lazy val contactFrontendService = servicesConfig.baseUrl("contact-frontend")
  private lazy val formFrontendService    = servicesConfig.baseUrl("dfs-digital-forms-frontend")
  lazy val pertaxFrontendService          = servicesConfig.baseUrl("pertax-frontend")
  lazy val businessTaxAccountService      = servicesConfig.baseUrl("business-tax-account")
  lazy val tcsBrokerHost                  = servicesConfig.baseUrl("tcs-broker")

  private lazy val payApiUrl = servicesConfig.baseUrl("pay-api")

  private lazy val enrolmentStoreProxyService = servicesConfig.baseUrl("enrolment-store-proxy")

  lazy val addTaxesFrontendUrl: String = servicesConfig.baseUrl("add-taxes-frontend")
  lazy val addTaxesPtaOrigin: String   = "pta-sa"

  private def getExternalUrl(key: String): Option[String] =
    runModeConfiguration.getOptional[String](s"external-url.$key")

  //These hosts should be empty for Prod like environments, all frontend services run on the same host so e.g localhost:9030/tai in local should be /tai in prod
  lazy val seissFrontendHost               = getExternalUrl(s"self-employed-income-support-frontend.host").getOrElse("")
  lazy val incomeTaxViewChangeFrontendHost = getExternalUrl(s"income-tax-view-change-frontend.host").getOrElse("")
  lazy val preferencesFrontendService      = getExternalUrl(s"preferences-frontend").getOrElse("")
  lazy val contactHost                     = getExternalUrl(s"contact-frontend.host").getOrElse("")
  lazy val citizenAuthHost                 = getExternalUrl(s"citizen-auth.host").getOrElse("")
  lazy val taiHost                         = getExternalUrl(s"tai-frontend.host").getOrElse("")
  lazy val formTrackingHost                = getExternalUrl(s"tracking-frontend.host").getOrElse("")

  lazy val identityVerificationHost           = getExternalUrl(s"identity-verification.host").getOrElse("")
  lazy val identityVerificationPrefix         = getExternalUrl(s"identity-verification.prefix").getOrElse("mdtp")
  lazy val basGatewayFrontendHost             = getExternalUrl(s"bas-gateway-frontend.host").getOrElse("")
  lazy val taxEnrolmentAssignmentFrontendHost = getExternalUrl(s"tax-enrolment-assignment-frontend.host").getOrElse("")
  lazy val pertaxFrontendHost                 = getExternalUrl(s"pertax-frontend.host").getOrElse("")
  lazy val pertaxFrontendForAuthHost          = getExternalUrl(s"pertax-frontend.auth-host").getOrElse("")
  lazy val feedbackSurveyFrontendHost         = getExternalUrl(s"feedback-survey-frontend.host").getOrElse("")
  lazy val tcsFrontendHost                    = getExternalUrl(s"tcs-frontend.host").getOrElse("")
  lazy val nispFrontendHost                   = getExternalUrl(s"nisp-frontend.host").getOrElse("")
  lazy val taxCalcFrontendHost                = getExternalUrl(s"taxcalc-frontend.host").getOrElse("")
  lazy val dfsFrontendHost                    = getExternalUrl(s"dfs-digital-forms-frontend.host").getOrElse("")
  lazy val fandfFrontendHost                  = getExternalUrl(s"fandf-frontend.host").getOrElse("")
  lazy val agentClientManagementFrontendHost  = getExternalUrl("agent-client-management-frontend.host").getOrElse("")

  lazy val saFrontendHost                               = getExternalUrl(s"sa-frontend.host").getOrElse("")
  lazy val governmentGatewayLostCredentialsFrontendHost =
    getExternalUrl(s"government-gateway-lost-credentials-frontend.host").getOrElse("")

  lazy val enrolmentManagementFrontendHost  = getExternalUrl(s"enrolment-management-frontend.host").getOrElse("")
  lazy val ssoUrl                           = getExternalUrl("sso-portal.host")
  lazy val annualTaxSummariesUrl            = getExternalUrl("tax-summaries-frontend.host").getOrElse("")
  lazy val isAtsTileEnabled                 = runModeConfiguration.get[String]("feature.tax-summaries-tile.enabled").toBoolean
  lazy val isNewsAndUpdatesTileEnabled      =
    runModeConfiguration.get[String]("feature.news-and-updates-tile.enabled").toBoolean
  lazy val isBreathingSpaceIndicatorEnabled =
    servicesConfig.getBoolean("feature.breathing-space-indicator.enabled")
  lazy val annualTaxSaSummariesTileLink     = s"$annualTaxSummariesUrl/annual-tax-summary"
  lazy val annualTaxPayeSummariesTileLink   = s"$annualTaxSummariesUrl/annual-tax-summary/paye/main"

  lazy val childBenefitLinkUrl = Some(
    "https://docs.google.com/forms/d/e/1FAIpQLSegbiz4ClGW0XkC1pY3B02ltiY1V79V7ha0jZinECIz_FvSyg/viewform"
  )
  lazy val isSeissTileEnabled  =
    runModeConfiguration.get[String]("feature.self-employed-income-support.enabled").toBoolean

  lazy val portalBaseUrl                = runModeConfiguration.get[String]("external-url.sso-portal.host")
  def toPortalUrl(path: String)         = new URL(portalBaseUrl + path)
  lazy val frontendTemplatePath: String =
    runModeConfiguration
      .getOptional[String]("microservice.services.frontend-template-provider.path")
      .getOrElse("/template/mustache")

  def transformUrlForSso(url: URL) =
    s"$basGatewayFrontendHost/bas-gateway/ssoout/non-digital?continue=" + URLEncoder.encode(url.toString, "UTF-8")

  def sa302Url(saUtr: String, taxYear: String) =
    s"/self-assessment-file/$taxYear/ind/$saUtr/return/viewYourCalculation/reviewYourFullCalculation"

  def displayNewsAndUpdatesUrl(newsSectionId: String) =
    s"/personal-account/news/$newsSectionId"

  def completeYourTaxReturnUrl(saUtr: String, taxYear: String, lang: Lang) =
    s"$saFrontendHost/self-assessment-file/$taxYear/ind/$saUtr/return?lang=" + (if (lang.code equals "en") "eng"
                                                                                else "cym")
  lazy val ssoToActivateSaEnrolmentPinUrl                                  =
    s"$enrolmentManagementFrontendHost/enrolment-management-frontend/IR-SA/get-access-tax-scheme?continue=/personal-account"
  lazy val ssoToRegisterForSaEnrolment                                     = transformUrlForSso(toPortalUrl("/home/services/enroll"))
  lazy val ssoToRegistration                                               = transformUrlForSso(toPortalUrl("/registration"))
  def ssoToSaAccountSummaryUrl(saUtr: String, taxYear: String)             =
    transformUrlForSso(toPortalUrl(s"/self-assessment/ind/$saUtr/taxreturn/$taxYear/options"))
  def viewSaPaymentsUrl(saUtr: String, lang: Lang): String                 =
    s"/self-assessment/ind/$saUtr/account/payments?lang=" + (if (lang.code equals "en") "eng"
                                                             else "cym")

  def betaFeedbackUnauthenticatedUrl(aDeskproToken: String) =
    s"$contactHost/contact/beta-feedback-unauthenticated?service=$aDeskproToken"

  lazy val contactHmrcUrl = "https://www.gov.uk/contact-hmrc"

  lazy val reportAProblemPartialUrl = s"$contactFrontendService/contact/problem_reports"

  lazy val makeAPaymentUrl = s"$payApiUrl/pay-api/pta/sa/journey/start"
  lazy val deskproToken    = "PTA"

  lazy val accessibilityStatementToggle: Boolean  =
    runModeConfiguration.getOptional[Boolean](s"accessibility-statement.toggle").getOrElse(false)
  lazy val accessibilityBaseUrl                   = servicesConfig.getString("accessibility-statement.baseUrl")
  lazy private val accessibilityRedirectUrl       =
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

  lazy val origin =
    runModeConfiguration
      .getOptional[String]("sosOrigin")
      .orElse(runModeConfiguration.getOptional[String]("appName"))
      .getOrElse("undefined")

  val ehCacheTtlInSeconds = runModeConfiguration.getOptional[Int]("ehCache.ttlInSeconds").getOrElse(600)

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
  lazy val childBenefitsCheckIfYouCanClaim          = "https://www.gov.uk/child-benefit/overview"

  lazy val nationalInsuranceRecordUrl = s"$nispFrontendHost/check-your-state-pension/account/nirecord/pta"

  lazy val enrolmentStoreProxyUrl = s"$enrolmentStoreProxyService/enrolment-store-proxy"

  // Links back to pertax
  lazy val pertaxFrontendHomeUrl  = pertaxFrontendHost + routes.HomeController.index.url
  lazy val pertaxFrontendBackLink = runModeConfiguration
    .get[String]("external-url.pertax-frontend.host") + routes.HomeController.index.url

  lazy val welshLangEnabled  = langs.availables.exists(l => l.code == "cy")
  lazy val taxCreditsEnabled =
    runModeConfiguration.getOptional[String]("feature.tax-credits.enabled").getOrElse("true").toBoolean

  // Only used in HomeControllerSpec
  lazy val allowLowConfidenceSAEnabled   =
    runModeConfiguration.getOptional[String]("feature.allow-low-confidence-sa.enabled").getOrElse("false").toBoolean
  lazy val ltaEnabled                    = runModeConfiguration.getOptional[String]("feature.lta.enabled").getOrElse("true").toBoolean
  lazy val allowSaPreview                =
    runModeConfiguration.getOptional[String]("feature.allow-sa-preview.enabled").getOrElse("false").toBoolean
  lazy val singleAccountEnrolmentFeature =
    runModeConfiguration.getOptional[String]("feature.single-account-enrolment.enabled").getOrElse("false").toBoolean

  lazy val taxComponentsEnabled =
    runModeConfiguration.getOptional[String]("feature.tax-components.enabled").getOrElse("true").toBoolean

  lazy val nispEnabled = runModeConfiguration.getOptional[String]("feature.nisp.enabled").getOrElse("true").toBoolean

  lazy val taxCreditsPaymentLinkEnabled =
    runModeConfiguration.getOptional[String]("feature.tax-credits-payment-link.enabled").getOrElse("true").toBoolean
  lazy val saveNiLetterAsPdfLinkEnabled =
    runModeConfiguration.getOptional[String]("feature.save-ni-letter-as-pdf.enabled").getOrElse("false").toBoolean

  lazy val enforcePaperlessPreferenceEnabled =
    runModeConfiguration.getOptional[String]("feature.enforce-paperless-preference.enabled").getOrElse("true").toBoolean

  lazy val personDetailsMessageCountEnabled =
    runModeConfiguration.getOptional[String]("feature.person-details-message-count.enabled").getOrElse("true").toBoolean

  lazy val updateInternationalAddressInPta =
    runModeConfiguration
      .getOptional[String]("feature.update-international-address-form.enabled")
      .getOrElse("false")
      .toBoolean
  lazy val closePostalAddressEnabled       =
    runModeConfiguration.getOptional[String]("feature.close-postal-address.enabled").getOrElse("false").toBoolean

  lazy val getNinoFromCID =
    runModeConfiguration.getOptional[Boolean]("feature.get-nino-from-cid.enabled").getOrElse(false)

  lazy val rlsInterruptToggle =
    runModeConfiguration.getOptional[Boolean]("feature.rls-interrupt-toggle.enabled").getOrElse(false)

  lazy val partialUpgradeEnabled =
    runModeConfiguration.getOptional[Boolean]("feature.partial-upgraded-required.enabled").getOrElse(false)

  val enc = URLEncoder.encode(_: String, "UTF-8")

  lazy val sessionTimeoutInSeconds   = runModeConfiguration.getOptional[Int]("ptaSession.timeout").getOrElse(900)
  lazy val sessionTimeoutInMinutes   = sessionTimeoutInSeconds / 60
  lazy val sessionCountdownInSeconds = runModeConfiguration.getOptional[Int]("ptaSession.countdown").getOrElse(120)

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

  lazy val breathingSpcaeBaseUrl          = servicesConfig.baseUrl("breathing-space-if-proxy")
  lazy val breathingSpaceAppName          = "breathing-space-if-proxy"
  lazy val breathingSpaceTimeoutInSec     =
    servicesConfig.getInt("feature.breathing-space-indicator.timeoutInSec")
  lazy val preferenceFrontendTimeoutInSec =
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

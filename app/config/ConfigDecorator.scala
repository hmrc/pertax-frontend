/*
 * Copyright 2021 HM Revenue & Customs
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
import org.joda.time.LocalDate
import play.api.{Configuration, Play}
import play.api.i18n.{Lang, Langs}
import uk.gov.hmrc.play.binders.Origin
import uk.gov.hmrc.play.bootstrap.binders.SafeRedirectUrl
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.{URL, URLEncoder}

@Singleton
class ConfigDecorator @Inject() (
  runModeConfiguration: Configuration,
  langs: Langs,
  servicesConfig: ServicesConfig
) extends TaxcalcUrls {
  lazy val authProviderChoice = runModeConfiguration.get[String](s"external-url.auth-provider-choice.host")

  val defaultOrigin = Origin("PERTAX")

  val authProviderKey = "AuthProvider"
  val authProviderGG = "GovernmentGateway"
  val authProviderVerify = "Verify"

  def currentLocalDate: LocalDate = LocalDate.now()

  private lazy val contactFrontendService = servicesConfig.baseUrl("contact-frontend")
  private lazy val formFrontendService = servicesConfig.baseUrl("dfs-digital-forms-frontend")
  lazy val pertaxFrontendService = servicesConfig.baseUrl("pertax-frontend")
  lazy val businessTaxAccountService = servicesConfig.baseUrl("business-tax-account")

  private lazy val payApiUrl = servicesConfig.baseUrl("pay-api")

  private lazy val enrolmentStoreProxyService = servicesConfig.baseUrl("enrolment-store-proxy")

  lazy val addTaxesFrontendUrl: String = servicesConfig.baseUrl("add-taxes-frontend")
  lazy val addTaxesPtaOrigin: String = "pta-sa"

  private def getExternalUrl(key: String): Option[String] =
    runModeConfiguration.getOptional[String](s"external-url.$key")

  //These hosts should be empty for Prod like environments, all frontend services run on the same host so e.g localhost:9030/tai in local should be /tai in prod
  lazy val preferencesFrontendService = getExternalUrl(s"preferences-frontend").getOrElse("")
  lazy val contactHost = getExternalUrl(s"contact-frontend.host").getOrElse("")
  lazy val citizenAuthHost = getExternalUrl(s"citizen-auth.host").getOrElse("")
  lazy val taiHost = getExternalUrl(s"tai-frontend.host").getOrElse("")
  lazy val formTrackingHost = getExternalUrl(s"tracking-frontend.host").getOrElse("")

  lazy val identityVerificationHost = getExternalUrl(s"identity-verification.host").getOrElse("")
  lazy val identityVerificationPrefix = getExternalUrl(s"identity-verification.prefix").getOrElse("mdtp")
  lazy val basGatewayFrontendHost = getExternalUrl(s"bas-gateway-frontend.host").getOrElse("")
  lazy val pertaxFrontendHost = getExternalUrl(s"pertax-frontend.host").getOrElse("")
  lazy val pertaxFrontendForAuthHost = getExternalUrl(s"pertax-frontend.auth-host").getOrElse("")
  lazy val feedbackSurveyFrontendHost = getExternalUrl(s"feedback-survey-frontend.host").getOrElse("")
  lazy val tcsFrontendHost = getExternalUrl(s"tcs-frontend.host").getOrElse("")
  lazy val nispFrontendHost = getExternalUrl(s"nisp-frontend.host").getOrElse("")
  lazy val taxCalcFrontendHost = getExternalUrl(s"taxcalc-frontend.host").getOrElse("")
  lazy val dfsFrontendHost = getExternalUrl(s"dfs-digital-forms-frontend.host").getOrElse("")

  lazy val saFrontendHost = getExternalUrl(s"sa-frontend.host").getOrElse("")
  lazy val governmentGatewayLostCredentialsFrontendHost =
    getExternalUrl(s"government-gateway-lost-credentials-frontend.host").getOrElse("")

  lazy val enrolmentManagementFrontendHost = getExternalUrl(s"enrolment-management-frontend.host").getOrElse("")
  lazy val ssoUrl = getExternalUrl("sso-portal.host")
  lazy val annualTaxSummariesUrl = getExternalUrl("tax-summaries-frontend.host").getOrElse("")
  lazy val isAtsTileEnabled = runModeConfiguration.get[String]("feature.tax-summaries-tile.enabled").toBoolean
  lazy val annualTaxSaSummariesTileLink = s"$annualTaxSummariesUrl/annual-tax-summary"
  lazy val annualTaxPayeSummariesTileLink = s"$annualTaxSummariesUrl/annual-tax-summary/paye/main"

  lazy val portalBaseUrl = runModeConfiguration.get[String]("external-url.sso-portal.host")
  def toPortalUrl(path: String) = new URL(portalBaseUrl + path)
  lazy val frontendTemplatePath: String =
    runModeConfiguration
      .getOptional[String]("microservice.services.frontend-template-provider.path")
      .getOrElse("/template/mustache")

  def transformUrlForSso(url: URL) =
    s"$basGatewayFrontendHost/bas-gateway/ssoout/non-digital?continue=" + URLEncoder.encode(url.toString, "UTF-8")

  def sa302Url(saUtr: String, taxYear: String) =
    s"/self-assessment-file/$taxYear/ind/$saUtr/return/viewYourCalculation/reviewYourFullCalculation"

  def completeYourTaxReturnUrl(saUtr: String, taxYear: String, lang: Lang) =
    s"$saFrontendHost/self-assessment-file/$taxYear/ind/$saUtr/return?lang=" + (if (lang.code equals "en") "eng"
                                                                                else "cym")
  lazy val ssoToActivateSaEnrolmentPinUrl =
    s"$enrolmentManagementFrontendHost/enrolment-management-frontend/IR-SA/get-access-tax-scheme?continue=/personal-account"
  lazy val ssoToRegisterForSaEnrolment = transformUrlForSso(toPortalUrl("/home/services/enroll"))
  lazy val ssoToRegistration = transformUrlForSso(toPortalUrl("/registration"))
  def ssoToSaAccountSummaryUrl(saUtr: String, taxYear: String) =
    transformUrlForSso(toPortalUrl(s"/self-assessment/ind/$saUtr/taxreturn/$taxYear/options"))
  def viewSaPaymentsUrl(saUtr: String, lang: Lang): String =
    s"/self-assessment/ind/$saUtr/account/payments?lang=" + (if (lang.code equals "en") "eng"
                                                             else "cym")

  def betaFeedbackUnauthenticatedUrl(aDeskproToken: String) =
    s"$contactHost/contact/beta-feedback-unauthenticated?service=$aDeskproToken"

  lazy val contactHmrcUrl = "https://www.gov.uk/contact-hmrc"

  lazy val reportAProblemPartialUrl = s"$contactFrontendService/contact/problem_reports"
  lazy val makeAPaymentUrl = s"$payApiUrl/pay-api/pta/sa/journey/start"
  lazy val deskproToken = "PTA"

  lazy val accessibilityStatementToggle: Boolean =
    runModeConfiguration.getOptional[Boolean](s"accessibility-statement.toggle").getOrElse(false)
  lazy val accessibilityBaseUrl = servicesConfig.getString("accessibility-statement.baseUrl")
  lazy private val accessibilityRedirectUrl =
    servicesConfig.getString("accessibility-statement.redirectUrl")
  def accessibilityStatementUrl(referrer: String) =
    s"$accessibilityBaseUrl/accessibility-statement$accessibilityRedirectUrl?referrerUrl=${SafeRedirectUrl(accessibilityBaseUrl + referrer).encodedUrl}"

  lazy val formTrackingServiceUrl = s"$formTrackingHost/track"

  lazy val notShownSaRecoverYourUserId =
    s"$governmentGatewayLostCredentialsFrontendHost/government-gateway-lost-credentials-frontend/choose-your-account-access?origin=${enc(defaultOrigin.toString)}"

  lazy val onlineServicesHelpdeskUrl =
    "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/online-services-helpdesk"

  lazy val selfAssessmentEnrolUrl =
    s"$enrolmentManagementFrontendHost/enrolment-management-frontend/IR-SA/request-access-tax-scheme?continue=/personal-account"
  lazy val selfAssessmentContactUrl =
    "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/self-assessment"

  lazy val origin =
    runModeConfiguration
      .getOptional[String]("sosOrigin")
      .orElse(runModeConfiguration.getOptional[String]("appName"))
      .getOrElse("undefined")

  lazy val hmrcProblemsSigningIn = "https://www.gov.uk/log-in-register-hmrc-online-services/problems-signing-in"
  lazy val generalQueriesUrl = "https://www.gov.uk/contact-hmrc"

  lazy val nationalInsuranceFormPartialLinkUrl =
    s"$formFrontendService/digital-forms/forms/personal-tax/national-insurance/catalogue"
  lazy val selfAssessmentFormPartialLinkUrl =
    s"$formFrontendService/digital-forms/forms/personal-tax/self-assessment/catalogue"

  lazy val identityVerificationUpliftUrl = s"$identityVerificationHost/$identityVerificationPrefix/uplift"
  lazy val multiFactorAuthenticationUpliftUrl = s"$basGatewayFrontendHost/bas-gateway/uplift-mfa"
  lazy val tcsChangeAddressUrl = s"$tcsFrontendHost/tax-credits-service/personal/change-address"
  lazy val tcsServiceRouterUrl = s"$tcsFrontendHost/tax-credits-service/renewals/service-router"
  lazy val updateAddressShortFormUrl = "https://www.tax.service.gov.uk/shortforms/form/PAYENICoC"
  lazy val changeNameLinkUrl =
    s"$dfsFrontendHost/digital-forms/form/notification-of-a-change-in-personal-details/draft/guide"
  lazy val changePersonalDetailsUrl =
    "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/change-your-personal-details"
  lazy val scottishRateIncomeTaxUrl = "https://www.gov.uk/scottish-rate-income-tax/how-it-works"
  lazy val personalAccountYourAddress = "/personal-account/your-address"
  lazy val personalAccount = "/personal-account"

  lazy val childBenefitsStaysInEducation =
    s"$dfsFrontendHost/digital-forms/form/Tell-Child-Benefit-about-your-child-staying-in-non-advanced-education-or-approved-training/draft/guide"
  lazy val childBenefitsLaterLeavesEducation =
    s"$dfsFrontendHost/digital-forms/form/Tell-Child-Benefit-about-your-child-leaving-non-advanced-education-or-approved-training/draft/guide"
  lazy val childBenefitsHasAnyChangeInCircumstances =
    s"$dfsFrontendHost/digital-forms/form/child-benefit-child-change-of-circumstances/draft/guide"
  lazy val childBenefitsApplyForExtension =
    s"$dfsFrontendHost/digital-forms/form/Application-for-extension-of-Child-Benefit/draft/guide"
  lazy val childBenefitsReportChange =
    s"$dfsFrontendHost/digital-forms/form/Child-Benefit-Claimant-Change-of-Circumstances/draft/guide"
  lazy val childBenefitsAuthoriseTaxAdvisor =
    s"$dfsFrontendHost/digital-forms/form/authorise-a-tax-adviser-for-high-income-child-benefit-charge-matters/draft/guide"
  lazy val childBenefitsStopOrRestart =
    s"$dfsFrontendHost/digital-forms/form/high-income-child-benefit-tax-charge/draft/guide"
  lazy val childBenefitsCheckIfYouCanClaim = "https://www.gov.uk/child-benefit/overview"

  lazy val nationalInsuranceRecordUrl = s"$nispFrontendHost/check-your-state-pension/account/nirecord/pta"

  lazy val enrolmentStoreProxyUrl = s"$enrolmentStoreProxyService/enrolment-store-proxy"

  // Links back to pertax
  lazy val pertaxFrontendHomeUrl = pertaxFrontendHost + routes.HomeController.index().url
  lazy val pertaxFrontendBackLink = runModeConfiguration
    .get[String]("external-url.pertax-frontend.host") + routes.HomeController.index().url

  // Links to sign out
  lazy val citizenAuthFrontendSignOut = citizenAuthHost + "/ida/signout"

  lazy val welshLangEnabled = langs.availables.exists(l => l.code == "cy")
  lazy val taxCreditsEnabled =
    runModeConfiguration.getOptional[String]("feature.tax-credits.enabled").getOrElse("true").toBoolean

  // Only used in HomeControllerSpec
  lazy val allowLowConfidenceSAEnabled =
    runModeConfiguration.getOptional[String]("feature.allow-low-confidence-sa.enabled").getOrElse("false").toBoolean
  lazy val ltaEnabled = runModeConfiguration.getOptional[String]("feature.lta.enabled").getOrElse("true").toBoolean
  lazy val allowSaPreview =
    runModeConfiguration.getOptional[String]("feature.allow-sa-preview.enabled").getOrElse("false").toBoolean

  lazy val bannerLinkUrl = runModeConfiguration.getOptional[String]("feature.ur-link.url")

  lazy val taxcalcEnabled =
    runModeConfiguration.getOptional[String]("feature.taxcalc.enabled").getOrElse("true").toBoolean
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
  lazy val closePostalAddressEnabled =
    runModeConfiguration.getOptional[String]("feature.close-postal-address.enabled").getOrElse("false").toBoolean

  lazy val getNinoFromCID =
    runModeConfiguration.getOptional[Boolean]("feature.get-nino-from-cid.enabled").getOrElse(false)

  val enc = URLEncoder.encode(_: String, "UTF-8")

  lazy val assetsPrefix = runModeConfiguration.get[String](s"assets.url") + runModeConfiguration
    .get[String](s"assets.version") + '/'

  lazy val sessionTimeoutInSeconds = runModeConfiguration.getOptional[Int]("ptaSession.timeout").getOrElse(900)
  lazy val sessionTimeoutInMinutes = sessionTimeoutInSeconds / 60
  lazy val sessionCountdownInSeconds = runModeConfiguration.getOptional[Int]("ptaSession.countdown").getOrElse(120)

  def getFeedbackSurveyUrl(origin: Origin): String =
    feedbackSurveyFrontendHost + "/feedback/" + enc(origin.origin)

  def getBasGatewayFrontendSignOutUrl(continueUrl: String): String =
    basGatewayFrontendHost + s"/bas-gateway/sign-out-without-state?continue=$continueUrl"

  lazy val editAddressTtl: Int = runModeConfiguration.getOptional[Int]("mongodb.editAddressTtl").getOrElse(0)

  lazy val saPartialReturnLinkText = "Back to account home"

  lazy val isNationalInsuranceCardEnabled: Boolean =
    runModeConfiguration
      .getOptional[String]("feature.national-insurance-tile.enabled")
      .getOrElse("false")
      .toBoolean
}

trait TaxcalcUrls {
  self: ConfigDecorator =>

  def underpaidUrlReasons(taxYear: Int) =
    s"${self.taxCalcFrontendHost}/tax-you-paid/$taxYear-${taxYear + 1}/paid-too-little/reasons"
  def overpaidUrlReasons(taxYear: Int) =
    s"${self.taxCalcFrontendHost}/tax-you-paid/$taxYear-${taxYear + 1}/paid-too-much/reasons"

  def underpaidUrl(taxYear: Int) = s"${self.taxCalcFrontendHost}/tax-you-paid/$taxYear-${taxYear + 1}/paid-too-little"
  def overpaidUrl(taxYear: Int) = s"${self.taxCalcFrontendHost}/tax-you-paid/$taxYear-${taxYear + 1}/paid-too-much"

  def rightAmountUrl(taxYear: Int) = s"${self.taxCalcFrontendHost}/tax-you-paid/$taxYear-${taxYear + 1}/right-amount"
  def notEmployedUrl(taxYear: Int) = s"${self.taxCalcFrontendHost}/tax-you-paid/$taxYear-${taxYear + 1}/not-employed"
  def notCalculatedUrl(taxYear: Int) =
    s"${self.taxCalcFrontendHost}/tax-you-paid/$taxYear-${taxYear + 1}/not-yet-calculated"

  lazy val taxPaidUrl = s"${self.taxCalcFrontendHost}/tax-you-paid/status"

  val makePaymentUrl = "https://www.gov.uk/simple-assessment"

}

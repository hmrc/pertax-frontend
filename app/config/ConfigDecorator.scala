/*
 * Copyright 2020 HM Revenue & Customs
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

import java.net.{URL, URLEncoder}

import controllers.routes
import com.google.inject.{Inject, Singleton}
import org.joda.time.LocalDate
import play.api.Mode.Mode
import play.api.i18n.{Lang, Langs}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.binders.Origin
import uk.gov.hmrc.play.config.ServicesConfig

@Singleton
class ConfigDecorator @Inject()(environment: Environment, configuration: Configuration, langs: Langs)
    extends ServicesConfig with TaxcalcUrls {

  val mode: Mode = environment.mode
  val runModeConfiguration: Configuration = configuration

  // Define the web contexts to access the IV-FE and AUTH frontend applications.
  lazy val ivfe_web_context = decorateUrlForLocalDev(s"identity-verification.web-context").getOrElse("mdtp")
  lazy val ida_web_context = decorateUrlForLocalDev(s"ida.web-context").getOrElse("ida")
  lazy val gg_web_context = decorateUrlForLocalDev(s"gg.web-context").getOrElse("gg/sign-in")

  lazy val authProviderChoice = configuration.getString(s"external-url.auth-provider-choice.host").getOrElse("")

  val defaultOrigin = Origin("PERTAX")

  val authProviderGG = "GGW"
  val authProviderVerify = "IDA"

  def currentLocalDate: LocalDate = LocalDate.now()

  private lazy val contactFrontendService = baseUrl("contact-frontend")
  private lazy val messageFrontendService = baseUrl("message-frontend")
  private lazy val formFrontendService = baseUrl("dfs-frontend")
  lazy val pertaxFrontendService = baseUrl("pertax-frontend")
  lazy val businessTaxAccountService = baseUrl("business-tax-account")
  lazy val tcsFrontendService = baseUrl("tcs-frontend")
  private lazy val payApiUrl = baseUrl("pay-api")
  lazy val authLoginApiService = baseUrl("auth-login-api")
  private lazy val enrolmentStoreProxyService = baseUrl("enrolment-store-proxy")

  private def decorateUrlForLocalDev(key: String): Option[String] =
    configuration.getString(s"external-url.$key").filter(_ => env == "Dev")

  //These hosts should be empty for Prod like environments, all frontend services run on the same host so e.g localhost:9030/tai in local should be /tai in prod
  lazy val contactHost = decorateUrlForLocalDev(s"contact-frontend.host").getOrElse("")
  lazy val citizenAuthHost = decorateUrlForLocalDev(s"citizen-auth.host").getOrElse("")
  lazy val companyAuthHost = decorateUrlForLocalDev(s"company-auth.host").getOrElse("")
  lazy val companyAuthFrontendHost = decorateUrlForLocalDev(s"company-auth-frontend.host").getOrElse("")
  lazy val taiHost = decorateUrlForLocalDev(s"tai-frontend.host").getOrElse("")
  lazy val fandfHost = decorateUrlForLocalDev(s"fandf-frontend.host").getOrElse("")
  lazy val tamcHost = decorateUrlForLocalDev(s"tamc-frontend.host").getOrElse("")
  lazy val formTrackingHost = decorateUrlForLocalDev(s"tracking-frontend.host").getOrElse("")
  lazy val businessTaxAccountHost = decorateUrlForLocalDev(s"business-tax-account.host").getOrElse("")
  lazy val identityVerificationHost = decorateUrlForLocalDev(s"identity-verification.host").getOrElse("")
  lazy val basGatewayFrontendHost = decorateUrlForLocalDev(s"bas-gateway-frontend.host").getOrElse("")
  lazy val pertaxFrontendHost = decorateUrlForLocalDev(s"pertax-frontend.host").getOrElse("")
  lazy val feedbackSurveyFrontendHost = decorateUrlForLocalDev(s"feedback-survey-frontend.host").getOrElse("")
  lazy val tcsFrontendHost = decorateUrlForLocalDev(s"tcs-frontend.host").getOrElse("")
  lazy val nispFrontendHost = decorateUrlForLocalDev(s"nisp-frontend.host").getOrElse("")
  lazy val taxCalcFrontendHost = decorateUrlForLocalDev(s"taxcalc-frontend.host").getOrElse("")
  lazy val taxCalcHost = decorateUrlForLocalDev("taxcalc.host").getOrElse("")
  lazy val dfsFrontendHost = decorateUrlForLocalDev(s"dfs-frontend.host").getOrElse("")
  lazy val plaBackEndHost = decorateUrlForLocalDev(s"pensions-lifetime-allowance.host").getOrElse("")
  lazy val saFrontendHost = decorateUrlForLocalDev(s"sa-frontend.host").getOrElse("")
  lazy val governmentGatewayLostCredentialsFrontendHost =
    decorateUrlForLocalDev(s"government-gateway-lost-credentials-frontend.host").getOrElse("")
  lazy val governmentGatewayRegistrationFrontendHost =
    decorateUrlForLocalDev(s"government-gateway-registration-frontend.host").getOrElse("")
  lazy val enrolmentManagementFrontendHost = decorateUrlForLocalDev(s"enrolment-management-frontend.host").getOrElse("")
  lazy val ssoUrl = decorateUrlForLocalDev("sso-portal.host")

  lazy val portalBaseUrl = configuration.getString("external-url.sso-portal.host").getOrElse("")
  def toPortalUrl(path: String) = new URL(portalBaseUrl + path)
  lazy val frontendTemplatePath: String =
    configuration.getString("microservice.services.frontend-template-provider.path").getOrElse("/template/mustache")
  lazy val frontendPath: String = configuration
    .getString("microservice.services.frontend-template-provider.protocol")
    .getOrElse("")
    .concat("://" + configuration.getString("microservice.services.frontend-template-provider.host").getOrElse(""))
    .concat(":" + configuration.getString("microservice.services.frontend-template-provider.port").getOrElse(""))
  def ssoifyUrl(url: URL) =
    s"$companyAuthFrontendHost/ssoout/non-digital?continue=" + URLEncoder.encode(url.toString, "UTF-8")

  def sa302Url(saUtr: String, taxYear: String) =
    s"/self-assessment-file/$taxYear/ind/$saUtr/return/viewYourCalculation/reviewYourFullCalculation"

  def completeYourTaxReturnUrl(saUtr: String, taxYear: String, lang: Lang) =
    s"$saFrontendHost/self-assessment-file/$taxYear/ind/$saUtr/return?lang=" + (if (lang.code equals ("en")) "eng"
                                                                                else "cym")
  lazy val ssoToActivateSaEnrolmentPinUrl =
    s"$enrolmentManagementFrontendHost/enrolment-management-frontend/IR-SA/get-access-tax-scheme?continue=/personal-account"
  lazy val ssoToRegisterForSaEnrolment = ssoifyUrl(toPortalUrl("/home/services/enroll"))
  lazy val ssoToRegistration = ssoifyUrl(toPortalUrl("/registration"))
  def ssoToSaAccountSummaryUrl(saUtr: String, taxYear: String) =
    ssoifyUrl(toPortalUrl(s"/self-assessment/ind/$saUtr/taxreturn/$taxYear/options"))

  def betaFeedbackUnauthenticatedUrl(aDeskproToken: String) =
    s"$contactHost/contact/beta-feedback-unauthenticated?service=$aDeskproToken"
  lazy val analyticsToken = configuration.getString(s"google-analytics.token")
  lazy val analyticsHost = Some(configuration.getString(s"google-analytics.host").getOrElse("service.gov.uk"))
  lazy val reportAProblemPartialUrl = s"$contactFrontendService/contact/problem_reports"
  lazy val makeAPaymentUrl = s"$payApiUrl/pay-api/pta/sa/journey/start"
  lazy val getPaymentsUrl = s"$payApiUrl/pay-api/payment/search/PTA"
  lazy val deskproToken = "PTA"
  lazy val citizenSwitchOffUrl = s"$citizenAuthHost/attorney/switch-off-act"
  lazy val taxEstimateServiceUrl = s"$taiHost/check-income-tax/paye"
  lazy val formTrackingServiceUrl = s"$formTrackingHost/track"
  lazy val messageInboxLinkUrl = s"$messageFrontendService/messages/inbox-link"
  lazy val fandfUrl = s"$fandfHost/trusted-helpers"
  def lostCredentialsChooseAccountUrl(continueUrl: String, forgottenOption: String) =
    s"$governmentGatewayLostCredentialsFrontendHost/government-gateway-lost-credentials-frontend/choose-your-account?continue=${enc(
      continueUrl)}&origin=${enc(defaultOrigin.toString)}&forgottenOption=$forgottenOption"
  lazy val notShownSaRecoverYourUserId =
    s"$governmentGatewayLostCredentialsFrontendHost/government-gateway-lost-credentials-frontend/choose-your-account-access?origin=${enc(defaultOrigin.toString)}"
  lazy val tamcTransferAllowanceUrl = s"$tamcHost/marriage-allowance-application/history"
  lazy val incomeTaxFormsUrl = "https://www.gov.uk/government/collections/hmrc-forms"
  lazy val onlineServicesHelpdeskUrl =
    "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/online-services-helpdesk"
  lazy val contactHrmcUrl = "https://www.gov.uk/contact-hmrc"
  lazy val selfAssessmentEnrolUrl =
    s"$enrolmentManagementFrontendHost/enrolment-management-frontend/IR-SA/request-access-tax-scheme?continue=/personal-account"
  lazy val selfAssessmentContactUrl =
    "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/self-assessment"
  lazy val ggLoginUrl = configuration.getString(s"ggLogin.url").getOrElse("")
  lazy val origin =
    configuration.getString("sosOrigin").orElse(configuration.getString("appName")).getOrElse("undefined")

  lazy val signinGGUrl = "https://www.tax.service.gov.uk/account"
  lazy val lostUserIdWithSa =
    "https://www.tax.service.gov.uk/account-recovery/disabled-user-id/check-email/IndividualWithSA"

  lazy val lostUserIdUrl = "https://www.tax.service.gov.uk/account-recovery/choose-account-type/lost-userid"
  lazy val lostPasswordUrl = "https://www.tax.service.gov.uk/account-recovery/choose-account-type/lost-password"
  lazy val problemsSigningInUrl = "https://www.gov.uk/log-in-register-hmrc-online-services/problems-signing-in"
  lazy val taxReturnByPostUrl = "https://www.gov.uk/government/publications/self-assessment-tax-return-sa100"
  lazy val hmrcProblemsSigningIn = "https://www.gov.uk/log-in-register-hmrc-online-services/problems-signing-in"
  lazy val generalQueriesUrl = "https://www.gov.uk/contact-hmrc"
  lazy val mainContentHeaderPartialUrl = s"$pertaxFrontendService/personal-account/integration/main-content-header"

  lazy val nationalInsuranceFormPartialLinkUrl = s"$formFrontendService/forms/personal-tax/national-insurance/catalogue"
  lazy val childBenefitCreditFormPartialLinkUrl =
    s"$formFrontendService/forms/personal-tax/benefits-and-credits/catalogue"
  lazy val selfAssessmentFormPartialLinkUrl = s"$formFrontendService/forms/personal-tax/self-assessment/catalogue"
  lazy val pensionFormPartialLinkUrl = s"$formFrontendService/forms/personal-tax/pensions/catalogue"
  lazy val businessTaxAccountUrl = s"$businessTaxAccountHost/business-account"
  lazy val identityVerificationUpliftUrl = s"$identityVerificationHost/$ivfe_web_context/uplift"
  lazy val multiFactorAuthenticationUpliftUrl = s"$basGatewayFrontendHost/bas-gateway/uplift-mfa"
  lazy val taxYouPaidStatus = s"$taxCalcFrontendHost/tax-you-paid/status"
  lazy val tcsHomeUrl = s"$tcsFrontendHost/tax-credits-service/renewals/service-router"
  lazy val tcsChangeAddressUrl = s"$tcsFrontendHost/tax-credits-service/personal/change-address"
  lazy val tcsServiceRouterUrl = s"$tcsFrontendHost/tax-credits-service/renewals/service-router"
  lazy val updateAddressShortFormUrl = "https://www.tax.service.gov.uk/shortforms/form/PAYENICoC"
  lazy val changeNameLinkUrl = s"$dfsFrontendHost/forms/form/notification-of-a-change-in-personal-details/new"
  lazy val changePersonalDetailsUrl =
    "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/change-your-personal-details"
  lazy val scottishRateIncomeTaxUrl = "https://www.gov.uk/scottish-rate-income-tax/how-it-works"
  lazy val personalAccountYourAddress = "/personal-account/your-address"
  lazy val personalAccount = "/personal-account"

  lazy val childBenefitsStaysInEducation =
    s"$dfsFrontendHost/forms/form/Tell-Child-Benefit-about-your-child-staying-in-non-advanced-education-or-approved-training/guide"
  lazy val childBenefitsLaterLeavesEducation =
    s"$dfsFrontendHost/forms/form/Tell-Child-Benefit-about-your-child-leaving-non-advanced-education-or-approved-training/guide"
  lazy val childBenefitsHasAnyChangeInCircumstances =
    s"$dfsFrontendHost/forms/form/child-benefit-child-change-of-circumstances/guide"
  lazy val childBenefitsApplyForExtension =
    s"$dfsFrontendHost/forms/form/Application-for-extension-of-Child-Benefit/guide"
  lazy val childBenefitsReportChange =
    s"$dfsFrontendHost/forms/form/Child-Benefit-Claimant-Change-of-Circumstances/guide"
  lazy val childBenefitsAuthoriseTaxAdvisor =
    s"$dfsFrontendHost/forms/form/authorise-a-tax-adviser-for-high-income-child-benefit-charge-matters/new"
  lazy val childBenefitsStopOrRestart = s"$dfsFrontendHost/forms/form/high-income-child-benefit-tax-charge/guide"
  lazy val childBenefitsCheckIfYouCanClaim = "https://www.gov.uk/child-benefit/overview"

  lazy val nationalInsuranceRecordUrl = s"$nispFrontendHost/check-your-state-pension/account/nirecord/pta"
  lazy val myStatePensionAccount = s"$nispFrontendHost/check-your-state-pension/account/pta"
  lazy val lifetimeProtectionAllowance = s"$dfsFrontendHost/protect-your-lifetime-allowance/existing-protections"

  lazy val enrolmentStoreProxyUrl = s"$enrolmentStoreProxyService/enrolment-store-proxy"

  lazy val marriageAllowanceSalaryAmount = "£11,500"

  // Links back to pertax
  lazy val pertaxFrontendHomeUrl = pertaxFrontendHost + routes.HomeController.index().url
  lazy val pertaxFrontendBackLink = configuration
    .getString("external-url.pertax-frontend.host")
    .getOrElse("") + routes.HomeController.index().url

  // Links to sign out
  lazy val citizenAuthFrontendSignOut = citizenAuthHost + "/ida/signout"

  lazy val welshLangEnabled = langs.availables.exists(l => l.code == "cy")
  lazy val taxCreditsEnabled = configuration.getString("feature.tax-credits.enabled").getOrElse("true").toBoolean
  lazy val activateSALinkEnabled =
    configuration.getString("feature.activate-sa-link.enabled").getOrElse("true").toBoolean
  lazy val allowLowConfidenceSAEnabled =
    configuration.getString("feature.allow-low-confidence-sa.enabled").getOrElse("false").toBoolean
  lazy val ltaEnabled = configuration.getString("feature.lta.enabled").getOrElse("true").toBoolean
  lazy val urLinkUrl = configuration.getString("feature.ur-link.url")
  lazy val platformFrontendHost = configuration.getString("platform.frontend.host").getOrElse("")

  lazy val taxcalcEnabled = configuration.getString("feature.taxcalc.enabled").getOrElse("true").toBoolean
  lazy val taxComponentsEnabled = configuration.getString("feature.tax-components.enabled").getOrElse("true").toBoolean
  lazy val saReminderBannerEnabled = configuration.getString("feature.sa-banner.enabled").getOrElse("true").toBoolean
  lazy val nispEnabled = configuration.getString("feature.nisp.enabled").getOrElse("true").toBoolean
  lazy val allowSaPreview = configuration.getString("feature.allow-sa-preview.enabled").getOrElse("false").toBoolean
  lazy val taxCreditsPaymentLinkEnabled =
    configuration.getString("feature.tax-credits-payment-link.enabled").getOrElse("true").toBoolean
  lazy val saveNiLetterAsPdfLinkEnabled =
    configuration.getString("feature.save-ni-letter-as-pdf.enabled").getOrElse("false").toBoolean
  lazy val updateInternationalAddressInPta =
    configuration.getString("feature.update-international-address-form.enabled").getOrElse("false").toBoolean
  lazy val closePostalAddressEnabled =
    configuration.getString("feature.close-postal-address.enabled").getOrElse("false").toBoolean

  lazy val egainWebchatPertaxId = configuration.getString(s"egain-webchat.pertax.id").getOrElse("TT55004894")

  val enc = URLEncoder.encode(_: String, "UTF-8")

  lazy val assetsPrefix = configuration.getString(s"assets.url").getOrElse("") + configuration
    .getString(s"assets.version")
    .getOrElse("") + '/'
  lazy val assetsUrl = configuration.getString(s"assets.url").getOrElse("")
  lazy val assetsVersion = configuration.getString(s"assets.version").getOrElse("")
  lazy val mongoUrl = configuration.getString("mongodb.uri").getOrElse("")

  lazy val sessionTimeoutInSeconds = configuration.getInt("session.timeout").getOrElse(1800)
  lazy val sessionTimeoutInMinutes = sessionTimeoutInSeconds / 60
  lazy val sessionCountdownInSeconds = configuration.getInt("session.countdown").getOrElse(120)

  def getFeedbackSurveyUrl(origin: Origin): String =
    feedbackSurveyFrontendHost + "/feedback/" + enc(origin.origin)

  def getCompanyAuthFrontendSignOutUrl(continueUrl: String): String =
    companyAuthHost + s"/gg/sign-out?continue=$continueUrl"

  lazy val editAddressTtl: Int = configuration.getInt("mongodb.editAddressTtl").getOrElse(86400)

}

trait TaxcalcUrls {
  self: ConfigDecorator =>

  def reconciliationsUrl(nino: String, startYear: Int, endYear: Int) =
    s"${self.taxCalcHost}/$nino/$startYear/$endYear/reconciliations"

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

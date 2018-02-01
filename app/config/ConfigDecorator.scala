/*
 * Copyright 2018 HM Revenue & Customs
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
import javax.inject.{Inject, Singleton}

import controllers.bindable.Origin
import controllers.routes
import play.api.Configuration
import play.api.i18n.Langs
import uk.gov.hmrc.play.config.ServicesConfig

@Singleton
class ConfigDecorator @Inject() (configuration: Configuration, langs: Langs) extends ServicesConfig {

  // Define the web contexts to access the IV-FE and AUTH frontend applications.
  lazy val ivfe_web_context = decorateUrlForLocalDev(s"identity-verification.web-context").getOrElse("mdtp")
  lazy val ida_web_context  = decorateUrlForLocalDev(s"ida.web-context").getOrElse("ida")
  lazy val gg_web_context   = decorateUrlForLocalDev(s"gg.web-context").getOrElse("gg")

  val defaultOrigin = Origin("PERTAX")

  private lazy val contactFrontendService = baseUrl("contact-frontend")
  private lazy val messageFrontendService = baseUrl("message-frontend")
  private lazy val formFrontendService = baseUrl("dfs-frontend")
  lazy val pertaxFrontendService = baseUrl("pertax-frontend")
  lazy val businessTaxAccountService = baseUrl("business-tax-account")
  lazy val tcsFrontendService = baseUrl("tcs-frontend")

  private def decorateUrlForLocalDev(key: String): Option[String] = configuration.getString(s"external-url.$key").filter(x => env=="Dev")

  //These hosts should be empty for Prod like environments, all frontend services run on the same host so e.g localhost:9030/tai in local should be /tai in prod
  lazy val contactHost                                  = decorateUrlForLocalDev(s"contact-frontend.host").getOrElse("")
  lazy val citizenAuthHost                              = decorateUrlForLocalDev(s"citizen-auth.host").getOrElse("")
  lazy val companyAuthHost                              = decorateUrlForLocalDev(s"company-auth.host").getOrElse("")
  lazy val companyAuthFrontendHost                      = decorateUrlForLocalDev(s"company-auth-frontend.host").getOrElse("")
  lazy val taiHost                                      = decorateUrlForLocalDev(s"tai-frontend.host").getOrElse("")
  lazy val fandfHost                                    = decorateUrlForLocalDev(s"fandf-frontend.host").getOrElse("")
  lazy val tamcHost                                     = decorateUrlForLocalDev(s"tamc-frontend.host").getOrElse("")
  lazy val formTrackingHost                             = decorateUrlForLocalDev(s"tracking-frontend.host").getOrElse("")
  lazy val businessTaxAccountHost                       = decorateUrlForLocalDev(s"business-tax-account.host").getOrElse("")
  lazy val identityVerificationHost                     = decorateUrlForLocalDev(s"identity-verification.host").getOrElse("")
  lazy val pertaxFrontendHost                           = decorateUrlForLocalDev(s"pertax-frontend.host").getOrElse("")
  lazy val feedbackSurveyFrontendHost                   = decorateUrlForLocalDev(s"feedback-survey-frontend.host").getOrElse("")
  lazy val tcsFrontendHost                              = decorateUrlForLocalDev(s"tcs-frontend.host").getOrElse("")
  lazy val nispFrontendHost                             = decorateUrlForLocalDev(s"nisp-frontend.host").getOrElse("")
  lazy val taxCalcFrontendHost                          = decorateUrlForLocalDev(s"taxcalc-frontend.host").getOrElse("")
  lazy val dfsFrontendHost                              = decorateUrlForLocalDev(s"dfs-frontend.host").getOrElse("")
  lazy val plaBackEndHost                               = decorateUrlForLocalDev(s"pensions-lifetime-allowance.host").getOrElse("")
  lazy val governmentGatewayLostCredentialsFrontendHost = decorateUrlForLocalDev(s"government-gateway-lost-credentials-frontend.host").getOrElse("")
  lazy val governmentGatewayRegistrationFrontendHost    = decorateUrlForLocalDev(s"government-gateway-registration-frontend.host").getOrElse("")


  lazy val portalBaseUrl = configuration.getString("external-url.portal.host").getOrElse("")
  def toPortalUrl(path: String) = new URL(portalBaseUrl + path)

  def ssoifyUrl(url: URL) = {
    s"$companyAuthFrontendHost/ssoout/non-digital?continue=" + URLEncoder.encode(url.toString, "UTF-8")
  }

  def sa302Url(saUtr: String, taxYear: String) = s"/self-assessment-file/$taxYear/ind/$saUtr/return/viewYourCalculation/reviewYourFullCalculation"

  def completeYourTaxReturnUrl(saUtr: String, taxYear: String) = ssoifyUrl(toPortalUrl(s"/self-assessment-file/$taxYear/ind/$saUtr/return?lang=eng"))
  lazy val ssoToActivateSaEnrolmentPinUrl = ssoifyUrl(toPortalUrl("/service/self-assessment?action=activate&step=enteractivationpin"))
  lazy val ssoToRegisterForSaEnrolment = ssoifyUrl(toPortalUrl("/home/services/enroll"))
  lazy val ssoToRegistration = ssoifyUrl(toPortalUrl("/registration"))
  def ssoToSaAccountSummaryUrl(saUtr: String, taxYear: String) = ssoifyUrl(toPortalUrl(s"/self-assessment/ind/$saUtr/taxreturn/$taxYear/options"))

  def betaFeedbackUnauthenticatedUrl(aDeskproToken: String) = s"$contactHost/contact/beta-feedback-unauthenticated?service=$aDeskproToken"
  lazy val analyticsToken = configuration.getString(s"google-analytics.token")
  lazy val analyticsHost = Some(configuration.getString(s"google-analytics.host").getOrElse("service.gov.uk"))
  lazy val ssoUrl = configuration.getString(s"portal.ssoUrl")
  lazy val reportAProblemPartialUrl = s"$contactFrontendService/contact/problem_reports"
  lazy val deskproToken = "PTA"
  lazy val citizenSwitchOffUrl = s"$citizenAuthHost/attorney/switch-off-act"
  lazy val taxEstimateServiceUrl = s"$taiHost/check-income-tax/paye"
  lazy val formTrackingServiceUrl = s"$formTrackingHost/track"
  lazy val messageInboxLinkUrl = s"$messageFrontendService/messages/inbox-link"
  lazy val fandfUrl = s"$fandfHost/trusted-helpers"
  def lostCredentialsChooseAccountUrl(continueUrl: String, forgottenOption: String) = {
    s"$governmentGatewayLostCredentialsFrontendHost/government-gateway-lost-credentials-frontend/choose-your-account?continue=${enc(continueUrl)}&origin=${enc(defaultOrigin.toString)}&forgottenOption=$forgottenOption"
  }
  lazy val notShownSaRecoverYourUserId = s"$governmentGatewayLostCredentialsFrontendHost/government-gateway-lost-credentials-frontend/choose-your-account-access?origin=${enc(defaultOrigin.toString)}"
  lazy val tamcTransferAllowanceUrl = s"$tamcHost/marriage-allowance-application/history"
  lazy val incomeTaxFormsUrl = "https://www.gov.uk/government/collections/hmrc-forms"
  lazy val selfAssessmentFormsAndHelpsheetsUrl = "https://www.gov.uk/self-assessment-forms-and-helpsheets"
  lazy val onlineServicesHelpdeskUrl = "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/online-services-helpdesk"
  lazy val contactHrmcUrl = "https://www.gov.uk/contact-hmrc"
  lazy val selfAssessmentContactUrl = "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/self-assessment"
  def registerForSelfAssessmentUrl(continueUrl: String) = {
    s"$governmentGatewayRegistrationFrontendHost/government-gateway-registration-frontend/are-you-trying-to-file-for-sa?continue=${enc(continueUrl)}&origin=${enc(defaultOrigin.toString)}"
  }
  lazy val taxReturnByPostUrl = "https://www.gov.uk/government/publications/self-assessment-tax-return-sa100"
  lazy val hmrcProblemsSigningIn = "https://www.gov.uk/log-in-register-hmrc-online-services/problems-signing-in"
  lazy val incomeTaxGeneralQueriesUrl = "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/income-tax-enquiries-for-individuals-pensioners-and-employees"
  lazy val mainContentHeaderPartialUrl = s"$pertaxFrontendService/personal-account/integration/main-content-header"
  lazy val refreshInterval = 900 + 10 //FIXME this should be sourced from the AuthenticationProvider
  lazy val enableRefresh = configuration.getBoolean("enableRefresh").getOrElse(true)

  lazy val nationalInsuranceFormPartialLinkUrl = s"$formFrontendService/forms/personal-tax/national-insurance/catalogue"
  lazy val childBenefitCreditFormPartialLinkUrl = s"$formFrontendService/forms/personal-tax/benefits-and-credits/catalogue"
  lazy val selfAssessmentFormPartialLinkUrl = s"$formFrontendService/forms/personal-tax/self-assessment/catalogue"
  lazy val pensionFormPartialLinkUrl = s"$formFrontendService/forms/personal-tax/pensions/catalogue"
  lazy val businessTaxAccountUrl = s"$businessTaxAccountHost/business-account"
  lazy val identityVerificationUpliftUrl = s"$identityVerificationHost/$ivfe_web_context/uplift"
  lazy val taxYouPaidStatus = s"$taxCalcFrontendHost/tax-you-paid/status"
  lazy val tcsHomeUrl = s"$tcsFrontendHost/tax-credits-service/renewals/service-router"
  lazy val tcsChangeAddressUrl = s"$tcsFrontendHost/tax-credits-service/personal/change-address"
  lazy val tcsServiceRouterUrl = s"$tcsFrontendHost/tax-credits-service/renewals/service-router"

  lazy val childBenefitsStaysInEducation = s"$dfsFrontendHost/forms/form/Tell-Child-Benefit-about-your-child-staying-in-non-advanced-education-or-approved-training/guide"
  lazy val childBenefitsLaterLeavesEducation = s"$dfsFrontendHost/forms/form/Tell-Child-Benefit-about-your-child-leaving-non-advanced-education-or-approved-training/guide"
  lazy val childBenefitsHasAnyChangeInCircumstances = s"$dfsFrontendHost/forms/form/child-benefit-child-change-of-circumstances/guide"
  lazy val childBenefitsApplyForExtension = s"$dfsFrontendHost/forms/form/Application-for-extension-of-Child-Benefit/guide"
  lazy val childBenefitsReportChange = s"$dfsFrontendHost/forms/form/Child-Benefit-Claimant-Change-of-Circumstances/guide"
  lazy val childBenefitsAuthoriseTaxAdvisor = s"$dfsFrontendHost/forms/form/authorise-a-tax-adviser-for-high-income-child-benefit-charge-matters/new"
  lazy val childBenefitsStopOrRestart = s"$dfsFrontendHost/forms/form/high-income-child-benefit-tax-charge/guide"
  lazy val childBenefitsCheckIfYouCanClaim = "https://www.gov.uk/child-benefit/overview"

  lazy val nationalInsuranceRecordUrl = s"$nispFrontendHost/check-your-state-pension/account/nirecord/pta"
  lazy val myStatePensionAccount = s"$nispFrontendHost/check-your-state-pension/account/pta"
  lazy val lifetimeProtectionAllowance = s"$dfsFrontendHost/protect-your-lifetime-allowance/existing-protections"

  lazy val marriageAllowanceSalaryAmount = "£11,500"

  // Links back to pertax
  lazy val pertaxFrontendHomeUrl = pertaxFrontendHost + routes.ApplicationController.index().url

  // Links to sign out
  lazy val citizenAuthFrontendSignOut = citizenAuthHost + "/ida/signout"

  lazy val welshLangEnabled = langs.availables.exists(l => l.code == "cy")
  lazy val taxCreditsEnabled = configuration.getString("feature.tax-credits.enabled").getOrElse("true").toBoolean
  lazy val activateSALinkEnabled = configuration.getString("feature.activate-sa-link.enabled").getOrElse("true").toBoolean
  lazy val allowLowConfidenceSAEnabled = configuration.getString("feature.allow-low-confidence-sa.enabled").getOrElse("false").toBoolean
  lazy val ltaEnabled = configuration.getString("feature.lta.enabled").getOrElse("true").toBoolean
  lazy val urLinkUrl = configuration.getString("feature.ur-link.url")

  lazy val taxcalcEnabled = configuration.getString("feature.taxcalc.enabled").getOrElse("true").toBoolean
  lazy val taxSummaryEnabled = configuration.getString("feature.tax-summary.enabled").getOrElse("true").toBoolean
  lazy val saReminderBannerEnabled = configuration.getString("feature.sa-banner.enabled").getOrElse("true").toBoolean
  lazy val nispEnabled = configuration.getString("feature.nisp.enabled").getOrElse("true").toBoolean
  lazy val allowSaPreview = configuration.getString("feature.allow-sa-preview.enabled").getOrElse("false").toBoolean

  lazy val egainWebchatPertaxId = configuration.getString(s"egain-webchat.pertax.id ").getOrElse("TT55004894")

  val enc = URLEncoder.encode(_: String, "UTF-8")

  lazy val assetsPrefix = configuration.getString(s"assets.url").getOrElse("") + configuration.getString(s"assets.version").getOrElse("") + '/'

  def getFeedbackSurveyUrl(origin: Origin): String = {
    feedbackSurveyFrontendHost + "/feedback-survey?origin=" + enc(origin.value)
  }

  def getCompanyAuthFrontendSignOutUrl(continueUrl: String): String = {
    companyAuthHost + s"/gg/sign-out?continue=$continueUrl"
  }
}

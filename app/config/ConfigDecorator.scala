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
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}

@Singleton
class ConfigDecorator @Inject()(
  environment: Environment,
  runModeConfiguration: Configuration,
  runMode: RunMode,
  langs: Langs,
  servicesConfig: ServicesConfig
) extends TaxcalcUrls {

  val mode: Mode = environment.mode

  // Define the web contexts to access the IV-FE and AUTH frontend applications.
  lazy val ivfe_web_context = decorateUrlForLocalDev(s"identity-verification.web-context").getOrElse("mdtp")
  lazy val ida_web_context = decorateUrlForLocalDev(s"ida.web-context").getOrElse("ida")
  lazy val gg_web_context = decorateUrlForLocalDev(s"gg.web-context").getOrElse("gg/sign-in")

  lazy val authProviderChoice = runModeConfiguration.get[String](s"external-url.auth-provider-choice.host")

  val defaultOrigin = Origin("PERTAX")

  val authProviderGG = "GGW"
  val authProviderVerify = "IDA"

  def currentLocalDate: LocalDate = LocalDate.now()

  private lazy val contactFrontendService = servicesConfig.baseUrl("contact-frontend")
  private lazy val formFrontendService = servicesConfig.baseUrl("dfs-digital-forms-frontend")
  lazy val pertaxFrontendService = servicesConfig.baseUrl("pertax-frontend")
  lazy val businessTaxAccountService = servicesConfig.baseUrl("business-tax-account")

  private lazy val payApiUrl = servicesConfig.baseUrl("pay-api")

  private lazy val enrolmentStoreProxyService = servicesConfig.baseUrl("enrolment-store-proxy")

  private def decorateUrlForLocalDev(key: String): Option[String] =
    runModeConfiguration.getOptional[String](s"external-url.$key").filter(_ => runMode.env == "Dev")

  //These hosts should be empty for Prod like environments, all frontend services run on the same host so e.g localhost:9030/tai in local should be /tai in prod
  lazy val preferencesFrontendService = decorateUrlForLocalDev(s"preferences-frontend").getOrElse("")
  lazy val contactHost = decorateUrlForLocalDev(s"contact-frontend.host").getOrElse("")
  lazy val citizenAuthHost = decorateUrlForLocalDev(s"citizen-auth.host").getOrElse("")
  lazy val companyAuthHost = decorateUrlForLocalDev(s"company-auth.host").getOrElse("")
  lazy val companyAuthFrontendHost = decorateUrlForLocalDev(s"company-auth-frontend.host").getOrElse("")
  lazy val taiHost = decorateUrlForLocalDev(s"tai-frontend.host").getOrElse("")
//  lazy val fandfHost = decorateUrlForLocalDev(s"fandf-frontend.host").getOrElse("")
//  lazy val tamcHost = decorateUrlForLocalDev(s"tamc-frontend.host").getOrElse("")
  lazy val formTrackingHost = decorateUrlForLocalDev(s"tracking-frontend.host").getOrElse("")
//  lazy val businessTaxAccountHost = decorateUrlForLocalDev(s"business-tax-account.host").getOrElse("")
  lazy val identityVerificationHost = decorateUrlForLocalDev(s"identity-verification.host").getOrElse("")
  lazy val basGatewayFrontendHost = decorateUrlForLocalDev(s"bas-gateway-frontend.host").getOrElse("")
  lazy val pertaxFrontendHost = decorateUrlForLocalDev(s"pertax-frontend.host").getOrElse("")
  lazy val feedbackSurveyFrontendHost = decorateUrlForLocalDev(s"feedback-survey-frontend.host").getOrElse("")
  lazy val tcsFrontendHost = decorateUrlForLocalDev(s"tcs-frontend.host").getOrElse("")
  lazy val nispFrontendHost = decorateUrlForLocalDev(s"nisp-frontend.host").getOrElse("")
  lazy val taxCalcFrontendHost = decorateUrlForLocalDev(s"taxcalc-frontend.host").getOrElse("")
//  lazy val taxCalcHost = decorateUrlForLocalDev("taxcalc.host").getOrElse("")
  lazy val dfsFrontendHost = decorateUrlForLocalDev(s"dfs-digital-forms-frontend.host").getOrElse("")

  lazy val saFrontendHost = decorateUrlForLocalDev(s"sa-frontend.host").getOrElse("")
  lazy val governmentGatewayLostCredentialsFrontendHost =
    decorateUrlForLocalDev(s"government-gateway-lost-credentials-frontend.host").getOrElse("")

  lazy val enrolmentManagementFrontendHost = decorateUrlForLocalDev(s"enrolment-management-frontend.host").getOrElse("")
  lazy val ssoUrl = decorateUrlForLocalDev("sso-portal.host")

  lazy val portalBaseUrl = runModeConfiguration.get[String]("external-url.sso-portal.host")
  def toPortalUrl(path: String) = new URL(portalBaseUrl + path)
  lazy val frontendTemplatePath: String =
    runModeConfiguration
      .getOptional[String]("microservice.services.frontend-template-provider.path")
      .getOrElse("/template/mustache")

  def transformUrlForSso(url: URL) =
    s"$companyAuthFrontendHost/ssoout/non-digital?continue=" + URLEncoder.encode(url.toString, "UTF-8")

  def sa302Url(saUtr: String, taxYear: String) =
    s"/self-assessment-file/$taxYear/ind/$saUtr/return/viewYourCalculation/reviewYourFullCalculation"

  def completeYourTaxReturnUrl(saUtr: String, taxYear: String, lang: Lang) =
    s"$saFrontendHost/self-assessment-file/$taxYear/ind/$saUtr/return?lang=" + (if (lang.code equals ("en")) "eng"
                                                                                else "cym")
  lazy val ssoToActivateSaEnrolmentPinUrl =
    s"$enrolmentManagementFrontendHost/enrolment-management-frontend/IR-SA/get-access-tax-scheme?continue=/personal-account"
  lazy val ssoToRegisterForSaEnrolment = transformUrlForSso(toPortalUrl("/home/services/enroll"))
  lazy val ssoToRegistration = transformUrlForSso(toPortalUrl("/registration"))
  def ssoToSaAccountSummaryUrl(saUtr: String, taxYear: String) =
    transformUrlForSso(toPortalUrl(s"/self-assessment/ind/$saUtr/taxreturn/$taxYear/options"))
  def viewSaPaymentsUrl(saUtr: String): String =
    transformUrlForSso(toPortalUrl(s"/self-assessment/ind/$saUtr/account/payments"))

  def betaFeedbackUnauthenticatedUrl(aDeskproToken: String) =
    s"$contactHost/contact/beta-feedback-unauthenticated?service=$aDeskproToken"
  lazy val analyticsToken = runModeConfiguration.getOptional[String](s"google-analytics.token")
  lazy val analyticsHost = Some(
    runModeConfiguration.getOptional[String](s"google-analytics.host").getOrElse("service.gov.uk"))
  lazy val reportAProblemPartialUrl = s"$contactFrontendService/contact/problem_reports"
  lazy val makeAPaymentUrl = s"$payApiUrl/pay-api/pta/sa/journey/start"
  lazy val deskproToken = "PTA"

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

  lazy val identityVerificationUpliftUrl = s"$identityVerificationHost/$ivfe_web_context/uplift"
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

  lazy val sessionTimeoutInSeconds = runModeConfiguration.getOptional[Int]("session.timeout").getOrElse(1800)
  lazy val sessionTimeoutInMinutes = sessionTimeoutInSeconds / 60
  lazy val sessionCountdownInSeconds = runModeConfiguration.getOptional[Int]("session.countdown").getOrElse(120)

  def getFeedbackSurveyUrl(origin: Origin): String =
    feedbackSurveyFrontendHost + "/feedback/" + enc(origin.origin)

  def getCompanyAuthFrontendSignOutUrl(continueUrl: String): String =
    companyAuthHost + s"/gg/sign-out?continue=$continueUrl"

  lazy val editAddressTtl: Int = runModeConfiguration.getOptional[Int]("mongodb.editAddressTtl").getOrElse(0)

  lazy val saPartialReturnLinkText = "Back to account home"
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

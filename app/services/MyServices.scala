/*
 * Copyright 2026 HM Revenue & Customs
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

package services

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.*
import models.admin.{PayeToPegaRedirectToggle, ShowTaxCalcTileToggle}
import play.api.i18n.Messages
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.time.CurrentTaxYear

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class MyServices @Inject() (
  configDecorator: ConfigDecorator,
  featureFlagService: FeatureFlagService,
  fandFService: FandFService,
  taiService: TaiService
)(implicit ec: ExecutionContext)
    extends CurrentTaxYear {

  def getMyServices(implicit request: UserRequest[?], hc: HeaderCarrier, messages: Messages): Future[Seq[MyService]] = {

    val selfAssessmentF    = getSelfAssessment(request.saUserType, request.trustedHelper.isDefined)
    val payAsYouEarnF      = getPayAsYouEarn(request.authNino, request.trustedHelper.isDefined)
    val taxCalcCardsF      = getTaxcalc(request.trustedHelper.isDefined)
    val trustedHelperF     = getTrustedHelper(request.authNino, request.trustedHelper.isDefined)
    val marriageAllowanceF = getMarriageAllowance(request.authNino, request.trustedHelper.isDefined)

    Future
      .sequence(
        Seq(payAsYouEarnF, taxCalcCardsF, selfAssessmentF, marriageAllowanceF, getNationalInsuranceCard, trustedHelperF)
      )
      .map(_.flatten)
  }

  def getSelfAssessment(saUserType: SelfAssessmentUserType, isTrustedHelper: Boolean)(implicit
    messages: Messages
  ): Future[Option[MyService]] =
    Future.successful(if (isTrustedHelper) {
      None
    } else {
      saUserType match {
        case _: ActivatedOnlineFilerSelfAssessmentUser       =>
          Some(
            MyService(
              messages("label.self_assessment"),
              controllers.interstitials.routes.InterstitialController.displaySelfAssessment.url,
              messages("label.newViewAndManageSA", s"${current.currentYear + 1}"),
              gaAction = Some("Income"),
              gaLabel = Some("Self Assessment")
            )
          )
        case WrongCredentialsSelfAssessmentUser(_)           =>
          Some(
            MyService(
              messages("label.self_assessment"),
              controllers.routes.SaWrongCredentialsController.landingPage().url,
              messages("title.signed_in_wrong_account.h1"),
              gaAction = Some("Income"),
              gaLabel = Some("Self Assessment")
            )
          )
        case NotYetActivatedOnlineFilerSelfAssessmentUser(_) =>
          Some(
            MyService(
              messages("label.self_assessment"),
              configDecorator.ssoToActivateSaEnrolmentPinUrl,
              messages("label.activate_your_self_assessment_registration"),
              gaAction = Some("Income"),
              gaLabel = Some("Self Assessment")
            )
          )
        case _                                               => None
      }
    })

  def getPayAsYouEarn(nino: Nino, isTrustedHelper: Boolean)(implicit messages: Messages): Future[Option[MyService]] = {
    val mdtpPaye = MyService(
      messages("label.pay_as_you_earn_paye"),
      s"${configDecorator.taiHost}/check-income-tax/what-do-you-want-to-do",
      "",
      gaAction = Some("Income"),
      gaLabel = Some("Pay As You Earn (PAYE)")
    )

    featureFlagService.get(PayeToPegaRedirectToggle).map { toggle =>
      if (toggle.isEnabled) {
        val penultimateDigit = nino.nino.charAt(6).asDigit
        if (configDecorator.payeToPegaRedirectList.contains(penultimateDigit) && !isTrustedHelper) {
          Some(
            MyService(
              messages("label.pay_as_you_earn_paye"),
              configDecorator.payeToPegaRedirectUrl,
              "",
              gaAction = Some("Income"),
              gaLabel = Some("Pay As You Earn (PAYE)")
            )
          )
        } else {
          Some(mdtpPaye)
        }
      } else {
        Some(mdtpPaye)
      }
    }
  }

  def getTaxcalc(trustedHelperEnabled: Boolean)(implicit messages: Messages): Future[Option[MyService]] =
    if (trustedHelperEnabled) {
      Future.successful(None)
    } else {
      featureFlagService.get(ShowTaxCalcTileToggle).map { taxCalcTileFlag =>
        if (taxCalcTileFlag.isEnabled) {
          Some(
            MyService(
              messages("alertBannerShuttering.taxcalc"),
              configDecorator.taxCalcHomePageUrl,
              "",
              gaAction = Some("Income"),
              gaLabel = Some("Tax Calculation")
            )
          )
        } else {
          None
        }
      }
    }

  def getNationalInsuranceCard(implicit messages: Messages): Future[Option[MyService]] =
    Future.successful(
      Some(
        MyService(
          messages("label.your_national_insurance_and_state_pension"),
          controllers.interstitials.routes.InterstitialController.displayNISP.url,
          "",
          gaAction = Some("Income"),
          gaLabel = Some("National Insurance and State Pension")
        )
      )
    )

  def getTrustedHelper(nino: Nino, isTrustedHelper: Boolean)(implicit
    hc: HeaderCarrier,
    messages: Messages
  ): Future[Option[MyService]] =
    if (isTrustedHelper) {
      Future.successful(None)
    } else {
      fandFService
        .isAnyFandFRelationships(nino)
        .map {
          case false => None
          case true  =>
            Some(
              MyService(
                messages("label.trusted_helpers_heading"),
                configDecorator.manageTrustedHelpersUrl,
                "",
                gaAction = Some("Account"),
                gaLabel = Some("Trusted helpers")
              )
            )
        }
    }

  def getMarriageAllowance(nino: Nino, isTrustedHelper: Boolean)(implicit
    hc: HeaderCarrier,
    request: UserRequest[?],
    messages: Messages
  ) =
    if (isTrustedHelper) {
      Future.successful(None)
    } else {
      taiService.getTaxComponentsList(nino, current.currentYear).map {
        case taxComponents if taxComponents.contains("MarriageAllowanceReceived")    =>
          Some(
            MyService(
              messages("title.marriage_allowance"),
              messages("label.your_partner_currently_transfers_part_of_their_personal_allowance_to_you"),
              "/marriage-allowance-application/history",
              gaAction = Some("Benefits"),
              gaLabel = Some("Marriage Allowance")
            )
          )
        case taxComponents if taxComponents.contains("MarriageAllowanceTransferred") =>
          Some(
            MyService(
              messages("title.marriage_allowance"),
              messages("label.you_currently_transfer_part_of_your_personal_allowance_to_your_partner"),
              "/marriage-allowance-application/history",
              gaAction = Some("Benefits"),
              gaLabel = Some("Marriage Allowance")
            )
          )
        case _                                                                       => None
      }
    }

  override def now: () => LocalDate = LocalDate.now
}

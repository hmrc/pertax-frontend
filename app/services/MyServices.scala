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
import models.admin.ShowTaxCalcTileToggle
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

    val isTrustedHelperUser = request.trustedHelper.isDefined

    val selfAssessmentTileF    = getSelfAssessmentOrCombinedMtdTile(request.saUserType, isTrustedHelperUser)
    val payAsYouEarnTileF      = getPayAsYouEarnTile()
    val taxCalculationTileF    = getTaxCalculationTile(isTrustedHelperUser)
    val trustedHelperTileF     = getTrustedHelperTile(request.authNino, isTrustedHelperUser)
    val marriageAllowanceTileF = getMarriageAllowanceTile(request.authNino, isTrustedHelperUser)
    val nationalInsuranceTileF = getNationalInsuranceTile

    Future
      .sequence(
        Seq(
          payAsYouEarnTileF,
          taxCalculationTileF,
          selfAssessmentTileF,
          marriageAllowanceTileF,
          nationalInsuranceTileF,
          trustedHelperTileF
        )
      )
      .map(_.flatten)
  }

  private def getSelfAssessmentOrCombinedMtdTile(
    selfAssessmentUserType: SelfAssessmentUserType,
    isTrustedHelperUser: Boolean
  )(implicit request: UserRequest[?], messages: Messages): Future[Option[MyService]] =
    Future.successful {
      if (isTrustedHelperUser) {
        None
      } else {
        val hasMtdItsa = userHasMtdItsaEnrolment(request.enrolments)

        (selfAssessmentUserType, hasMtdItsa) match {

          case (_: ActivatedOnlineFilerSelfAssessmentUser, true) =>
            Some(
              combinedMtdSaTile(
                href = controllers.interstitials.routes.InterstitialController.displaySelfAssessment.url
              )
            )

          case (WrongCredentialsSelfAssessmentUser(_), true) =>
            Some(
              combinedMtdSaTile(
                href = controllers.routes.SaWrongCredentialsController.landingPage().url
              )
            )

          case (_: ActivatedOnlineFilerSelfAssessmentUser, false) =>
            Some(
              saTile(
                href = controllers.interstitials.routes.InterstitialController.displaySelfAssessment.url,
                body = messages("label.newViewAndManageSA", s"${current.currentYear + 1}")
              )
            )

          case (WrongCredentialsSelfAssessmentUser(_), false) =>
            Some(
              saTile(
                href = controllers.routes.SaWrongCredentialsController.landingPage().url,
                body = messages("title.signed_in_wrong_account.h1")
              )
            )

          case (_: NotYetActivatedOnlineFilerSelfAssessmentUser, _) =>
            Some(
              saTile(
                href = configDecorator.ssoToActivateSaEnrolmentPinUrl,
                body = messages("label.activate_your_self_assessment_registration")
              )
            )

          case _ =>
            None
        }
      }
    }

  def getPayAsYouEarnTile()(implicit messages: Messages): Future[Option[MyService]] =
    Future.successful(
      Some(
        MyService(
          messages("label.pay_as_you_earn_paye"),
          controllers.routes.RedirectToPayeController.redirectToPaye.url,
          "",
          gaAction = Some("Income"),
          gaLabel = Some("Pay As You Earn (PAYE)")
        )
      )
    )

  private def getTaxCalculationTile(
    isTrustedHelperUser: Boolean
  )(implicit messages: Messages): Future[Option[MyService]] =
    if (isTrustedHelperUser) {
      Future.successful(None)
    } else {
      featureFlagService.get(ShowTaxCalcTileToggle).map { showTaxCalcTile =>
        if (showTaxCalcTile.isEnabled) {
          Some(
            MyService(
              messages(
                "label.tax_calc_option",
                s"${current.back(configDecorator.taxCalcYearsToShow).startYear}",
                s"${current.startYear}"
              ),
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

  private def getNationalInsuranceTile(implicit messages: Messages): Future[Option[MyService]] =
    Future.successful(
      Some(
        MyService(
          messages("label.new_national_insurance_and_state_pension"),
          controllers.interstitials.routes.InterstitialController.displayNISP.url,
          "",
          gaAction = Some("Income"),
          gaLabel = Some("National Insurance and State Pension")
        )
      )
    )

  private def getTrustedHelperTile(nino: Nino, isTrustedHelperUser: Boolean)(implicit
    hc: HeaderCarrier,
    messages: Messages
  ): Future[Option[MyService]] =
    if (isTrustedHelperUser) {
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

  private def getMarriageAllowanceTile(nino: Nino, isTrustedHelperUser: Boolean)(implicit
    hc: HeaderCarrier,
    request: UserRequest[?],
    messages: Messages
  ): Future[Option[MyService]] =
    if (isTrustedHelperUser) {
      Future.successful(None)
    } else {
      taiService.getTaxComponentsList(nino, current.currentYear).map { taxComponents =>
        val maMsg = taxComponents match {
          case tc if tc.contains("MarriageAllowanceReceived")    =>
            Some(messages("label.your_partner_currently_transfers_part_of_their_personal_allowance_to_you"))
          case tc if tc.contains("MarriageAllowanceTransferred") =>
            Some(messages("label.you_currently_transfer_part_of_your_personal_allowance_to_your_partner"))
          case _                                                 =>
            None
        }

        maMsg.map { msg =>
          MyService(
            messages("title.marriage_allowance"),
            "/marriage-allowance-application/history",
            msg,
            gaAction = Some("Benefits"),
            gaLabel = Some("Marriage Allowance")
          )
        }
      }
    }

  override def now: () => LocalDate = LocalDate.now

  private def combinedMtdSaTile(href: String)(implicit messages: Messages): MyService =
    MyService(
      messages("label.mtd_for_itsa"),
      href,
      "",
      gaAction = Some("Income"),
      gaLabel = Some("MTD IT & SA")
    )

  private def saTile(href: String, body: String)(implicit messages: Messages): MyService =
    MyService(
      messages("label.self_assessment"),
      href,
      body,
      gaAction = Some("Income"),
      gaLabel = Some("Self Assessment")
    )

  private val MtdItsaEnrolmentKey = "HMRC-MTD-IT"

  private def userHasMtdItsaEnrolment(enrolments: Set[uk.gov.hmrc.auth.core.Enrolment]): Boolean =
    enrolments.exists(_.key == MtdItsaEnrolmentKey)
}

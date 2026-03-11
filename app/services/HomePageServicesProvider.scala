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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.time.CurrentTaxYear

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class HomePageServicesProvider @Inject() (
  configDecorator: ConfigDecorator,
  featureFlagService: FeatureFlagService,
  fandFService: FandFService,
  taiService: TaiService
)(implicit ec: ExecutionContext)
    extends CurrentTaxYear {

  def getHomePageServices(implicit
    request: UserRequest[?],
    hc: HeaderCarrier,
    messages: Messages
  ): Future[HomePageServices] = {

    val isTrustedHelper = request.trustedHelper.isDefined
    val nino            = request.authNino

    val marriageAllowanceF: Future[Seq[HomePageService]] =
      if (isTrustedHelper) Future.successful(Seq.empty)
      else taiService.getTaxComponentsList(nino, current.currentYear).map(buildMarriageAllowanceServices)

    val trustedHelperF: Future[Seq[HomePageService]] =
      if (isTrustedHelper) Future.successful(Seq.empty)
      else fandFService.isAnyFandFRelationships(nino).map(buildTrustedHelperServices)

    for {
      selfAssessmentMy    <- getMySelfAssessment(request.saUserType, isTrustedHelper)
      payAsYouEarn        <- getPayAsYouEarn()
      taxCalc             <- getTaxCalculation(isTrustedHelper)
      nationalInsurance   <- getNationalInsurance()
      selfAssessmentOther <- getOtherSelfAssessment(request.saUserType, isTrustedHelper)
      childBenefit        <- getChildBenefit(isTrustedHelper)
      annualTaxSummary    <- getAnnualTaxSummaries(isTrustedHelper)
      marriageAllowance   <- marriageAllowanceF
      trustedHelper       <- trustedHelperF
    } yield HomePageServices(
      Seq(
        payAsYouEarn,
        taxCalc,
        selfAssessmentMy,
        nationalInsurance,
        selfAssessmentOther,
        childBenefit,
        annualTaxSummary
      ).flatten ++ marriageAllowance ++ trustedHelper
    )
  }

  private def getMySelfAssessment(
    saUserType: SelfAssessmentUserType,
    isTrustedHelper: Boolean
  )(implicit messages: Messages): Future[Option[MyService]] =
    Future.successful {
      if (isTrustedHelper) {
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
          case _                                               =>
            None
        }
      }
    }

  private def getOtherSelfAssessment(
    saUserType: SelfAssessmentUserType,
    isTrustedHelper: Boolean
  )(implicit messages: Messages): Future[Option[OtherService]] =
    Future.successful {
      if (isTrustedHelper) {
        None
      } else {
        saUserType match {
          case NotEnrolledSelfAssessmentUser(_) =>
            Some(
              OtherService(
                messages("label.activate_your_self_assessment_registration"),
                controllers.routes.SelfAssessmentController.requestAccess.url,
                gaAction = Some("Income"),
                gaLabel = Some("Self Assessment")
              )
            )
          case _                                =>
            None
        }
      }
    }

  private def getPayAsYouEarn()(implicit messages: Messages): Future[Option[MyService]] =
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

  private def getTaxCalculation(isTrustedHelper: Boolean)(implicit messages: Messages): Future[Option[MyService]] =
    if (isTrustedHelper) {
      Future.successful(None)
    } else {
      featureFlagService.get(ShowTaxCalcTileToggle).map { taxCalcTileFlag =>
        if (taxCalcTileFlag.isEnabled) {
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

  private def getNationalInsurance()(implicit messages: Messages): Future[Option[MyService]] =
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

  private def getChildBenefit(isTrustedHelper: Boolean)(implicit messages: Messages): Future[Option[OtherService]] =
    Future.successful {
      if (isTrustedHelper) {
        None
      } else {
        Some(
          OtherService(
            messages("label.child_benefit"),
            controllers.interstitials.routes.InterstitialController.displayChildBenefitsSingleAccountView.url,
            gaAction = Some("Benefits"),
            gaLabel = Some("Child Benefit")
          )
        )
      }
    }

  private def getAnnualTaxSummaries(isTrustedHelper: Boolean)(implicit
    messages: Messages
  ): Future[Option[OtherService]] =
    Future.successful {
      if (isTrustedHelper) {
        None
      } else {
        Some(
          OtherService(
            messages("card.ats.heading"),
            configDecorator.annualTaxSaSummariesTileLinkShow,
            gaAction = Some("Tax Summaries"),
            gaLabel = Some("Annual Tax Summary")
          )
        )
      }
    }

  private def buildMarriageAllowanceServices(taxComponents: List[String])(implicit
    messages: Messages
  ): Seq[HomePageService] =
    taxComponents match {
      case components if components.contains("MarriageAllowanceReceived") =>
        Seq(
          MyService(
            messages("title.marriage_allowance"),
            "/marriage-allowance-application/history",
            messages("label.your_partner_currently_transfers_part_of_their_personal_allowance_to_you"),
            gaAction = Some("Benefits"),
            gaLabel = Some("Marriage Allowance")
          )
        )

      case components if components.contains("MarriageAllowanceTransferred") =>
        Seq(
          MyService(
            messages("title.marriage_allowance"),
            "/marriage-allowance-application/history",
            messages("label.you_currently_transfer_part_of_your_personal_allowance_to_your_partner"),
            gaAction = Some("Benefits"),
            gaLabel = Some("Marriage Allowance")
          )
        )

      case _ =>
        Seq(
          OtherService(
            messages("title.marriage_allowance"),
            "/marriage-allowance-application/history",
            gaAction = Some("Benefits"),
            gaLabel = Some("Marriage Allowance")
          )
        )
    }

  private def buildTrustedHelperServices(hasRelationships: Boolean)(implicit
    messages: Messages
  ): Seq[HomePageService] =
    if (hasRelationships) {
      Seq(
        MyService(
          messages("label.trusted_helpers_heading"),
          configDecorator.manageTrustedHelpersUrl,
          "",
          gaAction = Some("Account"),
          gaLabel = Some("Trusted helpers")
        )
      )
    } else {
      Seq(
        OtherService(
          messages("label.trusted_helpers_heading"),
          configDecorator.manageTrustedHelpersUrl,
          gaAction = Some("Account"),
          gaLabel = Some("Trusted helpers")
        )
      )
    }

  override def now: () => LocalDate = LocalDate.now
}

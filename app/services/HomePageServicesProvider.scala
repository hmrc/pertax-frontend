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
import uk.gov.hmrc.auth.core.Enrolment
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

  private val MtdItsaEnrolmentKey = "HMRC-MTD-IT"

  def getHomePageServices(implicit
                          request: UserRequest[?],
                          hc: HeaderCarrier,
                          messages: Messages
                         ): Future[HomePageServices] = {

    val isTrustedHelperUser = request.trustedHelper.isDefined
    val nino                = request.authNino

    val marriageAllowanceF: Future[Seq[HomePageService]] =
      if (isTrustedHelperUser) Future.successful(Seq.empty)
      else taiService.getTaxComponentsList(nino, current.currentYear).map(buildMarriageAllowanceServices)

    val trustedHelperF: Future[Seq[HomePageService]] =
      if (isTrustedHelperUser) Future.successful(Seq.empty)
      else fandFService.isAnyFandFRelationships(nino).map(buildTrustedHelperServices)

    for {
      selfAssessmentMy    <- getMySelfAssessment(request.saUserType, request.enrolments, isTrustedHelperUser)
      payAsYouEarn        <- getPayAsYouEarn()
      taxCalc             <- getTaxCalculation(isTrustedHelperUser)
      nationalInsurance   <- getNationalInsurance()
      selfAssessmentOther <- getOtherSelfAssessment(request.saUserType, isTrustedHelperUser)
      mtdOther            <- getMtdOtherService(isTrustedHelperUser)
      childBenefit        <- getChildBenefit(isTrustedHelperUser)
      annualTaxSummary    <- getAnnualTaxSummaries(isTrustedHelperUser)
      marriageAllowance   <- marriageAllowanceF
      trustedHelper       <- trustedHelperF
    } yield HomePageServices(
      Seq(
        payAsYouEarn,
        taxCalc,
        selfAssessmentMy,
        nationalInsurance,
        selfAssessmentOther,
        mtdOther,
        childBenefit,
        annualTaxSummary
      ).flatten ++ marriageAllowance ++ trustedHelper
    )
  }

  private def userHasMtdItsaEnrolment(enrolments: Set[Enrolment]): Boolean =
    enrolments.exists(_.key == MtdItsaEnrolmentKey)

  private def combinedMtdSaTile(href: String)(implicit messages: Messages): MyService =
    MyService(
      messages("label.mtd_for_itsa"),
      href,
      "",
      gaAction = Some("Income"),
      gaLabel = Some("MTD IT & SA")
    )

  private def mySaTile(href: String, body: String)(implicit messages: Messages): MyService =
    MyService(
      messages("label.self_assessment"),
      href,
      body,
      gaAction = Some("Income"),
      gaLabel = Some("Self Assessment")
    )

  private def otherSaTile(title: String, linkUrl: String): OtherService =
    OtherService(
      title,
      linkUrl,
      gaAction = Some("Income"),
      gaLabel = Some("Self Assessment")
    )

  private def mtdTile(linkUrl: String)(implicit messages: Messages): OtherService =
    OtherService(
      messages("label.mtd_for_it"),
      linkUrl,
      gaAction = Some("MTDIT"),
      gaLabel = Some("Making Tax Digital for Income Tax")
    )

  private def getMySelfAssessment(
                                   saUserType: SelfAssessmentUserType,
                                   enrolments: Set[Enrolment],
                                   isTrustedHelperUser: Boolean
                                 )(implicit messages: Messages): Future[Option[MyService]] =
    Future.successful {
      if (isTrustedHelperUser) {
        None
      } else {
        val hasMtdItsa = userHasMtdItsaEnrolment(enrolments)

        (saUserType, hasMtdItsa) match {
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
              mySaTile(
                href = controllers.interstitials.routes.InterstitialController.displaySelfAssessment.url,
                body = messages("label.newViewAndManageSA", s"${current.currentYear + 1}")
              )
            )

          case (WrongCredentialsSelfAssessmentUser(_), false) =>
            Some(
              mySaTile(
                href = controllers.routes.SaWrongCredentialsController.landingPage().url,
                body = messages("title.signed_in_wrong_account.h1")
              )
            )

          case (_: NotYetActivatedOnlineFilerSelfAssessmentUser, _) =>
            Some(
              mySaTile(
                href = configDecorator.ssoToActivateSaEnrolmentPinUrl,
                body = messages("label.activate_your_self_assessment_registration")
              )
            )

          case _ =>
            None
        }
      }
    }

  private def getOtherSelfAssessment(
                                      saUserType: SelfAssessmentUserType,
                                      isTrustedHelperUser: Boolean
                                    )(implicit messages: Messages): Future[Option[OtherService]] =
    Future.successful {
      if (isTrustedHelperUser) {
        None
      } else {
        saUserType match {
          case NotEnrolledSelfAssessmentUser(_) =>
            Some(
              otherSaTile(
                title = messages("label.self_assessment"),
                linkUrl = controllers.routes.SelfAssessmentController.requestAccess.url
              )
            )

          case NotYetActivatedOnlineFilerSelfAssessmentUser(_) =>
            Some(
              otherSaTile(
                title = messages("label.self_assessment"),
                linkUrl = configDecorator.ssoToActivateSaEnrolmentPinUrl
              )
            )

          case _ =>
            None
        }
      }
    }

  private def getMtdOtherService(
                                  isTrustedHelperUser: Boolean
                                )(implicit request: UserRequest[?], messages: Messages): Future[Option[OtherService]] =
    if (isTrustedHelperUser) {
      Future.successful(None)
    } else {
      val hasActiveMtd = userHasMtdItsaEnrolment(request.enrolments)

      if (hasActiveMtd) {
        Future.successful(None)
      } else {
        Future.successful(
          Some(
            mtdTile(
              controllers.interstitials.routes.MtdAdvertInterstitialController.displayMTDITPage.url
            )
          )
        )
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

  private def getTaxCalculation(isTrustedHelperUser: Boolean)(implicit messages: Messages): Future[Option[MyService]] =
    if (isTrustedHelperUser) {
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
          messages("label.new_national_insurance_and_state_pension"),
          controllers.interstitials.routes.InterstitialController.displayNISP.url,
          "",
          gaAction = Some("Income"),
          gaLabel = Some("National Insurance and State Pension")
        )
      )
    )

  private def getChildBenefit(isTrustedHelperUser: Boolean)(implicit messages: Messages): Future[Option[OtherService]] =
    Future.successful {
      if (isTrustedHelperUser) {
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

  private def getAnnualTaxSummaries(isTrustedHelperUser: Boolean)(implicit
                                                                  messages: Messages
  ): Future[Option[OtherService]] =
    Future.successful {
      if (isTrustedHelperUser) {
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
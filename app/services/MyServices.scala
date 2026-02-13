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

import controllers.auth.requests.UserRequest
import models.*
import com.google.inject.Inject
import config.ConfigDecorator
import models.admin.ShowTaxCalcTileToggle
import play.api.i18n.Messages
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService

import scala.concurrent.{ExecutionContext, Future}
import util.DateTimeTools.current

class MyServices @Inject() (
  configDecorator: ConfigDecorator,
  featureFlagService: FeatureFlagService
)(implicit ec: ExecutionContext) {

  def getMyServices(implicit request: UserRequest[?], messages: Messages): Future[Seq[MyService]] = {

    val selfAssessmentF = getSelfAssessment(request.saUserType)
    val payAsYouEarnF   = getPayAsYouEarn()
    val taxCalcCardsF   = getTaxcalc(request.trustedHelper.isDefined)

    Future.sequence(Seq(payAsYouEarnF, taxCalcCardsF, selfAssessmentF)).map(_.flatten)
  }

  def getSelfAssessment(saUserType: SelfAssessmentUserType)(implicit messages: Messages): Future[Option[MyService]] =
    Future.successful(saUserType match {
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
            "label.self_assessment",
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
    })

  def getPayAsYouEarn()(implicit messages: Messages): Future[Option[MyService]] =

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
}

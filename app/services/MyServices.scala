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
import models.admin.{PayeToPegaRedirectToggle, ShowTaxCalcTileToggle}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService

import scala.concurrent.{ExecutionContext, Future}

class MyServices @Inject() (
  configDecorator: ConfigDecorator,
  featureFlagService: FeatureFlagService
)(implicit ec: ExecutionContext) {

  def getMyServices(implicit request: UserRequest[?]): Future[Seq[MyService]] = {

    val selfAssessmentF = getSelfAssessment(request.saUserType)
    val payAsYouEarnF   = getPayAsYouEarn(request.authNino, request.trustedHelper.isDefined)
    val taxCalcCardsF   = getTaxcalc(request.trustedHelper.isDefined)

    Future.sequence(Seq(payAsYouEarnF, taxCalcCardsF, selfAssessmentF)).map(_.flatten)
  }

  def getSelfAssessment(saUserType: SelfAssessmentUserType): Future[Option[MyService]] =
    Future.successful(saUserType match {
      case _: ActivatedOnlineFilerSelfAssessmentUser       =>
        Some(
          MyService(
            "label.self_assessment",
            controllers.interstitials.routes.InterstitialController.displaySelfAssessment.url,
            "label.newViewAndManageSA"
          )
        )
      case WrongCredentialsSelfAssessmentUser(_)           =>
        Some(
          MyService(
            "label.self_assessment",
            controllers.routes.SaWrongCredentialsController.landingPage().url,
            "title.signed_in_wrong_account.h1"
          )
        )
      case NotYetActivatedOnlineFilerSelfAssessmentUser(_) =>
        Some(
          MyService(
            "label.self_assessment",
            configDecorator.ssoToActivateSaEnrolmentPinUrl,
            "label.activate_your_self_assessment_registration"
          )
        )
      case NotEnrolledSelfAssessmentUser(_)                =>
        Some(
          MyService(
            "label.self_assessment",
            controllers.routes.SelfAssessmentController.requestAccess.url,
            "label.activate_your_self_assessment_registration"
          )
        )
      case NonFilerSelfAssessmentUser                      => None
    })

  def getPayAsYouEarn(nino: Nino, isTrustedHelper: Boolean): Future[Option[MyService]] = {
    val mdtpPaye = MyService(
      "label.pay_as_you_earn_paye",
      s"${configDecorator.taiHost}/check-income-tax/what-do-you-want-to-do",
      ""
    )

    featureFlagService.get(PayeToPegaRedirectToggle).map { toggle =>
      if (toggle.isEnabled) {
        val penultimateDigit = nino.nino.charAt(6).asDigit
        if (configDecorator.payeToPegaRedirectList.contains(penultimateDigit) && !isTrustedHelper) {
          Some(
            MyService(
              "label.pay_as_you_earn_paye",
              configDecorator.payeToPegaRedirectUrl,
              ""
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

  def getTaxcalc(trustedHelperEnabled: Boolean): Future[Option[MyService]] =
    if (trustedHelperEnabled) {
      Future.successful(None)
    } else {
      featureFlagService.get(ShowTaxCalcTileToggle).map { taxCalcTileFlag =>
        if (taxCalcTileFlag.isEnabled) {
          Some(
            MyService(
              "alertBannerShuttering.taxcalc",
              configDecorator.taxCalcHomePageUrl,
              ""
            )
          )
        } else {
          None
        }
      }
    }

  def getNationalInsuranceCard: Future[Option[MyService]] =
    Future.successful(
      Some(
        MyService(
          "label.your_national_insurance_and_state_pension",
          controllers.interstitials.routes.InterstitialController.displayNISP.url,
          ""
        )
      )
    )
}

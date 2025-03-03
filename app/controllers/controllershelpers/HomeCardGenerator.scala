/*
 * Copyright 2025 HM Revenue & Customs
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

package controllers.controllershelpers

import com.google.inject.{Inject, Singleton}
import config.{ConfigDecorator, NewsAndTilesConfig}
import controllers.auth.requests.UserRequest
import controllers.routes
import models._
import models.admin._
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, Call}
import play.twirl.api.{Html, HtmlFormat}
import services.partials.TaxCalcPartialService
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.sca.logging.Logging
import util.DateTimeTools.current
import util.EnrolmentsHelper
import views.html.cards.home._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HomeCardGenerator @Inject() (
  featureFlagService: FeatureFlagService,
  payAsYouEarnView: PayAsYouEarnView,
  taxCreditsView: TaxCreditsView,
  childBenefitSingleAccountView: ChildBenefitSingleAccountView,
  marriageAllowanceView: MarriageAllowanceView,
  taxSummariesView: TaxSummariesView,
  latestNewsAndUpdatesView: LatestNewsAndUpdatesView,
  itsaMergeView: ItsaMergeView,
  saMergeView: SaMergeView,
  enrolmentsHelper: EnrolmentsHelper,
  newsAndTilesConfig: NewsAndTilesConfig,
  nispView: NISPView,
  selfAssessmentRegistrationView: SelfAssessmentRegistrationView,
  taxCalcPartialService: TaxCalcPartialService
)(implicit configDecorator: ConfigDecorator, ex: ExecutionContext)
    extends Logging {

  def getIncomeCards(
    taxComponentsState: TaxComponentsState
  )(implicit request: UserRequest[AnyContent], messages: Messages): Future[Seq[Html]] = {

    val staticCards = Seq(
      getLatestNewsAndUpdatesCard(),
      getPayAsYouEarnCard(taxComponentsState)
    ).flatten

    val dynamicTaxCalcCards = featureFlagService.get(TaxcalcToggle).flatMap {
      case FeatureFlag(_, true) if request.trustedHelper.isEmpty =>
        taxCalcPartialService.getTaxCalcPartial.map(_.map(_.partialContent))
      case _                                                     =>
        Future.successful(Seq.empty)
    }

    val additionalCards = Seq(
      getSelfAssessmentCard(),
      Some(getNationalInsuranceCard())
    ).flatten

    dynamicTaxCalcCards.map { taxCalcCards =>
      staticCards ++ taxCalcCards ++ additionalCards
    }
  }

  def getATSCard()(implicit request: UserRequest[AnyContent], messages: Messages): Future[Seq[Html]] =
    if (request.trustedHelper.isEmpty) {
      getAnnualTaxSummaryCard.map(_.toSeq)
    } else {
      Future.successful(Nil)
    }

  def getPayAsYouEarnCard(
    taxComponentsState: TaxComponentsState
  )(implicit request: UserRequest[_], messages: Messages): Option[HtmlFormat.Appendable] =
    request.nino.flatMap { nino =>
      taxComponentsState match {
        case TaxComponentsNotAvailableState => None
        case _                              => Some(payAsYouEarnView(configDecorator, nino.withoutSuffix.takeRight(2)))
      }
    }

  private def displaySACall: Call = routes.InterstitialController.displaySelfAssessment

  private def handleSACall: Call = routes.SelfAssessmentController.handleSelfAssessment

  private def redirectToEnrolCall: Call = routes.SelfAssessmentController.redirectToEnrolForSa

  private def callAndContent(implicit request: UserRequest[_]): Option[(Call, String)] =
    request.saUserType match {
      case ActivatedOnlineFilerSelfAssessmentUser(_)       => Some(displaySACall -> "label.viewAndManageSA")
      case NotYetActivatedOnlineFilerSelfAssessmentUser(_) =>
        Some(handleSACall -> "label.activate_your_self_assessment")
      case WrongCredentialsSelfAssessmentUser(_)           =>
        Some(handleSACall -> "label.find_out_how_to_access_your_self_assessment")
      case NotEnrolledSelfAssessmentUser(_)                => Some(redirectToEnrolCall -> "label.request_access_to_your_sa")
      case sut                                             =>
        logger.warn(s"Unable to display self assessment card due to sa user type value of $sut")
        None
    }

  def getSelfAssessmentCard()(implicit
    messages: Messages,
    request: UserRequest[_]
  ): Option[HtmlFormat.Appendable] = request.trustedHelper match {
    case Some(_) => None
    case None    =>
      (
        enrolmentsHelper.itsaEnrolmentStatus(request.enrolments).isDefined,
        request.isSa,
        configDecorator.pegaSaRegistrationEnabled
      ) match {
        case (true, _, _)         => Some(itsaMergeView((current.currentYear + 1).toString))
        case (false, true, _)     =>
          callAndContent.map { case (redirectUrl, paragraphMessageKey) =>
            saMergeView((current.currentYear + 1).toString, redirectUrl.url, paragraphMessageKey)
          }
        case (false, false, true) => Some(selfAssessmentRegistrationView())
        case _                    => None
      }
  }

  def getAnnualTaxSummaryCard(implicit
    request: UserRequest[AnyContent],
    messages: Messages
  ): Future[Option[HtmlFormat.Appendable]] =
    featureFlagService.get(TaxSummariesTileToggle).map {
      case FeatureFlag(_, true) =>
        val url = if (request.isSaUserLoggedIntoCorrectAccount) {
          configDecorator.annualTaxSaSummariesTileLink
        } else {
          configDecorator.annualTaxPayeSummariesTileLink
        }

        Some(taxSummariesView(url))
      case _                    => None
    }

  def getLatestNewsAndUpdatesCard()(implicit messages: Messages): Option[HtmlFormat.Appendable] =
    if (configDecorator.isNewsAndUpdatesTileEnabled && newsAndTilesConfig.getNewsAndContentModelList().nonEmpty) {
      Some(latestNewsAndUpdatesView())
    } else {
      None
    }

  def getNationalInsuranceCard()(implicit messages: Messages): HtmlFormat.Appendable = nispView()

  def getBenefitCards(
    taxComponents: Option[TaxComponents],
    trustedHelper: Option[TrustedHelper]
  )(implicit messages: Messages): List[Html] =
    if (trustedHelper.isEmpty) {
      List(getChildBenefitCard(), getMarriageAllowanceCard(taxComponents), getTaxCreditsCard())
    } else {
      List.empty
    }

  def getTaxCreditsCard()(implicit messages: Messages): HtmlFormat.Appendable = taxCreditsView()

  def getChildBenefitCard()(implicit messages: Messages): HtmlFormat.Appendable = childBenefitSingleAccountView()

  def getMarriageAllowanceCard(taxComponents: Option[TaxComponents])(implicit
    messages: Messages
  ): HtmlFormat.Appendable =
    marriageAllowanceView(taxComponents)
}

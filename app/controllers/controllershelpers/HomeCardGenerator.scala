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
import models.*
import models.admin.*
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
import views.html.cards.home.*

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HomeCardGenerator @Inject() (
  featureFlagService: FeatureFlagService,
  payAsYouEarnView: PayAsYouEarnView,
  childBenefitSingleAccountView: ChildBenefitSingleAccountView,
  marriageAllowanceView: MarriageAllowanceView,
  taxSummariesView: TaxSummariesView,
  latestNewsAndUpdatesView: LatestNewsAndUpdatesView,
  itsaMergeView: ItsaMergeView,
  saMergeView: SaMergeView,
  mtditAdvertTileView: MTDITAdvertTileView,
  enrolmentsHelper: EnrolmentsHelper,
  newsAndTilesConfig: NewsAndTilesConfig,
  nispView: NISPView,
  selfAssessmentRegistrationView: SelfAssessmentRegistrationView,
  taxCalcPartialService: TaxCalcPartialService
)(implicit configDecorator: ConfigDecorator, ex: ExecutionContext)
    extends Logging {

  def getIncomeCards(implicit request: UserRequest[AnyContent], messages: Messages): Future[Seq[Html]] = {
    val latestNewsCardOpt = getLatestNewsAndUpdatesCard()

    featureFlagService.get(MDTITAdvertToggle).flatMap { ff =>
      val additionalCards = getSelfAssessmentCards(includeMDTITAdvert = ff.isEnabled) ++ Seq(getNationalInsuranceCard())

      for {
        payeCard     <- getPayAsYouEarnCard
        taxCalcCards <- getDynamicTaxCalcCards
      } yield {
        val staticCards = latestNewsCardOpt.toSeq :+ payeCard
        staticCards ++ taxCalcCards ++ additionalCards
      }
    }

  }

  private def getDynamicTaxCalcCards(implicit request: UserRequest[_]): Future[Seq[Html]] =
    featureFlagService.get(ShowTaxCalcTileToggle).flatMap {
      case FeatureFlag(_, true) if request.trustedHelper.isEmpty =>
        taxCalcPartialService.getTaxCalcPartial.map(_.map(_.partialContent))
      case _                                                     =>
        Future.successful(Seq.empty)
    }

  def getATSCard()(implicit request: UserRequest[AnyContent], messages: Messages): Future[Seq[Html]] =
    if (request.trustedHelper.isEmpty) {
      getAnnualTaxSummaryCard.map(_.toSeq)
    } else {
      Future.successful(Nil)
    }

  def getPayAsYouEarnCard(implicit request: UserRequest[_], messages: Messages): Future[HtmlFormat.Appendable] =
    featureFlagService.get(PayeToPegaRedirectToggle).map { case FeatureFlag(_, isEnabled) =>
      payAsYouEarnView(shouldUsePegaRouting = isEnabled)
    }

  private def displaySACall: Call = routes.InterstitialController.displaySelfAssessment

  private def handleSACall: Call = routes.SelfAssessmentController.handleSelfAssessment

  private def redirectToEnrolCall: Call = routes.SelfAssessmentController.redirectToEnrolForSa

  private def callAndContent(implicit request: UserRequest[?]): Option[(Call, String)] =
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

  def getSelfAssessmentCards(includeMDTITAdvert: Boolean)(implicit
    messages: Messages,
    request: UserRequest[?]
  ): Seq[HtmlFormat.Appendable] = request.trustedHelper match {
    case Some(_) => Nil
    case None    =>
      (
        enrolmentsHelper.mtdEnrolmentStatus(request.enrolments).isDefined,
        request.isSa,
        configDecorator.pegaSaRegistrationEnabled
      ) match {
        case (true, _, _)         =>
          Seq(itsaMergeView((current.currentYear + 1).toString))
        case (false, true, _)     =>
          callAndContent
            .map { case (redirectUrl, paragraphMessageKey) =>
              if (includeMDTITAdvert) {
                Seq(
                  saMergeView((current.currentYear + 1).toString, redirectUrl.url, paragraphMessageKey),
                  mtditAdvertTileView()
                )
              } else {
                Seq(
                  saMergeView((current.currentYear + 1).toString, redirectUrl.url, paragraphMessageKey)
                )
              }
            }
            .getOrElse(Nil)
        case (false, false, true) => Seq(selfAssessmentRegistrationView())
        case _                    => Nil
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
    taxComponents: List[String],
    trustedHelper: Option[TrustedHelper]
  )(implicit messages: Messages): List[Html] =
    if (trustedHelper.isEmpty) {
      List(getChildBenefitCard(), getMarriageAllowanceCard(taxComponents))
    } else {
      List.empty
    }

  def getChildBenefitCard()(implicit messages: Messages): HtmlFormat.Appendable = childBenefitSingleAccountView()

  def getMarriageAllowanceCard(taxComponents: List[String])(implicit
    messages: Messages
  ): HtmlFormat.Appendable =
    marriageAllowanceView(taxComponents)
}

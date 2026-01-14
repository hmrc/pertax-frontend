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
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.sca.logging.Logging
import util.DateTimeTools.current
import util.EnrolmentsHelper
import views.html.home.options.*

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HomeOptionsGenerator @Inject() (
  featureFlagService: FeatureFlagService,
  childBenefitSingleAccountView: ChildBenefitSingleAccountView,
  marriageAllowanceView: MarriageAllowanceView,
  taxSummariesView: TaxSummariesView,
  latestNewsAndUpdatesView: LatestNewsAndUpdatesView,
  enrolmentsHelper: EnrolmentsHelper,
  newsAndTilesConfig: NewsAndTilesConfig,
  nispView: NISPView,
  itsaMergeView: ItsaMergeView,
  payAsYouEarnView: PayAsYouEarnView,
  saMergeView: SaMergeView,
  taxCalcView: TaxCalcView,
  mtditAdvertView: MTDITAdvertView,
  trustedHelpersView: TrustedHelpersView,
  taskListView: TaskListView
)(implicit configDecorator: ConfigDecorator, ex: ExecutionContext)
    extends Logging {

  def getListOfTasks(implicit messages: Messages): Future[Html] =
    Future.successful(taskListView(Nil)(messages))

  def getCurrentTaxesAndBenefits(implicit request: UserRequest[AnyContent], messages: Messages): Future[Seq[Html]] = {

    val payeCardF     = getPayAsYouEarnCard
    val taxCalcCardsF = getDynamicTaxCalcCards

    for {
      payeCard     <- payeCardF
      taxCalcCards <- taxCalcCardsF
    } yield {
      val additionalCards =
        getSelfAssessmentCards :+ getNationalInsuranceCard

      Seq(payeCard) ++ taxCalcCards ++ additionalCards
    }
  }

  def getOtherTaxesAndBenefits(implicit request: UserRequest[AnyContent], messages: Messages): Future[Seq[Html]] = {

    val mtditAdvertFlagF = featureFlagService.get(MDTITAdvertToggle)

    for {
      mtditAdvertToggle <- mtditAdvertFlagF
      atsCard           <- getAnnualTaxSummaryCard
    } yield getMtditCard(
      mtditAdvertToggle.isEnabled
    ) ++ getBenefitCards(request.trustedHelper) ++ atsCard ++ getTrustedHelpersCard()
  }

  def getTrustedHelpersCard()(implicit
    request: UserRequest[AnyContent],
    messages: Messages
  ): Seq[HtmlFormat.Appendable] =
    if (request.trustedHelper.isEmpty)
      Seq(trustedHelpersView())
    else Nil

  private def getDynamicTaxCalcCards(implicit request: UserRequest[_], messages: Messages): Future[Seq[Html]] =
    if (request.trustedHelper.isDefined) {
      Future.successful(Seq.empty)
    } else {
      Future.successful(Seq(taxCalcView()))
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

  private def displaySACall: Call = controllers.interstitials.routes.InterstitialController.displaySelfAssessment

  private def handleSACall: Call = routes.SelfAssessmentController.handleSelfAssessment

  private def redirectToEnrolCall: Call = routes.SelfAssessmentController.redirectToEnrolForSa

  private def callAndContent(implicit request: UserRequest[?]): Option[(Call, String)] =
    request.saUserType match {
      case ActivatedOnlineFilerSelfAssessmentUser(_)       =>
        Some(displaySACall -> "label.newViewAndManageSA")
      case NotYetActivatedOnlineFilerSelfAssessmentUser(_) =>
        Some(handleSACall -> "label.activate_your_self_assessment")
      case WrongCredentialsSelfAssessmentUser(_)           =>
        Some(handleSACall -> "label.find_out_how_to_access_your_self_assessment")
      case NotEnrolledSelfAssessmentUser(_)                => Some(redirectToEnrolCall -> "label.request_access_to_your_sa")
      case sut                                             =>
        logger.warn(s"Unable to display self assessment card due to sa user type value of $sut")
        None
    }

  def getSelfAssessmentCards(implicit
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
        case (true, _, _)     =>
          Seq(itsaMergeView((current.currentYear + 1).toString))
        case (false, true, _) =>
          callAndContent
            .map { case (redirectUrl, paragraphMessageKey) =>
              Seq(
                saMergeView((current.currentYear + 1).toString, redirectUrl.url, paragraphMessageKey)
              )
            }
            .getOrElse(Nil)
        case _                => Nil
      }
  }

  private def getMtditCard(includeMTDITAdvert: Boolean)(implicit
    messages: Messages,
    request: UserRequest[?]
  ): Seq[HtmlFormat.Appendable] = request.trustedHelper match {
    case Some(_) => Nil
    case None    =>
      (
        enrolmentsHelper.mtdEnrolmentStatus(request.enrolments).isDefined,
        request.isSa,
        includeMTDITAdvert
      ) match {
        case (false, true, true) =>
          Seq(mtditAdvertView())
        case _                   => Nil
      }
  }

  def getAnnualTaxSummaryCard(implicit messages: Messages): Future[Seq[HtmlFormat.Appendable]] =
    featureFlagService.get(TaxSummariesTileToggle).map {
      case FeatureFlag(_, true) => Seq(taxSummariesView(configDecorator.annualTaxSaSummariesTileLinkShow))
      case _                    => Nil
    }

  def getLatestNewsAndUpdatesCard()(implicit
    messages: Messages,
    request: UserRequest[AnyContent]
  ): Option[HtmlFormat.Appendable] =
    if (configDecorator.isNewsAndUpdatesTileEnabled && newsAndTilesConfig.getNewsAndContentModelList().nonEmpty) {
      Some(latestNewsAndUpdatesView())
    } else {
      None
    }

  def getNationalInsuranceCard(implicit messages: Messages): HtmlFormat.Appendable =
    nispView()

  def getBenefitCards(
    trustedHelper: Option[TrustedHelper]
  )(implicit messages: Messages): List[Html] =
    if (trustedHelper.isEmpty) {
      List(getChildBenefitCard(), getMarriageAllowanceCard())
    } else {
      List.empty
    }

  def getChildBenefitCard()(implicit messages: Messages): HtmlFormat.Appendable = childBenefitSingleAccountView()

  def getMarriageAllowanceCard()(implicit
    messages: Messages
  ): HtmlFormat.Appendable =
    marriageAllowanceView()
}

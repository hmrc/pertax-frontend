/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.data.OptionT
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
    val cards1: Seq[Future[Seq[HtmlFormat.Appendable]]] =
      List(
        Future.successful(getLatestNewsAndUpdatesCard().toSeq),
        Future.successful(getPayAsYouEarnCard(taxComponentsState).toSeq)
      )

    val cards2: Seq[Future[Seq[HtmlFormat.Appendable]]] = Seq(
      featureFlagService.get(TaxcalcToggle).flatMap { toggle =>
        if (!toggle.isEnabled || request.trustedHelper.nonEmpty) {
          Future.successful(Nil)
        } else {
          taxCalcPartialService.getTaxCalcPartial
            .map(_.map(_.partialContent))
        }
      }
    )

    val cards3: Seq[Future[Seq[HtmlFormat.Appendable]]] = List(
      Future.successful(getSelfAssessmentCard().toSeq),
      Future.successful(getNationalInsuranceCard().toSeq)
    )

    Future
      .sequence(cards1 ++ cards2 ++ cards3)
      .map(_.flatten)
  }

  def getATSCard()(implicit request: UserRequest[AnyContent], messages: Messages): Future[Seq[Html]] = {

    val card: Seq[Future[Seq[HtmlFormat.Appendable]]] = List(
      if (request.trustedHelper.isEmpty) {
        getAnnualTaxSummaryCard.value.map(_.toSeq)
      } else {
        Future.successful(Nil)
      }
    )

    Future
      .sequence(card)
      .map(_.flatten)
  }

  def getPayAsYouEarnCard(
    taxComponentsState: TaxComponentsState
  )(implicit request: UserRequest[_], messages: Messages): Option[HtmlFormat.Appendable] =
    taxComponentsState match {
      case TaxComponentsNotAvailableState => None
      case _                              => Some(payAsYouEarnView(configDecorator, request.authNino.withoutSuffix.takeRight(2)))
    }

  private def displaySACall: Call       = routes.InterstitialController.displaySelfAssessment
  private def handleSACall: Call        = routes.SelfAssessmentController.handleSelfAssessment
  private def redirectToEnrolCall: Call = routes.SelfAssessmentController.redirectToEnrolForSa

  private def callAndContent(implicit request: UserRequest[_]) =
    request.saUserType match {
      case ActivatedOnlineFilerSelfAssessmentUser(_)       => Some(Tuple2(displaySACall, "label.viewAndManageSA"))
      case NotYetActivatedOnlineFilerSelfAssessmentUser(_) =>
        Some(Tuple2(handleSACall, "label.activate_your_self_assessment"))
      case WrongCredentialsSelfAssessmentUser(_)           =>
        Some(Tuple2(handleSACall, "label.find_out_how_to_access_your_self_assessment"))
      case NotEnrolledSelfAssessmentUser(_)                => Some(Tuple2(redirectToEnrolCall, "label.request_access_to_your_sa"))
      case sut                                             =>
        logger.warn(s"Unable to display self assessment card due to sa user type value of $sut")
        None
    }

  def getSelfAssessmentCard()(implicit
    messages: Messages,
    request: UserRequest[_]
  ): Option[HtmlFormat.Appendable] = {
    val isItsaEnrolled = enrolmentsHelper.itsaEnrolmentStatus(request.enrolments).isDefined
    request.trustedHelper.map(_ => None).getOrElse {
      if (isItsaEnrolled || request.isSa) {
        if (isItsaEnrolled) {
          Some(itsaMergeView((current.currentYear + 1).toString))
        } else {
          callAndContent match {
            case Some((redirectUrl, paragraphMessageKey)) =>
              Some(saMergeView((current.currentYear + 1).toString, redirectUrl.url, paragraphMessageKey))
            case _                                        => None
          }
        }
      } else if (configDecorator.pegaSaRegistrationEnabled) { // Temporary condition for Pega
        Some(selfAssessmentRegistrationView())
      } else {
        None
      }
    }
  }

  def getAnnualTaxSummaryCard(implicit
    request: UserRequest[AnyContent],
    messages: Messages
  ): OptionT[Future, HtmlFormat.Appendable] =
    OptionT(featureFlagService.get(TaxSummariesTileToggle).map { featureFlag =>
      if (featureFlag.isEnabled) {
        val url = if (request.isSaUserLoggedIntoCorrectAccount) {
          configDecorator.annualTaxSaSummariesTileLink
        } else {
          configDecorator.annualTaxPayeSummariesTileLink
        }

        Some(taxSummariesView(url))
      } else {
        None
      }
    })

  def getLatestNewsAndUpdatesCard()(implicit messages: Messages): Option[HtmlFormat.Appendable] =
    if (configDecorator.isNewsAndUpdatesTileEnabled && newsAndTilesConfig.getNewsAndContentModelList().nonEmpty) {
      Some(latestNewsAndUpdatesView())
    } else {
      None
    }

  def getNationalInsuranceCard()(implicit messages: Messages): Option[HtmlFormat.Appendable] =
    Some(nispView())

  def getBenefitCards(
    taxComponents: Option[TaxComponents],
    trustedHelper: Option[TrustedHelper]
  )(implicit messages: Messages): List[Html] =
    if (trustedHelper.isEmpty) {
      List(
        getChildBenefitCard(),
        getMarriageAllowanceCard(taxComponents),
        getTaxCreditsCard()
      ).flatten
    } else {
      List.empty
    }

  def getTaxCreditsCard()(implicit messages: Messages): Some[HtmlFormat.Appendable] =
    Some(taxCreditsView())

  def getChildBenefitCard()(implicit messages: Messages): Option[HtmlFormat.Appendable] =
    Some(childBenefitSingleAccountView())

  def getMarriageAllowanceCard(taxComponents: Option[TaxComponents])(implicit
    messages: Messages
  ): Some[HtmlFormat.Appendable] =
    Some(marriageAllowanceView(taxComponents))

}

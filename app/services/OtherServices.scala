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

import cats.data.EitherT
import cats.implicits.*
import com.google.inject.Inject
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.*
import models.MtdUserType.*
import models.admin.MTDUserStatusToggle
import play.api.Logging
import play.api.i18n.Messages
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.time.CurrentTaxYear
import util.EnrolmentsHelper

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class OtherServices @Inject() (
  configDecorator: ConfigDecorator,
  fandFService: FandFService,
  taiService: TaiService,
  enrolmentStoreProxyService: EnrolmentStoreProxyService,
  featureFlagService: FeatureFlagService,
  enrolmentsHelper: EnrolmentsHelper
)(implicit ec: ExecutionContext)
    extends CurrentTaxYear
    with Logging {

  def getOtherServices(implicit
    request: UserRequest[_],
    hc: HeaderCarrier,
    messages: Messages
  ): Future[Seq[OtherService]] = {

    val isTrustedHelperUser = request.trustedHelper.isDefined

    Future
      .sequence(
        Seq(
          getSelfAssessmentOtherServiceTile(request.saUserType, isTrustedHelperUser),
          getMtdOtherServiceTile(isTrustedHelperUser),
          getChildBenefitOtherServiceTile(isTrustedHelperUser),
          getMarriageAllowanceOtherServiceTile(request.authNino, isTrustedHelperUser),
          getAnnualTaxSummaryOtherServiceTile(isTrustedHelperUser),
          getTrustedHelperOtherServiceTile(request.authNino, isTrustedHelperUser)
        )
      )
      .map(_.flatten)
  }

  private def noneIfTrustedHelper[A](isTrustedHelperUser: Boolean)(fa: => Future[Option[A]]): Future[Option[A]] =
    if (isTrustedHelperUser) Future.successful(None) else fa

  private def mtdTile(linkUrl: String)(implicit messages: Messages): OtherService =
    OtherService(
      messages("label.mtd_for_it"),
      linkUrl,
      gaAction = Some("MTDIT"),
      gaLabel = Some("Making Tax Digital for Income Tax")
    )

  private def saTile(title: String, linkUrl: String): OtherService =
    OtherService(
      title,
      linkUrl,
      gaAction = Some("Income"),
      gaLabel = Some("Self Assessment")
    )

  private def fetchMtdUserStatus()(implicit
    request: UserRequest[_],
    hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, MtdUser] =
    for {
      toggle <- featureFlagService.getAsEitherT(MTDUserStatusToggle)
      status <-
        if (!toggle.isEnabled)
          EitherT.rightT[Future, UpstreamErrorResponse](NonFilerMtdUser: MtdUser)
        else
          enrolmentStoreProxyService.getMtdUserType(request.authNino)
    } yield status

  private def mtdLinkForStatus(status: MtdUser): Option[String] =
    status match {
      case NonFilerMtdUser =>
        Some(controllers.interstitials.routes.MtdAdvertInterstitialController.displayMTDITPage.url)

      case NotEnrolledMtdUser =>
        Some(controllers.routes.ClaimMtdFromPtaController.start.url)

      case WrongCredentialsMtdUser(_, _) =>
        Some(controllers.interstitials.routes.MtdAdvertInterstitialController.displayMTDITPage.url)

      case _ =>
        None
    }

  def getMtdOtherServiceTile(
    isTrustedHelperUser: Boolean
  )(implicit
    request: UserRequest[_],
    hc: HeaderCarrier,
    messages: Messages
  ): Future[Option[OtherService]] =
    noneIfTrustedHelper(isTrustedHelperUser) {

      if (enrolmentsHelper.mtdEnrolmentStatus(request.enrolments).nonEmpty) Future.successful(None)
      else {
        fetchMtdUserStatus()
          .fold(
            error => {
              logger.warn(
                s"[OtherServices][getMtdOtherServiceTile] Unable to determine MTD status: ${error.getMessage}"
              )
              None
            },
            status => mtdLinkForStatus(status).map(mtdTile)
          )
      }
    }

  def getSelfAssessmentOtherServiceTile(
    saUserType: SelfAssessmentUserType,
    isTrustedHelperUser: Boolean
  )(implicit messages: Messages): Future[Option[OtherService]] =
    noneIfTrustedHelper(isTrustedHelperUser) {
      Future.successful {
        saUserType match {
          case NotEnrolledSelfAssessmentUser(_) =>
            Some(
              saTile(
                title = messages("label.self_assessment"),
                linkUrl = controllers.routes.SelfAssessmentController.requestAccess.url
              )
            )

          case NotYetActivatedOnlineFilerSelfAssessmentUser(_) =>
            Some(
              saTile(
                title = messages("label.self_assessment"),
                linkUrl = configDecorator.ssoToActivateSaEnrolmentPinUrl
              )
            )

          case _ =>
            None
        }
      }
    }

  def getChildBenefitOtherServiceTile(
    isTrustedHelperUser: Boolean
  )(implicit messages: Messages): Future[Option[OtherService]] =
    noneIfTrustedHelper(isTrustedHelperUser) {
      Future.successful(
        Some(
          OtherService(
            messages("label.child_benefit"),
            controllers.interstitials.routes.InterstitialController.displayChildBenefitsSingleAccountView.url,
            gaAction = Some("Benefits"),
            gaLabel = Some("Child Benefit")
          )
        )
      )
    }

  def getAnnualTaxSummaryOtherServiceTile(
    isTrustedHelperUser: Boolean
  )(implicit messages: Messages): Future[Option[OtherService]] =
    noneIfTrustedHelper(isTrustedHelperUser) {
      Future.successful(
        Some(
          OtherService(
            messages("card.ats.heading"),
            configDecorator.annualTaxSaSummariesTileLinkShow,
            gaAction = Some("Tax Summaries"),
            gaLabel = Some("Annual Tax Summary")
          )
        )
      )
    }

  def getMarriageAllowanceOtherServiceTile(
    nino: Nino,
    isTrustedHelperUser: Boolean
  )(implicit
    hc: HeaderCarrier,
    request: UserRequest[_],
    messages: Messages
  ): Future[Option[OtherService]] =
    noneIfTrustedHelper(isTrustedHelperUser) {
      taiService.getTaxComponentsList(nino, current.currentYear).map { components =>
        val hasMarriageAllowance =
          components.contains("MarriageAllowanceReceived") || components.contains("MarriageAllowanceTransferred")

        if (hasMarriageAllowance) None
        else {
          Some(
            OtherService(
              messages("title.marriage_allowance"),
              "/marriage-allowance-application/history",
              gaAction = Some("Benefits"),
              gaLabel = Some("Marriage Allowance")
            )
          )
        }
      }
    }

  def getTrustedHelperOtherServiceTile(
    nino: Nino,
    isTrustedHelperUser: Boolean
  )(implicit
    hc: HeaderCarrier,
    messages: Messages
  ): Future[Option[OtherService]] =
    noneIfTrustedHelper(isTrustedHelperUser) {
      fandFService.isAnyFandFRelationships(nino).map {
        case false =>
          Some(
            OtherService(
              messages("label.trusted_helpers_heading"),
              configDecorator.manageTrustedHelpersUrl,
              gaAction = Some("Account"),
              gaLabel = Some("Trusted helpers")
            )
          )
        case true  => None
      }
    }

  override def now: () => LocalDate = LocalDate.now
}

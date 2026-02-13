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
import play.api.i18n.Messages
import uk.gov.hmrc.http.HeaderCarrier
import services.FandFService
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.time.CurrentTaxYear

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class OtherServices @Inject() (
  configDecorator: ConfigDecorator,
  fandFService: FandFService,
  taiService: TaiService
)(implicit ec: ExecutionContext)
    extends CurrentTaxYear {

  def getOtherServices(implicit
    request: UserRequest[?],
    hc: HeaderCarrier,
    messages: Messages
  ): Future[Seq[OtherService]] = {
    // todo MTD is missing
    val selfAssessment    = getSelfAssessment(request.saUserType, request.trustedHelper.isDefined)
    val childBenefits     = getChildBenefit(request.trustedHelper.isDefined)
    val marriageAllowance = getMarriageAllowance(request.authNino, request.trustedHelper.isDefined)
    val annualTaxSummary  = getAnnualTaxSummaries(request.trustedHelper.isDefined)
    val trustedHelper     = getTrustedHelper(request.authNino, request.trustedHelper.isDefined)

    Future
      .sequence(Seq(selfAssessment, childBenefits, marriageAllowance, annualTaxSummary, trustedHelper))
      .map(_.flatten)
  }

  def getSelfAssessment(saUserType: SelfAssessmentUserType, trustedHelperEnabled: Boolean)(implicit
    messages: Messages
  ): Future[Option[OtherService]] =
    Future.successful(if (trustedHelperEnabled) {
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
        case _                                => None
      }
    })

  def getChildBenefit(trustedHelperEnabled: Boolean)(implicit messages: Messages): Future[Option[OtherService]] =
    Future.successful(if (trustedHelperEnabled) {
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
    })

  def getMarriageAllowance(nino: Nino, isTrustedHelper: Boolean)(implicit
    hc: HeaderCarrier,
    request: UserRequest[?],
    messages: Messages
  ) =
    if (isTrustedHelper) {
      Future.successful(None)
    } else {
      taiService.getTaxComponentsList(nino, current.currentYear).map {
        case taxComponents
            if taxComponents
              .contains("MarriageAllowanceReceived") || taxComponents.contains("MarriageAllowanceTransferred") =>
          None
        case _ =>
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

  def getAnnualTaxSummaries(trustedHelperEnabled: Boolean)(implicit messages: Messages): Future[Option[OtherService]] =
    Future.successful(if (trustedHelperEnabled) {
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
    })

  def getTrustedHelper(nino: Nino, isTrustedHelper: Boolean)(implicit
    hc: HeaderCarrier,
    messages: Messages
  ): Future[Option[OtherService]] =
    if (isTrustedHelper) {
      Future.successful(None)
    } else {
      fandFService
        .isAnyFandFRelationships(nino)
        .map {
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

    /* todo implement MTD
    gaAction = Some("MTDIT"),
    gaLabel = Some("Making Tax Digital for Income Tax"),
     */

  override def now: () => LocalDate = LocalDate.now

}

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

import scala.concurrent.{ExecutionContext, Future}

class OtherServices @Inject() (
  configDecorator: ConfigDecorator
)(implicit ec: ExecutionContext) {

  def getOtherServices(implicit request: UserRequest[?], messages: Messages): Future[Seq[OtherService]] = {
    // todo MTD is missing
    val selfAssessment    = getSelfAssessment(request.saUserType)
    val childBenefits     = getChildBenefit(request.trustedHelper.isDefined)
    val marriageAllowance = getMarriageAllowance(request.trustedHelper.isDefined)
    val annualTaxSummary  = getAnnualTaxSummaries
    val trustedHelper     = getTrustedHelper

    Future
      .sequence(Seq(selfAssessment, childBenefits, marriageAllowance, annualTaxSummary, trustedHelper))
      .map(_.flatten)
  }

  def getSelfAssessment(saUserType: SelfAssessmentUserType)(implicit messages: Messages): Future[Option[OtherService]] =
    Future.successful(saUserType match {
      case NonFilerSelfAssessmentUser       =>
        Some(
          OtherService(
            messages("label.self_assessment"),
            "https://www.gov.uk/self-assessment-tax-returns",
            gaAction = Some("Income"),
            gaLabel = Some("Self Assessment")
          )
        )
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

  // todo add check from tai and move to myService if transferee or transferor
  def getMarriageAllowance(trustedHelperEnabled: Boolean)(implicit messages: Messages): Future[Option[OtherService]] =
    Future.successful(if (trustedHelperEnabled) {
      None
    } else {
      Some(
        OtherService(
          messages("title.marriage_allowance"),
          "/marriage-allowance-application/history",
          gaAction = Some("Benefits"),
          gaLabel = Some("Marriage Allowance")
        )
      )
    })

  def getAnnualTaxSummaries(implicit messages: Messages): Future[Option[OtherService]] =
    Future.successful(
      Some(
        OtherService(
          messages("card.ats.heading"),
          configDecorator.annualTaxSaSummariesTileLinkShow,
          gaAction = Some("Tax Summaries"),
          gaLabel = Some("Annual Tax Summary")
          messages("card.ats.heading"),
          configDecorator.annualTaxSaSummariesTileLinkShow
        )
      )
    )

  def getTrustedHelper(implicit messages: Messages): Future[Option[OtherService]] =
    Future.successful(
      Some(
        OtherService(
          messages("label.trusted_helpers_heading"),
          configDecorator.manageTrustedHelpersUrl,
          gaAction = Some("Account"),
          gaLabel = Some("Trusted helpers")
        )
      )
    )

    /* todo implement MTD
    gaAction = Some("MTDIT"),
    gaLabel = Some("Making Tax Digital for Income Tax"),
     */

}

/*
 * Copyright 2017 HM Revenue & Customs
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

package controllers.auth

import javax.inject._

import config.ConfigDecorator
import controllers.bindable.{Origin, StrictContinueUrl}
import controllers.routes._
import models.{AmbiguousFilerSelfAssessmentUser, NotYetActivatedOnlineFilerSelfAssessmentUser}
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, Request}
import services._
import uk.gov.hmrc.play.frontend.auth._
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent._

@Singleton
class LocalPageVisibilityPredicateFactory @Inject() (
  enrolmentExceptionListService: EnrolmentExceptionListService,
  citizenDetailsService: CitizenDetailsService,
  selfAssessmentService: SelfAssessmentService,
  configDecorator: ConfigDecorator
) {

  val (eels, cds, sas) = (enrolmentExceptionListService, citizenDetailsService, selfAssessmentService)

  def build(successUrl: Option[StrictContinueUrl] = None, origin: Origin) = {
    val (s, o) = (successUrl, origin.toString)

    new LocalConfidenceLevelPredicate {

      override lazy val successUrl = s
      override lazy val upliftUrl = configDecorator.identityVerificationUpliftUrl
      override lazy val origin = o
      override lazy val onwardUrl = configDecorator.pertaxFrontendHost + ApplicationController.showUpliftJourneyOutcome(successUrl)
      override lazy val ivExceptionListEnabled = configDecorator.ivExeptionsEnabled
      override lazy val allowLowConfidenceSAEnabled = configDecorator.allowLowConfidenceSAEnabled
      override lazy val enrolmentExceptionListService = eels
      override lazy val citizenDetailsService = cds
      override lazy val selfAssessmentService = sas
    }

  }


}


trait LocalConfidenceLevelPredicate extends PageVisibilityPredicate with ConfidenceLevelChecker {

  def successUrl: Option[StrictContinueUrl]
  def upliftUrl: String
  def origin: String
  def onwardUrl: String
  def ivExceptionListEnabled: Boolean
  def allowLowConfidenceSAEnabled: Boolean
  def enrolmentExceptionListService: EnrolmentExceptionListService

  def selfAssessmentService: SelfAssessmentService

  def citizenDetailsService: CitizenDetailsService
  private lazy val failureUrl = onwardUrl
  private lazy val completionUrl = onwardUrl

  def apply(authContext: AuthContext, request: Request[AnyContent]): Future[PageVisibilityResult] = {
    implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, Some(request.session))
    if ( userHasHighConfidenceLevel(authContext) ) {
      Future.successful(PageIsVisible)
    } else {
      val saUtr = authContext.principal.accounts.sa.map(_.utr) match {
        case Some(x) => Future.successful(Some(x))
        case None =>
          selfAssessmentService.getSelfAssessmentUserType(Some(authContext)) map {
            case NotYetActivatedOnlineFilerSelfAssessmentUser(x) => Some(x)
            case AmbiguousFilerSelfAssessmentUser(x) => Some(x)
            case _ => None
          }
      }

      val ivExempt = if (allowLowConfidenceSAEnabled) Future.successful(true) else {
        if (ivExceptionListEnabled) {
          saUtr flatMap {
            case Some(x) => enrolmentExceptionListService.isAccountIdentityVerificationExempt(x)
            case None => Future.successful(false)
          }
        } else Future.successful(false)
      }

      ivExempt map {
        case true => new PageBlocked(Future.successful(Redirect(ApplicationController.ivExemptLandingPage(successUrl))))
        case false => new PageBlocked(Future.successful(buildIVUpliftUrl(ConfidenceLevel.L200)))
      }
    }
  }

  private def buildIVUpliftUrl(confidenceLevel: ConfidenceLevel) =
    Redirect(
      upliftUrl,
      Map("origin" -> Seq(origin),
        "confidenceLevel" -> Seq(confidenceLevel.level.toString),
        "completionURL" -> Seq(completionUrl),
        "failureURL" -> Seq(failureUrl)
      )
    )

}

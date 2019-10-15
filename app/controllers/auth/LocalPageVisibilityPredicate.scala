/*
 * Copyright 2019 HM Revenue & Customs
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

///*
// * Copyright 2019 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package controllers.auth
//
//import config.ConfigDecorator
//import controllers.routes
//import javax.inject._
//import play.api.mvc.Results._
//import play.api.mvc.{AnyContent, Request}
//import services._
//import uk.gov.hmrc.play.HeaderCarrierConverter
//import uk.gov.hmrc.play.binders.Origin
//import uk.gov.hmrc.play.frontend.auth._
//import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
//import uk.gov.hmrc.play.frontend.binders.SafeRedirectUrl
//
//import scala.concurrent._
//
//@Singleton
//class LocalPageVisibilityPredicateFactory @Inject()(
//  injectedCitizenDetailsService: CitizenDetailsService,
//  configDecorator: ConfigDecorator
//) {
//
//  def build(successUrl: Option[SafeRedirectUrl] = None, origin: Origin) = {
//    val (s, o) = (successUrl, origin.origin)
//
//    new LocalConfidenceLevelPredicate {
//
//      override lazy val successUrl = s
//      override lazy val upliftUrl = configDecorator.identityVerificationUpliftUrl
//      override lazy val origin = o
//      override lazy val onwardUrl = configDecorator.pertaxFrontendHost + routes.ApplicationController
//        .showUpliftJourneyOutcome(successUrl)
//      override lazy val allowLowConfidenceSAEnabled = configDecorator.allowLowConfidenceSAEnabled
//      override lazy val citizenDetailsService = injectedCitizenDetailsService
//    }
//  }
//}
//
//trait LocalConfidenceLevelPredicate extends PageVisibilityPredicate with ConfidenceLevelChecker {
//
//  def successUrl: Option[SafeRedirectUrl]
//  def upliftUrl: String
//  def origin: String
//  def onwardUrl: String
//  def allowLowConfidenceSAEnabled: Boolean
//  def citizenDetailsService: CitizenDetailsService
//
//  private lazy val failureUrl = onwardUrl
//  private lazy val completionUrl = onwardUrl
//
//  def apply(authContext: AuthContext, request: Request[AnyContent]): Future[PageVisibilityResult] = {
//    implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
//    if (userHasHighConfidenceLevel(authContext)) {
//      Future.successful(PageIsVisible)
//    } else {
//      if (allowLowConfidenceSAEnabled) {
//        Future.successful(
//          PageBlocked(Future.successful(Redirect(routes.ApplicationController.ivExemptLandingPage(successUrl)))))
//      } else {
//        Future.successful(PageBlocked(Future.successful(buildIVUpliftUrl(ConfidenceLevel.L200))))
//      }
//    }
//  }
//
//  private def buildIVUpliftUrl(confidenceLevel: ConfidenceLevel) =
//    Redirect(
//      upliftUrl,
//      Map(
//        "origin"          -> Seq(origin),
//        "confidenceLevel" -> Seq(confidenceLevel.level.toString),
//        "completionURL"   -> Seq(completionUrl),
//        "failureURL"      -> Seq(failureUrl))
//    )
//}

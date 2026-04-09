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

package controllers

import cats.data.EitherT
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithBreadcrumbAction}
import models.ClaimMtdFromPtaChoiceFormProvider
import models.MtdUserType.*
import models.admin.{ClaimMtdFromPtaToggle, MTDUserStatusToggle}
import play.api.mvc.*
import services.EnrolmentStoreProxyService
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import views.html.interstitial.MTDITClaimChoiceView
import views.html.iv.failure.TechnicalIssuesView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ClaimMtdFromPtaController @Inject() (
  authJourney: AuthJourney,
  withBreadcrumbAction: WithBreadcrumbAction,
  cc: MessagesControllerComponents,
  featureFlagService: FeatureFlagService,
  enrolmentStoreProxyService: EnrolmentStoreProxyService,
  mtditClaimChoiceView: MTDITClaimChoiceView,
  configDecorator: ConfigDecorator,
  technicalIssuesView: TechnicalIssuesView
)(implicit ec: ExecutionContext)
    extends PertaxBaseController(cc) {

  private def authenticate: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails andThen withBreadcrumbAction.addBreadcrumb(baseBreadcrumb)

  private def serviceUnavailableResult()(implicit request: Request[_]): Result =
    ServiceUnavailable(
      technicalIssuesView(routes.HomeController.index.url)(request, configDecorator)
    )

  private def ensureClaimMtdJourneyEnabled: EitherT[Future, UpstreamErrorResponse, Boolean] =
    for {
      mtdStatusToggle <- featureFlagService.getAsEitherT(MTDUserStatusToggle)
      claimToggle     <- featureFlagService.getAsEitherT(ClaimMtdFromPtaToggle)
    } yield mtdStatusToggle.isEnabled && claimToggle.isEnabled

  def start: Action[AnyContent] = authenticate.async { implicit request =>
    if (!request.isSa) {
      Future.successful(NotFound)
    } else {
      val action: EitherT[Future, UpstreamErrorResponse, Result] =
        for {
          enabled     <- ensureClaimMtdJourneyEnabled
          mtdUserType <- enrolmentStoreProxyService.getMtdUserType(request.authNino)
        } yield (enabled, mtdUserType) match {
          case (true, NotEnrolledMtdUser) =>
            Ok(
              mtditClaimChoiceView(
                postAction = routes.ClaimMtdFromPtaController.submit,
                ClaimMtdFromPtaChoiceFormProvider.form
              )
            )
          case _                          =>
            Redirect(controllers.interstitials.routes.MtdAdvertInterstitialController.displayMTDITPage)
        }

      action.fold(
        _ => serviceUnavailableResult(),
        identity
      )
    }
  }

  def submit: Action[AnyContent] = authenticate { implicit request =>
    ClaimMtdFromPtaChoiceFormProvider.form
      .bindFromRequest()
      .fold(
        formWithErrors =>
          BadRequest(
            mtditClaimChoiceView(postAction = routes.ClaimMtdFromPtaController.submit, claimMtdForm = formWithErrors)
          ),
        success =>
          if (success.choice) {
            Redirect(configDecorator.mtdClaimFromPtaHandoffUrl)
          } else {
            Redirect(routes.HomeController.index)
          }
      )
  }
}

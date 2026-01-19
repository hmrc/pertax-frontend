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

package controllers.interstitials

import cats.data.EitherT
import com.google.inject.Inject
import controllers.PertaxBaseController
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithBreadcrumbAction}
import error.ErrorRenderer
import models.MtdUserType.*
import models.admin.{ClaimMtdFromPtaToggle, MTDUserStatusToggle}
import play.api.Logging
import play.api.mvc.*
import services.EnrolmentStoreProxyService
import services.partials.{FormPartialService, SaPartialService}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.time.CurrentTaxYear
import util.EnrolmentsHelper
import views.html.interstitial.*

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class MtdAdvertInterstitialController @Inject() (
  val formPartialService: FormPartialService,
  val saPartialService: SaPartialService,
  authJourney: AuthJourney,
  withBreadcrumbAction: WithBreadcrumbAction,
  cc: MessagesControllerComponents,
  errorRenderer: ErrorRenderer,
  mtditAdvertPageView: MTDITAdvertPageView,
  enrolmentsHelper: EnrolmentsHelper,
  featureFlagService: FeatureFlagService,
  enrolmentStoreProxyService: EnrolmentStoreProxyService
)(implicit ec: ExecutionContext)
    extends PertaxBaseController(cc)
    with Logging
    with CurrentTaxYear {

  override def now: () => LocalDate = () => LocalDate.now()

  private val authenticate: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails andThen withBreadcrumbAction
      .addBreadcrumb(baseBreadcrumb)

  def displayMTDITPage: Action[AnyContent] = authenticate.async { implicit request =>
    val advertOk: Result = Ok(mtditAdvertPageView())

    if (request.trustedHelper.nonEmpty || enrolmentsHelper.mtdEnrolmentStatus(request.enrolments).nonEmpty) {
      errorRenderer.futureError(FORBIDDEN)
    } else {

      val eitherResult: EitherT[Future, UpstreamErrorResponse, Result] =
        featureFlagService.getAsEitherT(MTDUserStatusToggle).flatMap { statusToggle =>
          if (!statusToggle.isEnabled) {
            logger.warn("MTDUserStatusToggle is disabled")
            EitherT.rightT(advertOk)
          } else {
            enrolmentStoreProxyService.getMtdUserType(request.authNino).flatMap {
              case NonFilerMtdUser =>
                logger.info("User is not registered with MTD")
                EitherT.rightT(advertOk)

              case NotEnrolledMtdUser =>
                logger.info("User is registered but not enrolled with MTD")

                if (!request.isSaUserLoggedIntoCorrectAccount) {
                  logger.info("User is NOT logged into the correct SA account")
                  EitherT.rightT(advertOk)
                } else {
                  EitherT
                    .liftF(featureFlagService.get(ClaimMtdFromPtaToggle))
                    .map { claimToggle =>
                      if (claimToggle.isEnabled) {
                        Redirect(controllers.routes.ClaimMtdFromPtaController.start)
                      } else {
                        advertOk
                      }
                    }
                }

              case WrongCredentialsMtdUser(_, mtdCredId) =>
                logger.info(
                  s"Wrong account for MTD. Current cred is ${request.credentials.providerId} and MTD is on credential $mtdCredId}"
                )
                EitherT.rightT(advertOk)

              case EnrolledMtdUser(_) =>
                EitherT.rightT(
                  Redirect(controllers.interstitials.routes.InterstitialController.displayItsaMergePage)
                )

              case _ =>
                logger.info("Could not determine MTD user type")
                EitherT.rightT(advertOk)
            }
          }
        }

      eitherResult.fold(
        _ => advertOk,
        identity
      )
    }
  }
}

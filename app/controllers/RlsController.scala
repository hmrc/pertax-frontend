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

package controllers

import cats.data.EitherT
import com.google.inject.Inject
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.controllershelpers.AddressJourneyCachingHelper
import error.ErrorRenderer
import models.admin.RlsInterruptToggle
import models.dto.AddressPageVisitedDto
import models.{Address, AddressesLock}
import play.api.mvc.{Action, ActionBuilder, AnyContent, MessagesControllerComponents}
import repositories.EditAddressLockRepository
import routePages.HasAddressAlreadyVisitedPage
import services.CitizenDetailsService
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import util.AuditServiceTools.buildEvent
import views.html.personaldetails.CheckYourAddressInterruptView

import scala.concurrent.{ExecutionContext, Future}

class RlsController @Inject() (
  authJourney: AuthJourney,
  auditConnector: AuditConnector,
  cachingHelper: AddressJourneyCachingHelper,
  editAddressLockRepository: EditAddressLockRepository,
  featureFlagService: FeatureFlagService,
  citizenDetailsService: CitizenDetailsService,
  errorRenderer: ErrorRenderer,
  cc: MessagesControllerComponents,
  checkYourAddressInterruptView: CheckYourAddressInterruptView
)(implicit
  ec: ExecutionContext
) extends PertaxBaseController(cc) {

  private val authenticate: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails

  private def auditRls(mainAddress: Option[Address], postalAddress: Option[Address])(implicit
    request: UserRequest[_],
    ec: ExecutionContext
  ) =
    editAddressLockRepository.getAddressesLock(request.authNino.withoutSuffix).flatMap {
      case AddressesLock(residentialLock, postalLock) =>
        val residentialDetail =
          if (residentialLock) {
            "residentialAddressUpdated" -> Some("true")
          } else {
            "residentialRLS" -> Some(mainAddress.exists(_.isRls).toString)
          }
        val postalDetail      =
          if (postalLock) {
            "postalAddressUpdated" -> Some("true")
          } else {
            "postalRLS" -> Some(postalAddress.exists(_.isRls).toString)
          }

        auditConnector.sendEvent(
          buildEvent(
            "RLSInterrupt",
            "user_shown_rls_interrupt_page",
            Map(
              "nino"               -> Some(request.authNino.toString),
              residentialDetail,
              postalDetail,
              "residentialAddress" -> mainAddress.map(_.fullAddress.mkString(";")),
              "postalAddress"      -> postalAddress.map(_.fullAddress.mkString(";"))
            ).filter(_._2.isDefined)
          )
        )
    }

  def rlsInterruptOnPageLoad: Action[AnyContent] = authenticate.async { implicit request =>
    (for {
      rlsFlag            <- featureFlagService.getAsEitherT(RlsInterruptToggle)
      addressLock        <- EitherT[Future, UpstreamErrorResponse, AddressesLock](
                              editAddressLockRepository.getAddressesLock(request.authNino.withoutSuffix).map(Right(_))
                            )
      maybePersonDetails <- citizenDetailsService.personDetails(request.authNino)
    } yield
      if (rlsFlag.isEnabled) {
        val mainAddress = maybePersonDetails.flatMap { pd =>
          pd.address.map { address =>
            if (address.isRls && !addressLock.main) address else address.copy(isRls = false)
          }
        }

        val postalAddress = maybePersonDetails.flatMap { pd =>
          pd.correspondenceAddress.map { address =>
            if (address.isRls && !addressLock.postal) address else address.copy(isRls = false)
          }
        }

        if (mainAddress.exists(_.isRls) || postalAddress.exists(_.isRls)) {
          auditRls(mainAddress, postalAddress)
          cachingHelper.addToCache(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
          Ok(checkYourAddressInterruptView(mainAddress, postalAddress))
        } else {
          Redirect(routes.HomeController.index)
        }
      } else {
        Redirect(routes.HomeController.index)
      }).fold(
      _ => errorRenderer.error(INTERNAL_SERVER_ERROR),
      identity
    )
  }
}

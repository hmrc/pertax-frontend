/*
 * Copyright 2022 HM Revenue & Customs
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

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.dto.AddressPageVisitedDto
import models.{Address, AddressPageVisitedDtoId, AddressesLock}
import play.api.mvc.{Action, ActionBuilder, AnyContent, MessagesControllerComponents}
import repositories.EditAddressLockRepository
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.renderer.TemplateRenderer
import util.AuditServiceTools.buildEvent
import views.html.InternalServerErrorView
import views.html.personaldetails.CheckYourAddressInterruptView

import scala.concurrent.{ExecutionContext, Future}

class RlsController @Inject() (
  authJourney: AuthJourney,
  auditConnector: AuditConnector,
  cachingHelper: AddressJourneyCachingHelper,
  editAddressLockRepository: EditAddressLockRepository,
  cc: MessagesControllerComponents,
  checkYourAddressInterruptView: CheckYourAddressInterruptView,
  internalServerErrorView: InternalServerErrorView
)(implicit
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext
) extends PertaxBaseController(cc) {

  private val authenticate: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails

  private def auditRls(mainAddress: Option[Address], postalAddress: Option[Address])(implicit
    request: UserRequest[_],
    ec: ExecutionContext
  ) =
    editAddressLockRepository.getAddressesLock(request.nino.map(_.withoutSuffix).getOrElse("Nino")).flatMap {
      case AddressesLock(residentialLock, postalLock) =>
        val residentialDetail =
          if (residentialLock) "residentialAddressUpdated" -> Some("true")
          else "residentialRLS"                            -> Some(mainAddress.exists(_.isRls).toString)
        val postalDetail =
          if (postalLock) "postalAddressUpdated" -> Some("true")
          else "postalRLS"                       -> Some(postalAddress.exists(_.isRls).toString)

        auditConnector.sendEvent(
          buildEvent(
            "RLSInterrupt",
            "user_shown_rls_interrupt_page",
            Map(
              "nino" -> Some(request.nino.getOrElse("NoNino").toString),
              residentialDetail,
              postalDetail,
              "residentialAddress" -> mainAddress.map(_.fullAddress.mkString(";")),
              "postalAddress"      -> postalAddress.map(_.fullAddress.mkString(";"))
            ).filter(_._2.isDefined)
          )
        )
    }

  def rlsInterruptOnPageLoad: Action[AnyContent] = authenticate.async { implicit request =>
    if (configDecorator.rlsInterruptToggle) {
      editAddressLockRepository.getAddressesLock(request.nino.map(_.withoutSuffix).getOrElse("Nino")).flatMap {
        case AddressesLock(residentialLock, postalLock) =>
          request.personDetails
            .map { personDetails =>
              val mainAddress =
                personDetails.address.map { address =>
                  if (address.isRls && !residentialLock)
                    address
                  else
                    address.copy(isRls = false)
                }
              val postalAddress =
                personDetails.correspondenceAddress
                  .map { address =>
                    if (address.isRls && !postalLock)
                      address
                    else
                      address.copy(isRls = false)
                  }
              if (mainAddress.exists(_.isRls) || postalAddress.exists(_.isRls)) {
                auditRls(mainAddress, postalAddress)
                cachingHelper
                  .addToCache(AddressPageVisitedDtoId, AddressPageVisitedDto(true))
                Future.successful(
                  Ok(checkYourAddressInterruptView(mainAddress, postalAddress))
                )
              } else {
                Future.successful(Redirect(routes.HomeController.index))
              }

            }
            .getOrElse(Future.successful(InternalServerError(internalServerErrorView())))
      }
    } else {
      Future.successful(Redirect(routes.HomeController.index))
    }
  }
}

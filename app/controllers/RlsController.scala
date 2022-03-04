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
import controllers.auth.requests.UserRequest
import controllers.auth.AuthJourney
import controllers.controllershelpers.{AddressJourneyCachingHelper, CountryHelper}
import models.dto.AddressPageVisitedDto
import models.{Address, AddressPageVisitedDtoId, AddressesLock, EditCorrespondenceAddress, EditResidentialAddress}
import play.api.mvc.{Action, ActionBuilder, AnyContent, MessagesControllerComponents}
import repositories.EditAddressLockRepository
import uk.gov.hmrc.http.HeaderCarrier
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
  countryHelper: CountryHelper,
  ec: ExecutionContext
) extends PertaxBaseController(cc) {

  private val authenticate: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails

  private def auditRls(mainAddress: Option[Address], postalAddress: Option[Address])(implicit
    request: UserRequest[_],
    hc: HeaderCarrier = HeaderCarrier(),
    ec: ExecutionContext
  ) =
    editAddressLockRepository.getAddressesLock(request.nino.map(_.withoutSuffix).getOrElse("Nino")).flatMap {
      case AddressesLock(residentialLock, postalLock) =>
        val residentialDetail =
          if (residentialLock) "residential address has been updated" -> Some("true")
          else "Is residential address rls"                           -> Some(mainAddress.isDefined.toString)
        val postalDetail =
          if (postalLock) "postal address has been updated" -> Some("true")
          else "Is postal address rls"                      -> Some(postalAddress.isDefined.toString)

        auditConnector.sendEvent(
          buildEvent(
            "RLSInterrupt",
            "user_shown_rls_interrupt_page",
            Map(
              "nino" -> Some(request.nino.getOrElse("NoNino").toString),
              residentialDetail,
              postalDetail,
              "Residential address" -> mainAddress.map(_.fullAddress.mkString(";")),
              "Postal address"      -> postalAddress.map(_.fullAddress.mkString(";"))
            ).filter(_._2.isDefined)
          )
        )
    }

  def rlsInterruptOnPageLoad(): Action[AnyContent] = authenticate.async { implicit request =>
    if (configDecorator.rlsInterruptToggle) {
      editAddressLockRepository.getAddressesLock(request.nino.map(_.withoutSuffix).getOrElse("Nino")).flatMap {
        case AddressesLock(residentialLock, postalLock) =>
          request.personDetails
            .map { personDetails =>
              val mainAddress =
                personDetails.address.flatMap(address => if (address.isRls && !residentialLock) Some(address) else None)
              val postalAddress =
                personDetails.correspondenceAddress
                  .flatMap(address => if (address.isRls && !postalLock) Some(address) else None)
              if (mainAddress.isDefined || postalAddress.isDefined) {
                auditRls(mainAddress, postalAddress)
                cachingHelper
                  .addToCache(AddressPageVisitedDtoId, AddressPageVisitedDto(true))
                Future.successful(
                  Ok(checkYourAddressInterruptView(mainAddress, postalAddress))
                )
              } else {
                Future.successful(Redirect(routes.HomeController.index()))
              }

            }
            .getOrElse(Future.successful(InternalServerError(internalServerErrorView())))
      }
    } else {
      Future.successful(Redirect(routes.HomeController.index()))
    }
  }
}

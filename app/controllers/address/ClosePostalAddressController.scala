/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers.address

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.bindable.PostalAddrType
import controllers.controllershelpers.AddressJourneyAuditingHelper.auditForClosingPostalAddress
import models.{Address, PersonDetails}
import models.dto.ClosePostalAddressChoiceDto
import org.joda.time.LocalDate
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import repositories.EditAddressLockRepository
import services.{AddressMovedService, CitizenDetailsService, LocalSessionCache, UpdateAddressBadRequestResponse, UpdateAddressErrorResponse, UpdateAddressSuccessResponse, UpdateAddressUnexpectedResponse}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.renderer.TemplateRenderer
import util.AuditServiceTools.buildEvent
import util.LocalPartialRetriever

import scala.concurrent.{ExecutionContext, Future}

class ClosePostalAddressController @Inject()(
  val citizenDetailsService: CitizenDetailsService,
  val addressMovedService: AddressMovedService,
  val editAddressLockRepository: EditAddressLockRepository,
  auditConnector: AuditConnector,
  sessionCache: LocalSessionCache,
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction,
  cc: MessagesControllerComponents
)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends AddressBaseController(sessionCache, authJourney, withActiveTabAction, cc) {

  def onPageLoad: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        val address = getAddress(personDetails.address).fullAddress
        Future.successful(
          Ok(views.html.personaldetails.closeCorrespondenceAddressChoice(address, ClosePostalAddressChoiceDto.form)))
      }
    }

  def onSubmit: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personalDetails =>
        ClosePostalAddressChoiceDto.form.bindFromRequest.fold(
          formWithErrors => {
            Future.successful(
              BadRequest(
                views.html.personaldetails
                  .closeCorrespondenceAddressChoice(getAddress(personalDetails.address).fullAddress, formWithErrors))
            )
          },
          closePostalAddressChoiceDto => {
            if (closePostalAddressChoiceDto.value) {
              Future.successful(Redirect(routes.ClosePostalAddressController.onPageLoadConfirm()))
            } else {
              Future.successful(Redirect(controllers.routes.AddressController.personalDetails()))
            }
          }
        )
      }
    }

  def onPageLoadConfirm: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        val address = getAddress(personDetails.address).fullAddress
        Future.successful(Ok(views.html.personaldetails.confirmCloseCorrespondenceAddress(address)))
      }
    }

  def onSubmitConfirm: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { nino => personDetails =>
        for {
          addressChanges <- editAddressLockRepository.get(nino.withoutSuffix)
          result <- if (addressChanges.nonEmpty) {
                     Future.successful(Redirect(controllers.routes.AddressController.personalDetails()))
                   } else closePostalAddress(nino, personDetails)

        } yield result
      }
    }

  private def closePostalAddress(nino: Nino, personDetails: PersonDetails)(
    implicit request: UserRequest[_]): Future[Result] = {
    def internalServerError =
      InternalServerError(
        views.html.error(
          "global.error.InternalServerError500.title",
          Some("global.error.InternalServerError500.title"),
          List("global.error.InternalServerError500.message")
        ))

    val address = getAddress(personDetails.correspondenceAddress)
    val closingAddress = address.copy(endDate = Some(LocalDate.now), startDate = Some(LocalDate.now))

    for {
      response <- citizenDetailsService.updateAddress(nino, personDetails.etag, closingAddress)
      action <- response match {
                 case UpdateAddressBadRequestResponse =>
                   Future.successful(
                     BadRequest(
                       views.html.error(
                         "global.error.BadRequest.title",
                         Some("global.error.BadRequest.title"),
                         List("global.error.BadRequest.message1", "global.error.BadRequest.message2")
                       )
                     )
                   )
                 case UpdateAddressUnexpectedResponse(_) | UpdateAddressErrorResponse(_) =>
                   Future.successful(internalServerError)
                 case UpdateAddressSuccessResponse =>
                   for {
                     _ <- auditConnector.sendEvent(
                           buildEvent(
                             "closedAddressSubmitted",
                             "closure_of_correspondence",
                             auditForClosingPostalAddress(closingAddress, personDetails.etag, "correspondence")))
                     _        <- clearCache() //This clears ENTIRE session cache, no way to target individual keys
                     inserted <- editAddressLockRepository.insert(nino.withoutSuffix, PostalAddrType)
                     _        <- addressMovedService.moved(address.postcode.getOrElse(""), address.postcode.getOrElse(""))
                   } yield
                     if (inserted) {
                       Ok(
                         views.html.personaldetails.updateAddressConfirmation(
                           PostalAddrType,
                           closedPostalAddress = true,
                           Some(getAddress(personDetails.address).fullAddress),
                           None
                         )
                       )
                     } else {
                       internalServerError
                     }
               }
    } yield action
  }

  private def getAddress(address: Option[Address]): Address =
    address match {
      case Some(address) => address
      case None          => throw new Exception("Address does not exist in the current context")
    }
}

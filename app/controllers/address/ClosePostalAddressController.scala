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
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.PersonDetails
import models.dto.ClosePostalAddressChoiceDto
import org.joda.time.LocalDate
import play.api.Logger
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import repositories.EditAddressLockRepository
import services.{AddressMovedService, CitizenDetailsService, UpdateAddressBadRequestResponse, UpdateAddressErrorResponse, UpdateAddressSuccessResponse, UpdateAddressUnexpectedResponse}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.renderer.TemplateRenderer
import util.AuditServiceTools.buildEvent
import util.LocalPartialRetriever
import views.html.error
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.{CloseCorrespondenceAddressChoiceView, ConfirmCloseCorrespondenceAddressView, UpdateAddressConfirmationView}

import scala.concurrent.{ExecutionContext, Future}

class ClosePostalAddressController @Inject()(
  val citizenDetailsService: CitizenDetailsService,
  val editAddressLockRepository: EditAddressLockRepository,
  val addressMovedService: AddressMovedService,
  cachingHelper: AddressJourneyCachingHelper,
  auditConnector: AuditConnector,
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction,
  cc: MessagesControllerComponents,
  closeCorrespondenceAddressChoiceView: CloseCorrespondenceAddressChoiceView,
  confirmCloseCorrespondenceAddressView: ConfirmCloseCorrespondenceAddressView,
  updateAddressConfirmationView: UpdateAddressConfirmationView,
  displayAddressInterstitialView: DisplayAddressInterstitialView)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends AddressController(authJourney, withActiveTabAction, cc, displayAddressInterstitialView) {

  def onPageLoad: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        val address = getAddress(personDetails.address).fullAddress
        Future.successful(Ok(closeCorrespondenceAddressChoiceView(address, ClosePostalAddressChoiceDto.form)))
      }
    }

  def onSubmit: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personalDetails =>
        ClosePostalAddressChoiceDto.form.bindFromRequest.fold(
          formWithErrors => {
            Future.successful(
              BadRequest(
                closeCorrespondenceAddressChoiceView(getAddress(personalDetails.address).fullAddress, formWithErrors))
            )
          },
          closePostalAddressChoiceDto => {
            if (closePostalAddressChoiceDto.value) {
              Future.successful(Redirect(routes.ClosePostalAddressController.confirmPageLoad()))
            } else {
              Future.successful(Redirect(routes.PersonalDetailsController.onPageLoad()))
            }
          }
        )
      }
    }

  def confirmPageLoad: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        val address = getAddress(personDetails.address).fullAddress
        Future.successful(Ok(confirmCloseCorrespondenceAddressView(address)))
      }
    }

  def confirmSubmit: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { nino => personDetails =>
        for {
          addressChanges <- editAddressLockRepository.get(nino.withoutSuffix)
          result <- if (addressChanges.nonEmpty) {
                     Future.successful(Redirect(routes.PersonalDetailsController.onPageLoad()))
                   } else {
                     submitConfirmClosePostalAddress(nino, personDetails)
                   }
        } yield result
      }
    }

  private def submitConfirmClosePostalAddress(nino: Nino, personDetails: PersonDetails)(
    implicit request: UserRequest[_]): Future[Result] = {

    val address = getAddress(personDetails.correspondenceAddress)
    val closingAddress = address.copy(endDate = Some(LocalDate.now), startDate = Some(LocalDate.now))

    citizenDetailsService.getEtag(nino.nino) flatMap {
      case None =>
        Logger.error("Failed to retrieve Etag from citizen-details")
        Future.successful(internalServerError)

      case Some(version) =>
        for {
          response <- citizenDetailsService.updateAddress(nino, version.etag, closingAddress)
          action <- response match {
                     case UpdateAddressBadRequestResponse =>
                       Future.successful(
                         BadRequest(
                           error(
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
                                 auditForClosingPostalAddress(closingAddress, version.etag, "correspondence")))
                         _ <- cachingHelper
                               .clearCache() //This clears ENTIRE session cache, no way to target individual keys
                         inserted <- editAddressLockRepository.insert(nino.withoutSuffix, PostalAddrType)
                         _ <- addressMovedService
                               .moved(address.postcode.getOrElse(""), address.postcode.getOrElse(""))
                       } yield
                         if (inserted) {
                           Ok(
                             updateAddressConfirmationView(
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
  }
}

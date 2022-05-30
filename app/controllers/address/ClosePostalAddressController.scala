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

package controllers.address

import com.google.inject.Inject
import config.ConfigDecorator
import connectors.{CitizenDetailsConnector, UpdateAddressBadRequestResponse, UpdateAddressErrorResponse, UpdateAddressSuccessResponse, UpdateAddressUnexpectedResponse}
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.bindable.PostalAddrType
import controllers.controllershelpers.AddressJourneyAuditingHelper.auditForClosingPostalAddress
import controllers.controllershelpers.AddressJourneyCachingHelper
import error.ErrorRenderer
import models.{Address, AddressJourneyTTLModel, EditCorrespondenceAddress, PersonDetails}
import models.dto.ClosePostalAddressChoiceDto
import org.joda.time.LocalDate
import play.api.Logging
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import repositories.EditAddressLockRepository
import services._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import util.AuditServiceTools.buildEvent
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.{CloseCorrespondenceAddressChoiceView, ConfirmCloseCorrespondenceAddressView, UpdateAddressConfirmationView}

import scala.concurrent.{ExecutionContext, Future}

class ClosePostalAddressController @Inject() (
  val citizenDetailsConnector: CitizenDetailsConnector,
  val editAddressLockRepository: EditAddressLockRepository,
  val addressMovedService: AddressMovedService,
  cachingHelper: AddressJourneyCachingHelper,
  auditConnector: AuditConnector,
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction,
  cc: MessagesControllerComponents,
  errorRenderer: ErrorRenderer,
  closeCorrespondenceAddressChoiceView: CloseCorrespondenceAddressChoiceView,
  confirmCloseCorrespondenceAddressView: ConfirmCloseCorrespondenceAddressView,
  updateAddressConfirmationView: UpdateAddressConfirmationView,
  displayAddressInterstitialView: DisplayAddressInterstitialView
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends AddressController(authJourney, withActiveTabAction, cc, displayAddressInterstitialView) with Logging {

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
          formWithErrors =>
            Future.successful(
              BadRequest(
                closeCorrespondenceAddressChoiceView(getAddress(personalDetails.address).fullAddress, formWithErrors)
              )
            ),
          closePostalAddressChoiceDto =>
            if (closePostalAddressChoiceDto.value) {
              Future.successful(Redirect(routes.ClosePostalAddressController.confirmPageLoad))
            } else {
              Future.successful(Redirect(routes.PersonalDetailsController.onPageLoad))
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
          result <- {
            if (addressChanges.map(_.editedAddress).exists(_.isInstanceOf[EditCorrespondenceAddress])) {
              Future.successful(Redirect(routes.PersonalDetailsController.onPageLoad))
            } else {
              submitConfirmClosePostalAddress(nino, personDetails)
            }
          }
        } yield result

      }
    }

  private def submitConfirmClosePostalAddress(nino: Nino, personDetails: PersonDetails)(implicit
    request: UserRequest[_]
  ): Future[Result] = {

    val address = getAddress(personDetails.correspondenceAddress)
    val closingAddress = address.copy(endDate = Some(LocalDate.now), startDate = Some(LocalDate.now))

    citizenDetailsConnector.getEtag(nino.nino) flatMap {
      case None =>
        logger.error("Failed to retrieve Etag from citizen-details")
        errorRenderer.futureError(INTERNAL_SERVER_ERROR)

      case Some(version) =>
        for {
          response <- citizenDetailsConnector.updateAddress(nino, version.etag, closingAddress)
          action <- response match {
                      case UpdateAddressBadRequestResponse =>
                        errorRenderer.futureError(BAD_REQUEST)
                      case UpdateAddressUnexpectedResponse(_) | UpdateAddressErrorResponse(_) =>
                        errorRenderer.futureError(INTERNAL_SERVER_ERROR)
                      case UpdateAddressSuccessResponse =>
                        for {
                          _ <- auditConnector.sendEvent(
                                 buildEvent(
                                   "closedAddressSubmitted",
                                   "closure_of_correspondence",
                                   auditForClosingPostalAddress(closingAddress, version.etag, "correspondence")
                                 )
                               )
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
                            errorRenderer.error(INTERNAL_SERVER_ERROR)
                          }
                    }
        } yield action
    }
  }

  private def getAddress(address: Option[Address]): Address =
    address match {
      case Some(address) => address
      case None          => throw new Exception("Address does not exist in the current context")
    }
}

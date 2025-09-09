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

package controllers.address

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.bindable.PostalAddrType
import controllers.controllershelpers.AddressJourneyAuditingHelper.auditForClosingPostalAddress
import controllers.controllershelpers.AddressJourneyCachingHelper
import error.ErrorRenderer
import models.dto.ClosePostalAddressChoiceDto
import models.{Address, EditCorrespondenceAddress, PersonDetails}
import play.api.Logging
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import repositories.EditAddressLockRepository
import services._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import util.AuditServiceTools.buildEvent
import views.html.InternalServerErrorView
import views.html.personaldetails.{CloseCorrespondenceAddressChoiceView, ConfirmCloseCorrespondenceAddressView, UpdateAddressConfirmationView}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class ClosePostalAddressController @Inject() (
  val citizenDetailsService: CitizenDetailsService,
  val editAddressLockRepository: EditAddressLockRepository,
  val addressMovedService: AddressMovedService,
  cachingHelper: AddressJourneyCachingHelper,
  auditConnector: AuditConnector,
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  errorRenderer: ErrorRenderer,
  closeCorrespondenceAddressChoiceView: CloseCorrespondenceAddressChoiceView,
  confirmCloseCorrespondenceAddressView: ConfirmCloseCorrespondenceAddressView,
  updateAddressConfirmationView: UpdateAddressConfirmationView,
  featureFlagService: FeatureFlagService,
  internalServerErrorView: InternalServerErrorView
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends AddressController(
      authJourney,
      cc,
      featureFlagService,
      errorRenderer,
      citizenDetailsService,
      internalServerErrorView
    )
    with Logging {

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
        ClosePostalAddressChoiceDto.form
          .bindFromRequest()
          .fold(
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
          result         <- {
            if (addressChanges.map(_.editedAddress).exists(_.isInstanceOf[EditCorrespondenceAddress])) {
              Future.successful(Redirect(routes.PersonalDetailsController.onPageLoad))
            } else {
              submitConfirmClosePostalAddress(nino, personDetails)
            }
          }
        } yield result

      }
    }

  private def submitConfirmClosePostalAddress(
    nino: Nino,
    personDetails: PersonDetails
  )(implicit request: UserRequest[_]): Future[Result] = {

    val address        = getAddress(personDetails.correspondenceAddress)
    val closingAddress = address.copy(startDate = Some(LocalDate.now), endDate = Some(LocalDate.now))
    val etagValue      = personDetails.etag
    val postcode       = address.postcode.getOrElse("")

    def handleError(statusCode: Int): Future[Result] = statusCode match {
      case BAD_REQUEST => errorRenderer.futureError(BAD_REQUEST)
      case CONFLICT    =>
        citizenDetailsService.clearCachedPersonDetails(nino)
        errorRenderer.futureError(INTERNAL_SERVER_ERROR)
      case _           => errorRenderer.futureError(INTERNAL_SERVER_ERROR)
    }

    def handleSuccess: Future[Result] =
      for {
        _        <- auditConnector.sendEvent(
                      buildEvent(
                        "closedAddressSubmitted",
                        "closure_of_correspondence",
                        auditForClosingPostalAddress(closingAddress, etagValue, "correspondence")
                      )
                    )
        _        <- cachingHelper.clearCache()
        inserted <- editAddressLockRepository.insert(nino.withoutSuffix, PostalAddrType)
        _        <- addressMovedService.moved(postcode, postcode)
      } yield
        if (inserted) {
          Ok(
            updateAddressConfirmationView(
              PostalAddrType,
              closedPostalAddress = true,
              Some(getAddress(personDetails.address).fullAddress),
              None,
              displayP85Message = false
            )
          )
        } else {
          errorRenderer.error(INTERNAL_SERVER_ERROR)
        }

    citizenDetailsService
      .updateAddress(nino, etagValue, closingAddress)
      .foldF(error => handleError(error.statusCode), _ => handleSuccess)
  }

  private def getAddress(address: Option[Address]): Address =
    address match {
      case Some(address) => address
      case None          => throw new Exception("Address does not exist in the current context")
    }
}

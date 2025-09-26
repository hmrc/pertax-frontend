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

import cats.data.EitherT
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
import services.*
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.UpstreamErrorResponse
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
        EitherT
          .liftF(editAddressLockRepository.get(nino.withoutSuffix))
          .flatMap { addressChanges =>
            if (addressChanges.map(_.editedAddress).exists(_.isInstanceOf[EditCorrespondenceAddress])) {
              EitherT.rightT[Future, UpstreamErrorResponse](Redirect(routes.PersonalDetailsController.onPageLoad))
            } else {
              println("PPPPPPPPP Submitting close postal address")
              submitConfirmClosePostalAddress(nino, personDetails)
            }
          }
          .fold(_ => errorRenderer.error(INTERNAL_SERVER_ERROR), identity)
      }
    }

  private def submitConfirmClosePostalAddress(
    nino: Nino,
    personDetails: PersonDetails
  )(implicit request: UserRequest[_]): EitherT[Future, UpstreamErrorResponse, Result] = {

    val address        = getAddress(personDetails.correspondenceAddress)
    val closingAddress = address.copy(startDate = Some(LocalDate.now), endDate = Some(LocalDate.now))

    for {
      _ <- citizenDetailsService
             .updateAddress(nino, closingAddress, personDetails)
      _ <- EitherT.liftF(
             auditConnector.sendEvent(
               buildEvent(
                 "closedAddressSubmitted",
                 "closure_of_correspondence",
                 auditForClosingPostalAddress(closingAddress, personDetails.etag, "correspondence")
               )
             )
           )
      _ <- EitherT.liftF(editAddressLockRepository.insert(nino.withoutSuffix, PostalAddrType))
      _ <- EitherT.liftF(citizenDetailsService.clearCachedPersonDetails(nino))
      _ <- EitherT.liftF(
             cachingHelper
               .clearCache()
           ) // This clears ENTIRE session cache, no way to target individual keys
    } yield Ok(
      updateAddressConfirmationView(
        PostalAddrType,
        closedPostalAddress = true,
        Some(getAddress(personDetails.address).fullAddress),
        None,
        displayP85Message = false
      )
    )
  }

  private def getAddress(address: Option[Address]): Address =
    address match {
      case Some(address) => address
      case None          => throw new Exception("Address does not exist in the current context")
    }
}

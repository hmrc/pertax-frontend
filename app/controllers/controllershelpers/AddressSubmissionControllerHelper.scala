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

package controllers.controllershelpers

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.bindable.{AddrType, ResidentialAddrType}
import controllers.controllershelpers.AddressJourneyAuditingHelper.{addressWasHeavilyModifiedOrManualEntry, addressWasUnmodified, dataToAudit}
import error.ErrorRenderer
import models.dto.InternationalAddressChoiceDto.isUk
import models.dto.{AddressDto, DateDto, InternationalAddressChoiceDto}
import models.{Address, AddressJourneyData, PersonDetails}
import play.api.Logging
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.i18n.Messages
import play.api.mvc.Results.{BadRequest, Ok}
import play.api.mvc.Result
import repositories.EditAddressLockRepository
import services.{AddressMovedService, CitizenDetailsService}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.language.LanguageUtils
import util.AuditServiceTools.buildEvent
import views.html.personaldetails.{CannotUpdateAddressEarlyDateView, UpdateAddressConfirmationView}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class AddressSubmissionControllerHelper @Inject() (
  addressMovedService: AddressMovedService,
  editAddressLockRepository: EditAddressLockRepository,
  auditConnector: AuditConnector,
  errorRenderer: ErrorRenderer,
  updateAddressConfirmationView: UpdateAddressConfirmationView,
  citizenDetailsService: CitizenDetailsService,
  cannotUpdateAddressEarlyDateView: CannotUpdateAddressEarlyDateView,
  languageUtils: LanguageUtils
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends Logging {

  def isSubmittedAddressStartDateValid(submittedStartDateDto: Option[DateDto], addressType: AddrType): Boolean =
    submittedStartDateDto.nonEmpty || addressType != ResidentialAddrType

  def updateCitizenDetailsAddress(
    nino: Nino,
    addressType: AddrType,
    journeyData: AddressJourneyData,
    personDetails: PersonDetails,
    submittedAddress: AddressDto
  )(implicit hc: HeaderCarrier, request: UserRequest[_], messages: Messages): Future[Result] = {
    def isStartDateError(error: UpstreamErrorResponse): Boolean =
      error.statusCode == 400 && error.message.toLowerCase().contains("start date")

    val p85enabled: Boolean = !isUk(journeyData.submittedInternationalAddressChoiceDto)

    val startDateErrorResponse: Result =
      BadRequest(
        cannotUpdateAddressEarlyDateView(
          addressType,
          languageUtils.Dates.formatDate(
            journeyData.submittedStartDateDto
              .map(_.startDate)
              .getOrElse(LocalDate.now())
          ),
          p85enabled
        )
      )

    val originalAddressDto: Option[AddressDto] =
      journeyData.selectedAddressRecord.map(AddressDto.fromAddressRecord)
    val addressDtoWithFormattedPostCode        = submittedAddress
      .copy(postcode = submittedAddress.postcode.map(submittedAddress.formatMandatoryPostCode))

    val newAddress: Address =
      submittedAddress.toAddress(
        addressType.ifIs("Residential", "Correspondence"),
        journeyData.submittedStartDateDto.fold(LocalDate.now)(_.startDate)
      )

    citizenDetailsService
      .updateAddress(nino, newAddress, personDetails)
      .foldF(
        {
          case error if isStartDateError(error) =>
            Future.successful(startDateErrorResponse)

          case _ =>
            errorRenderer.futureError(INTERNAL_SERVER_ERROR)
        },
        _ =>
          for {
            _                           <- handleAddressChangeAuditing(
                                             originalAddressDto,
                                             addressDtoWithFormattedPostCode,
                                             personDetails.etag,
                                             addressType.ifIs("Residential", "Correspondence")
                                           )
            _                           <- editAddressLockRepository.insert(nino.withoutSuffix, addressType)
            _                           <- citizenDetailsService.clearCachedPersonDetails(nino)
            addressMovedCountryInsideUk <-
              addressMovedService
                .moved(
                  personDetails.address.flatMap(_.postcode).getOrElse(""),
                  newAddress.postcode.getOrElse(""),
                  p85enabled
                )

          } yield Ok(
            updateAddressConfirmationView(
              addressType,
              closedPostalAddress = false,
              None,
              addressMovedService.toMessageKey(addressMovedCountryInsideUk),
              displayP85Message = p85enabled
            )
          )
      )
  }

  def handleAddressChangeAuditing(
    originalAddressDto: Option[AddressDto],
    addressDto: AddressDto,
    etag: String,
    addressType: String
  )(implicit hc: HeaderCarrier, request: UserRequest[_]): Future[AuditResult] =
    if (addressWasUnmodified(originalAddressDto, addressDto)) {
      auditConnector.sendEvent(
        buildEvent(
          "postcodeAddressSubmitted",
          "change_of_address",
          dataToAudit(addressDto, etag, addressType, None, originalAddressDto.flatMap(_.propertyRefNo))
            .filter(!_._1.startsWith("originalLine")) - "originalPostcode"
        )
      )
    } else if (addressWasHeavilyModifiedOrManualEntry(originalAddressDto, addressDto)) {
      auditConnector.sendEvent(
        buildEvent(
          "manualAddressSubmitted",
          "change_of_address",
          dataToAudit(addressDto, etag, addressType, None, originalAddressDto.flatMap(_.propertyRefNo))
        )
      )
    } else {
      auditConnector.sendEvent(
        buildEvent(
          "postcodeAddressModifiedSubmitted",
          "change_of_address",
          dataToAudit(
            addressDto,
            etag,
            addressType,
            originalAddressDto,
            originalAddressDto.flatMap(_.propertyRefNo)
          )
        )
      )
    }
}

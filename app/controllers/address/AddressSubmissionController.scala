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
import cats.data.EitherT
import config.ConfigDecorator
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.bindable.{AddrType, ResidentialAddrType}
import controllers.controllershelpers.AddressJourneyAuditingHelper.{addressWasHeavilyModifiedOrManualEntry, addressWasUnmodified, dataToAudit}
import controllers.controllershelpers.AddressJourneyCachingHelper
import error.ErrorRenderer
import models.dto.InternationalAddressChoiceDto.isUk
import models.dto.{AddressDto, DateDto, InternationalAddressChoiceDto}
import models.{Address, AddressJourneyData, ETag, PersonDetails}
import play.api.Logging
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import repositories.EditAddressLockRepository
import services.{AddressMovedService, CitizenDetailsService}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.language.LanguageUtils
import util.AuditServiceTools.buildEvent
import views.html.InternalServerErrorView
import views.html.personaldetails.{CannotUpdateAddressEarlyDateView, ReviewChangesView, UpdateAddressConfirmationView}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class AddressSubmissionController @Inject() (
  addressMovedService: AddressMovedService,
  editAddressLockRepository: EditAddressLockRepository,
  authJourney: AuthJourney,
  cachingHelper: AddressJourneyCachingHelper,
  auditConnector: AuditConnector,
  cc: MessagesControllerComponents,
  errorRenderer: ErrorRenderer,
  updateAddressConfirmationView: UpdateAddressConfirmationView,
  reviewChangesView: ReviewChangesView,
  featureFlagService: FeatureFlagService,
  citizenDetailsService: CitizenDetailsService,
  internalServerErrorView: InternalServerErrorView,
  cannotUpdateAddressEarlyDateView: CannotUpdateAddressEarlyDateView,
  languageUtils: LanguageUtils
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

  def onPageLoad(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        cachingHelper.gettingCachedJourneyData(typ).map { journeyData =>
          (journeyData.submittedAddressDto, journeyData.submittedInternationalAddressChoiceDto) match {
            case (Some(address), Some(country)) =>
              val isUkAddress              = InternationalAddressChoiceDto.isUk(Some(country))
              val doYouLiveInTheUK: String = s"label.address_country.${country.toString}"

              if (isUkAddress) {
                val newPostcode: String = journeyData.submittedAddressDto.flatMap(_.postcode).getOrElse("")
                val oldPostcode: String = personDetails.address.flatMap(add => add.postcode).getOrElse("")

                val showAddressChangedDate: Boolean =
                  !newPostcode.replace(" ", "").equalsIgnoreCase(oldPostcode.replace(" ", ""))

                if (isSubmittedAddressStartDateValid(journeyData.submittedStartDateDto, typ)) {
                  Ok(
                    reviewChangesView(
                      typ,
                      address,
                      doYouLiveInTheUK,
                      isUkAddress,
                      journeyData.submittedStartDateDto,
                      showAddressChangedDate
                    )
                  )
                } else {
                  Redirect(routes.PersonalDetailsController.onPageLoad)
                }
              } else {
                if (isSubmittedAddressStartDateValid(journeyData.submittedStartDateDto, typ)) {
                  Ok(
                    reviewChangesView(
                      typ,
                      address,
                      doYouLiveInTheUK,
                      isUkAddress,
                      journeyData.submittedStartDateDto,
                      displayDateAddressChanged = true
                    )
                  )
                } else {
                  Redirect(routes.PersonalDetailsController.onPageLoad)
                }
              }
            case _                              => Redirect(routes.PersonalDetailsController.onPageLoad)
          }
        }
      }
    }

  private def isSubmittedAddressStartDateValid(submittedStartDateDto: Option[DateDto], addressType: AddrType): Boolean =
    submittedStartDateDto.nonEmpty || addressType != ResidentialAddrType

  private def updateCitizenDetailsAddress(
    nino: Nino,
    etag: ETag,
    address: Address,
    addressType: AddrType,
    journeyData: AddressJourneyData,
    personDetails: PersonDetails,
    submittedAddress: AddressDto
  )(implicit hc: HeaderCarrier, request: UserRequest[_]): Future[Result] = {
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
    val addressDtowithFormattedPostCode        = submittedAddress
      .copy(postcode = submittedAddress.postcode.map(submittedAddress.formatMandatoryPostCode))

    citizenDetailsService
      .updateAddress(nino, etag.etag, address)
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
                                             addressDtowithFormattedPostCode,
                                             etag,
                                             addressType.ifIs("Residential", "Correspondence")
                                           )
            _                           <- editAddressLockRepository.insert(nino.withoutSuffix, addressType)
            addressMovedCountryInsideUk <-
              addressMovedService
                .moved(
                  personDetails.address.flatMap(_.postcode).getOrElse(""),
                  address.postcode.getOrElse(""),
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

  def onSubmit(addressType: AddrType): Action[AnyContent] = authenticate.async { implicit request =>
    addressJourneyEnforcer { nino => personDetails =>
      (for {
        maybeEtag: Option[ETag]         <- citizenDetailsService.getEtag(nino.nino)
        journeyData: AddressJourneyData <- EitherT[Future, UpstreamErrorResponse, AddressJourneyData](
                                             cachingHelper.gettingCachedJourneyData(addressType).map(Right(_))
                                           )
      } yield {
        val maybeSubmittedAddress = journeyData.submittedAddressDto

        (
          isSubmittedAddressStartDateValid(journeyData.submittedStartDateDto, addressType),
          maybeSubmittedAddress,
          maybeEtag
        ) match {
          case (true, Some(submittedAddress), Some(etag)) =>
            val address =
              submittedAddress.toAddress(
                addressType.ifIs("Residential", "Correspondence"),
                journeyData.submittedStartDateDto.fold(LocalDate.now)(_.startDate)
              )

            updateCitizenDetailsAddress(
              nino,
              etag,
              address,
              addressType,
              journeyData,
              personDetails,
              submittedAddress
            )

          case _ => errorRenderer.futureError(INTERNAL_SERVER_ERROR)
        }
      }).foldF(
        error => errorRenderer.futureError(error.statusCode),
        identity
      )
    }
  }

  private def handleAddressChangeAuditing(
    originalAddressDto: Option[AddressDto],
    addressDto: AddressDto,
    version: ETag,
    addressType: String
  )(implicit hc: HeaderCarrier, request: UserRequest[_]): Future[AuditResult] =
    if (addressWasUnmodified(originalAddressDto, addressDto)) {
      auditConnector.sendEvent(
        buildEvent(
          "postcodeAddressSubmitted",
          "change_of_address",
          dataToAudit(addressDto, version.etag, addressType, None, originalAddressDto.flatMap(_.propertyRefNo))
            .filter(!_._1.startsWith("originalLine")) - "originalPostcode"
        )
      )
    } else if (addressWasHeavilyModifiedOrManualEntry(originalAddressDto, addressDto)) {
      auditConnector.sendEvent(
        buildEvent(
          "manualAddressSubmitted",
          "change_of_address",
          dataToAudit(addressDto, version.etag, addressType, None, originalAddressDto.flatMap(_.propertyRefNo))
        )
      )
    } else {
      auditConnector.sendEvent(
        buildEvent(
          "postcodeAddressModifiedSubmitted",
          "change_of_address",
          dataToAudit(
            addressDto,
            version.etag,
            addressType,
            originalAddressDto,
            originalAddressDto.flatMap(_.propertyRefNo)
          )
        )
      )
    }
}

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
import controllers.bindable.{AddrType, PostalAddrType, ResidentialAddrType}
import controllers.controllershelpers.AddressJourneyAuditingHelper.{addressWasHeavilyModifiedOrManualEntry, addressWasUnmodified, dataToAudit}
import controllers.controllershelpers.AddressJourneyCachingHelper
import error.ErrorRenderer
import models.dto.InternationalAddressChoiceDto.isUk
import models.dto.{AddressDto, InternationalAddressChoiceDto}
import models.{AddressChanged, AddressJourneyData, ETag}
import play.api.Logging
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import repositories.EditAddressLockRepository
import services.{AddressMovedService, CitizenDetailsService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.language.LanguageUtils
import util.AuditServiceTools.buildEvent
import views.html.InternalServerErrorView
import views.html.personaldetails.{CannotUpdateAddressEarlyDateView, ReviewChangesView, UpdateAddressConfirmationView}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class AddressSubmissionController @Inject() (
  val addressMovedService: AddressMovedService,
  val editAddressLockRepository: EditAddressLockRepository,
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
        cachingHelper.gettingCachedJourneyData(typ) { journeyData =>
          (journeyData.submittedAddressDto, journeyData.submittedInternationalAddressChoiceDto) match {
            case (Some(address), Some(country)) =>
              val isUkAddress              = InternationalAddressChoiceDto.isUk(Some(country))
              val doYouLiveInTheUK: String = s"label.address_country.${country.toString}"

              if (isUkAddress) {
                val newPostcode: String = journeyData.submittedAddressDto.flatMap(_.postcode).getOrElse("")
                val oldPostcode: String = personDetails.address.flatMap(add => add.postcode).getOrElse("")

                val showAddressChangedDate: Boolean =
                  !newPostcode.replace(" ", "").equalsIgnoreCase(oldPostcode.replace(" ", ""))
                ensuringSubmissionRequirements(typ, journeyData) {
                  Future.successful(
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
                  )
                }
              } else {
                ensuringSubmissionRequirements(typ, journeyData) {
                  Future.successful(
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
                  )
                }
              }
            case _                              => Future.successful(Redirect(routes.PersonalDetailsController.onPageLoad))
          }
        }
      }
    }

  def onSubmit(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      val addressType = mapAddressType(typ)

      addressJourneyEnforcer { nino => personDetails =>
        val etagValue = personDetails.etag

        cachingHelper.gettingCachedJourneyData(typ) { journeyData =>
          val p85Enabled = !isUk(journeyData.submittedInternationalAddressChoiceDto)

          ensuringSubmissionRequirements(typ, journeyData) {
            journeyData.submittedAddressDto.fold {
              Future.successful(Redirect(routes.PersonalDetailsController.onPageLoad))
            } { addressDto =>
              val startDate = journeyData.submittedStartDateDto.map(_.startDate).getOrElse(LocalDate.now())
              val address   = addressDto.toAddress(addressType, startDate)

              val originalPostcode = personDetails.address.flatMap(_.postcode).getOrElse("")
              val newPostcode      = address.postcode.getOrElse("")

              addressMovedService
                .moved(originalPostcode, newPostcode)
                .flatMap { addressChanged =>
                  for {
                    _      <- editAddressLockRepository.insert(nino.withoutSuffix, typ)
                    result <- updateAddressWithHandling(
                                nino,
                                etagValue,
                                address,
                                startDate,
                                typ,
                                journeyData,
                                addressDto,
                                addressChanged,
                                p85Enabled,
                                addressType
                              )
                  } yield result
                }
                .recoverWith { case _: Exception =>
                  errorRenderer.futureError(INTERNAL_SERVER_ERROR)
                }
            }
          }
        }
      }
    }

  private def updateAddressWithHandling(
    nino: uk.gov.hmrc.domain.Nino,
    etagValue: String,
    address: models.Address,
    startDate: LocalDate,
    typ: AddrType,
    journeyData: AddressJourneyData,
    addressDto: AddressDto,
    addressChanged: AddressChanged,
    p85Enabled: Boolean,
    addressType: String
  )(implicit request: UserRequest[_]): Future[Result] = {
    val etag = ETag(etagValue)

    citizenDetailsService
      .updateAddress(nino, etagValue, address)
      .foldF(
        {
          case error if error.statusCode == BAD_REQUEST && error.message.toLowerCase.contains("start date") =>
            Future.successful(
              BadRequest(
                cannotUpdateAddressEarlyDateView(
                  typ,
                  languageUtils.Dates.formatDate(startDate),
                  p85Enabled
                )
              )
            )
          case error if error.statusCode == CONFLICT                                                        =>
            citizenDetailsService.clearCachedPersonDetails(nino)
            errorRenderer.futureError(INTERNAL_SERVER_ERROR)

          case _ =>
            errorRenderer.futureError(INTERNAL_SERVER_ERROR)
        },
        _ =>
          Future.successful(
            buildSuccessResponse(
              typ,
              journeyData,
              addressDto,
              etag,
              addressType,
              addressChanged,
              p85Enabled
            )
          )
      )
  }

  private def buildSuccessResponse(
    typ: AddrType,
    journeyData: AddressJourneyData,
    addressDto: AddressDto,
    etag: ETag,
    addressType: String,
    addressChanged: AddressChanged,
    p85Enabled: Boolean
  )(implicit request: UserRequest[_]): Result = {
    val originalAddressDto = journeyData.selectedAddressRecord.map(AddressDto.fromAddressRecord)
    val formattedDto       = addressDto.copy(postcode = addressDto.postcode.map(addressDto.formatMandatoryPostCode))

    handleAddressChangeAuditing(originalAddressDto, formattedDto, etag, addressType)
    cachingHelper.clearCache()

    Ok(
      updateAddressConfirmationView(
        typ,
        closedPostalAddress = false,
        None,
        addressMovedService.toMessageKey(addressChanged),
        displayP85Message = p85Enabled
      )
    )
  }

  private def mapAddressType(typ: AddrType) = typ match {
    case PostalAddrType => "Correspondence"
    case _              => "Residential"
  }

  private def ensuringSubmissionRequirements(typ: AddrType, journeyData: AddressJourneyData)(
    block: => Future[Result]
  ): Future[Result] =
    if (journeyData.submittedStartDateDto.isEmpty && typ == ResidentialAddrType) {
      Future.successful(Redirect(routes.PersonalDetailsController.onPageLoad))
    } else {
      block
    }

  private def handleAddressChangeAuditing(
    originalAddressDto: Option[AddressDto],
    addressDto: AddressDto,
    version: ETag,
    addressType: String
  )(implicit hc: HeaderCarrier, request: UserRequest[_]) =
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

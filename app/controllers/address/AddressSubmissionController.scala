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
import connectors.CitizenDetailsConnector
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.bindable.{AddrType, PostalAddrType, ResidentialAddrType}
import controllers.controllershelpers.AddressJourneyAuditingHelper.{addressWasHeavilyModifiedOrManualEntry, addressWasUnmodified, dataToAudit}
import controllers.controllershelpers.AddressJourneyCachingHelper
import error.{ErrorRenderer, GenericErrors}
import models.dto.AddressDto
import models.{AddressJourneyData, ETag}
import play.api.Logging
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import repositories.EditAddressLockRepository
import services.AddressMovedService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import util.AuditServiceTools.buildEvent
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.{ReviewChangesView, UpdateAddressConfirmationView}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class AddressSubmissionController @Inject() (
  val citizenDetailsConnector: CitizenDetailsConnector,
  val addressMovedService: AddressMovedService,
  val editAddressLockRepository: EditAddressLockRepository,
  authJourney: AuthJourney,
  cachingHelper: AddressJourneyCachingHelper,
  auditConnector: AuditConnector,
  cc: MessagesControllerComponents,
  errorRenderer: ErrorRenderer,
  updateAddressConfirmationView: UpdateAddressConfirmationView,
  reviewChangesView: ReviewChangesView,
  displayAddressInterstitialView: DisplayAddressInterstitialView,
  genericErrors: GenericErrors
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends AddressController(authJourney, cc, displayAddressInterstitialView) with Logging {

  def onPageLoad(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        cachingHelper.gettingCachedJourneyData(typ) { journeyData =>
          val isUkAddress: Boolean = journeyData.submittedInternationalAddressChoiceDto.forall(_.value)
          val doYouLiveInTheUK: String =
            if (journeyData.submittedInternationalAddressChoiceDto.forall(_.value)) {
              "label.yes"
            } else {
              "label.no"
            }

          if (isUkAddress) {
            val newPostcode: String = journeyData.submittedAddressDto.flatMap(_.postcode).getOrElse("")
            val oldPostcode: String = personDetails.address.flatMap(add => add.postcode).getOrElse("")

            val showAddressChangedDate: Boolean =
              !newPostcode.replace(" ", "").equalsIgnoreCase(oldPostcode.replace(" ", ""))
            ensuringSubmissionRequirements(typ, journeyData) {
              journeyData.submittedAddressDto.fold(
                Future.successful(Redirect(routes.PersonalDetailsController.onPageLoad))
              ) { addressDto =>
                Future.successful(
                  Ok(
                    reviewChangesView(
                      typ,
                      addressDto,
                      doYouLiveInTheUK,
                      isUkAddress,
                      journeyData.submittedStartDateDto,
                      showAddressChangedDate
                    )
                  )
                )
              }
            }
          } else {
            ensuringSubmissionRequirements(typ, journeyData) {
              journeyData.submittedAddressDto.fold(
                Future.successful(Redirect(routes.PersonalDetailsController.onPageLoad))
              ) { addressDto =>
                Future.successful(
                  Ok(
                    reviewChangesView(
                      typ,
                      addressDto,
                      doYouLiveInTheUK,
                      isUkAddress,
                      journeyData.submittedStartDateDto,
                      displayDateAddressChanged = true
                    )
                  )
                )
              }
            }
          }
        }
      }
    }

  def onSubmit(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      val addressType = mapAddressType(typ)

      addressJourneyEnforcer { nino => personDetails =>
        citizenDetailsConnector.getEtag(nino.nino) flatMap {
          case None =>
            logger.error("Failed to retrieve Etag from citizen-details")
            errorRenderer.futureError(INTERNAL_SERVER_ERROR)

          case Some(version) =>
            cachingHelper.gettingCachedJourneyData(typ) { journeyData =>
              ensuringSubmissionRequirements(typ, journeyData) {

                journeyData.submittedAddressDto.fold(
                  Future.successful(Redirect(routes.PersonalDetailsController.onPageLoad))
                ) { addressDto =>
                  val address =
                    addressDto
                      .toAddress(addressType, journeyData.submittedStartDateDto.fold(LocalDate.now)(_.startDate))

                  val originalPostcode = personDetails.address.flatMap(_.postcode).getOrElse("")

                  addressMovedService.moved(originalPostcode, address.postcode.getOrElse("")).flatMap {
                    addressChanged =>
                      def successResponseBlock(): Result = {
                        val originalAddressDto: Option[AddressDto] =
                          journeyData.selectedAddressRecord.map(AddressDto.fromAddressRecord)

                        val addressDtowithFormattedPostCode =
                          addressDto.copy(postcode = addressDto.postcode.map(addressDto.formatMandatoryPostCode))
                        handleAddressChangeAuditing(
                          originalAddressDto,
                          addressDtowithFormattedPostCode,
                          version,
                          addressType
                        )
                        cachingHelper.clearCache()

                        Ok(
                          updateAddressConfirmationView(
                            typ,
                            closedPostalAddress = false,
                            None,
                            addressMovedService.toMessageKey(addressChanged)
                          )
                        )
                      }

                      for {
                        _      <- editAddressLockRepository.insert(nino.withoutSuffix, typ)
                        result <- citizenDetailsConnector.updateAddress(nino, version.etag, address)
                      } yield result.response(genericErrors, successResponseBlock)
                  }
                }
              }
            }
        }
      }
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
    if (addressWasUnmodified(originalAddressDto, addressDto))
      auditConnector.sendEvent(
        buildEvent(
          "postcodeAddressSubmitted",
          "change_of_address",
          dataToAudit(addressDto, version.etag, addressType, None, originalAddressDto.flatMap(_.propertyRefNo))
            .filter(!_._1.startsWith("originalLine")) - "originalPostcode"
        )
      )
    else if (addressWasHeavilyModifiedOrManualEntry(originalAddressDto, addressDto))
      auditConnector.sendEvent(
        buildEvent(
          "manualAddressSubmitted",
          "change_of_address",
          dataToAudit(addressDto, version.etag, addressType, None, originalAddressDto.flatMap(_.propertyRefNo))
        )
      )
    else
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

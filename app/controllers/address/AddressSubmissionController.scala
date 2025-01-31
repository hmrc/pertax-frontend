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
import models.{AddressJourneyData, ETag}
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
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.{CannotUpdateAddressEarlyDateView, ReviewChangesView, UpdateAddressConfirmationView}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class AddressSubmissionController @Inject() (
  val citizenDetailsService: CitizenDetailsService,
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
  featureFlagService: FeatureFlagService,
  internalServerErrorView: InternalServerErrorView,
  cannotUpdateAddressEarlyDateView: CannotUpdateAddressEarlyDateView,
  languageUtils: LanguageUtils
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends AddressController(
      authJourney,
      cc,
      displayAddressInterstitialView,
      featureFlagService,
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
        citizenDetailsService
          .getEtag(nino.nino)
          .foldF(
            _ => errorRenderer.futureError(INTERNAL_SERVER_ERROR),
            version =>
              version
                .map { version =>
                  cachingHelper.gettingCachedJourneyData(typ) { journeyData =>
                    val p85Enabled = !isUk(journeyData.submittedInternationalAddressChoiceDto)
                    ensuringSubmissionRequirements(typ, journeyData) {

                      journeyData.submittedAddressDto.fold(
                        Future.successful(Redirect(routes.PersonalDetailsController.onPageLoad))
                      ) { addressDto =>
                        val address =
                          addressDto
                            .toAddress(addressType, journeyData.submittedStartDateDto.fold(LocalDate.now)(_.startDate))

                        val originalPostcode = personDetails.address.flatMap(_.postcode).getOrElse("")

                        addressMovedService
                          .moved(originalPostcode, address.postcode.getOrElse(""))
                          .flatMap { addressChanged =>
                            def successResponseBlock(): Result = {
                              val originalAddressDto: Option[AddressDto] =
                                journeyData.selectedAddressRecord.map(AddressDto.fromAddressRecord)

                              val addressDtowithFormattedPostCode =
                                addressDto
                                  .copy(postcode = addressDto.postcode.map(addressDto.formatMandatoryPostCode))
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
                                  addressMovedService.toMessageKey(addressChanged),
                                  displayP85Message = p85Enabled
                                )
                              )
                            }

                            for {
                              _      <- editAddressLockRepository.insert(nino.withoutSuffix, typ)
                              result <- citizenDetailsService
                                          .updateAddress(nino, version.etag, address)
                                          .foldF(
                                            {
                                              case error
                                                  if error.statusCode == 400 && error.message
                                                    .contains("Start Date cannot be the same") =>
                                                Future.successful(
                                                  BadRequest(
                                                    cannotUpdateAddressEarlyDateView(
                                                      typ,
                                                      languageUtils.Dates.formatDate(
                                                        journeyData.submittedStartDateDto
                                                          .map(_.startDate)
                                                          .getOrElse(LocalDate.now())
                                                      ),
                                                      p85Enabled
                                                    )
                                                  )
                                                )

                                              case _ =>
                                                errorRenderer.futureError(INTERNAL_SERVER_ERROR)
                                            },
                                            _ => Future.successful(successResponseBlock())
                                          )
                            } yield result
                          }
                      }
                    }
                  }
                }
                .getOrElse(errorRenderer.futureError(INTERNAL_SERVER_ERROR))
          )
      }
    }

  private def mapAddressType(typ: AddrType) = typ match {
    case PostalAddrType => "Correspondence"
    case _              => "Residential"
  }

  private def ensuringSubmissionRequirements(typ: AddrType, journeyData: AddressJourneyData)(
    block: => Future[Result]
  ): Future[Result] =
    if (journeyData.submittedStartDateDto.isEmpty && typ == ResidentialAddrType)
      Future.successful(Redirect(routes.PersonalDetailsController.onPageLoad))
    else block

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

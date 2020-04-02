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
import controllers.bindable.{AddrType, PostalAddrType, PrimaryAddrType, SoleAddrType}
import controllers.controllershelpers.AddressJourneyAuditingHelper.{addressWasHeavilyModifiedOrManualEntry, addressWasUnmodified, dataToAudit}
import models.{AddressJourneyData, PersonDetails}
import models.dto.AddressDto
import org.joda.time.LocalDate
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import repositories.EditAddressLockRepository
import services.{AddressMovedService, CitizenDetailsService, LocalSessionCache}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.renderer.TemplateRenderer
import util.AuditServiceTools.buildEvent
import util.LocalPartialRetriever

import scala.concurrent.{ExecutionContext, Future}

class AddressSubmissionController @Inject()(
  val addressMovedService: AddressMovedService,
  val citizenDetailsService: CitizenDetailsService,
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

  def onPageLoad(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        gettingCachedJourneyData(typ) { journeyData =>
          val isUkAddress: Boolean = journeyData.submittedInternationalAddressChoiceDto.forall(_.value)
          val doYouLiveInTheUK: String =
            if (journeyData.submittedInternationalAddressChoiceDto.forall(_.value)) {
              "label.yes"
            } else {
              "label.no"
            }

          if (isUkAddress) {
            val newPostcode: String = journeyData.submittedAddressDto.map(_.postcode).fold("")(_.getOrElse(""))
            val oldPostcode: String = personDetails.address.flatMap(add => add.postcode).fold("")(_.toString)

            val showAddressChangedDate: Boolean =
              !newPostcode.replace(" ", "").equalsIgnoreCase(oldPostcode.replace(" ", ""))
            ensuringSubmissionRequirements(typ, journeyData) {
              journeyData.submittedAddressDto.fold(redirectToPersonalDetails) { addressDto =>
                Future.successful(
                  Ok(
                    views.html.personaldetails.reviewChanges(
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
              journeyData.submittedAddressDto.fold(redirectToPersonalDetails) { addressDto =>
                Future.successful(
                  Ok(
                    views.html.personaldetails.reviewChanges(
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
        gettingCachedJourneyData(typ) { journeyData =>
          ensuringSubmissionRequirements(typ, journeyData) {

            journeyData.submittedAddressDto.fold(redirectToPersonalDetails) { addressDto =>
              val address =
                addressDto.toAddress(addressType, journeyData.submittedStartDateDto.fold(LocalDate.now)(_.startDate))

              val originalPostcode = personDetails.address.flatMap(_.postcode).getOrElse("")

              addressMovedService.moved(originalPostcode, address.postcode.getOrElse("")).flatMap { addressChanged =>
                def successResponseBlock(): Result = {
                  val originalAddressDto: Option[AddressDto] =
                    journeyData.selectedAddressRecord.map(AddressDto.fromAddressRecord)

                  val addressDtowithFormattedPostCode =
                    addressDto.copy(postcode = addressDto.postcode.map(addressDto.formatMandatoryPostCode))
                  handleAddressChangeAuditing(
                    originalAddressDto,
                    addressDtowithFormattedPostCode,
                    personDetails,
                    addressType)
                  clearCache()

                  Ok(
                    views.html.personaldetails
                      .updateAddressConfirmation(
                        typ,
                        closedPostalAddress = false,
                        None,
                        addressMovedService.toMessageKey(addressChanged)
                      )
                  )
                }

                for {
                  _      <- editAddressLockRepository.insert(nino.withoutSuffix, typ)
                  result <- citizenDetailsService.updateAddress(nino, personDetails.etag, address)
                } yield {
                  result.response(successResponseBlock)
                }
              }
            }
          }
        }
      }
    }

  private def handleAddressChangeAuditing(
    originalAddressDto: Option[AddressDto],
    addressDto: AddressDto,
    personDetails: PersonDetails,
    addressType: String)(implicit hc: HeaderCarrier, request: UserRequest[_]) =
    if (addressWasUnmodified(originalAddressDto, addressDto))
      auditConnector.sendEvent(
        buildEvent(
          "postcodeAddressSubmitted",
          "change_of_address",
          dataToAudit(addressDto, personDetails.etag, addressType, None, originalAddressDto.flatMap(_.propertyRefNo))
            .filter(!_._1.startsWith("originalLine")) - "originalPostcode"
        ))
    else if (addressWasHeavilyModifiedOrManualEntry(originalAddressDto, addressDto))
      auditConnector.sendEvent(
        buildEvent(
          "manualAddressSubmitted",
          "change_of_address",
          dataToAudit(addressDto, personDetails.etag, addressType, None, originalAddressDto.flatMap(_.propertyRefNo))
        )
      )
    else
      auditConnector.sendEvent(
        buildEvent(
          "postcodeAddressModifiedSubmitted",
          "change_of_address",
          dataToAudit(
            addressDto,
            personDetails.etag,
            addressType,
            originalAddressDto,
            originalAddressDto.flatMap(_.propertyRefNo)
          )
        )
      )

  private def ensuringSubmissionRequirements(typ: AddrType, journeyData: AddressJourneyData)(
    block: => Future[Result]): Future[Result] =
    if (journeyData.submittedStartDateDto.isEmpty && (typ == PrimaryAddrType | typ == SoleAddrType)) {
      redirectToPersonalDetails
    } else {
      block
    }

  private def mapAddressType(typ: AddrType): String = typ match {
    case PostalAddrType => "Correspondence"
    case _              => "Residential"
  }

  private def redirectToPersonalDetails: Future[Result] =
    Future.successful(Redirect(controllers.routes.AddressController.personalDetails()))
}

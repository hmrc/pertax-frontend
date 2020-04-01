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

package controllers

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.bindable._
import controllers.controllershelpers.AddressJourneyAuditingHelper._
import controllers.controllershelpers.{AddressJourneyCachingHelper, CountryHelper, PersonalDetailsCardGenerator}
import com.google.inject.Inject
import models._
import models.addresslookup.RecordSet
import models.dto._
import org.joda.time.LocalDate
import play.api.Logger
import play.api.data.{Form, FormError}
import play.api.mvc._
import play.twirl.api.Html
import repositories.EditAddressLockRepository
import services._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.renderer.{ActiveTabYourAccount, TemplateRenderer}
import util.AuditServiceTools._
import util.PertaxSessionKeys.{filter, postcode}
import util.{LanguageHelper, LocalPartialRetriever}

import scala.concurrent.{ExecutionContext, Future}

class AddressController @Inject()(
  val citizenDetailsService: CitizenDetailsService,
  val addressLookupService: AddressLookupService,
  val addressMovedService: AddressMovedService,
  val personalDetailsCardGenerator: PersonalDetailsCardGenerator,
  val countryHelper: CountryHelper,
  val editAddressLockRepository: EditAddressLockRepository,
  authJourney: AuthJourney,
  val sessionCache: LocalSessionCache,
  withActiveTabAction: WithActiveTabAction,
  auditConnector: AuditConnector,
  cc: MessagesControllerComponents)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends PertaxBaseController(cc) with AddressJourneyCachingHelper {

  def currentAddressType(personDetails: PersonDetails): String =
    personDetails.address.flatMap(_.`type`).getOrElse("Residential")

  def postcodeFromRequest(implicit request: UserRequest[AnyContent]): String =
    request.body.asFormUrlEncoded.flatMap(_.get(postcode).flatMap(_.headOption)).getOrElse("")

  def filterFromRequest(implicit request: UserRequest[AnyContent]): Option[String] =
    request.body.asFormUrlEncoded.flatMap(_.get(filter).flatMap(_.headOption))

  def getAddress(address: Option[Address]): Address =
    address match {
      case Some(address) => address
      case None          => throw new Exception("Address does not exist in the current context")
    }

  def addressBreadcrumb: Breadcrumb =
    "label.personal_details" -> routes.AddressController.personalDetails().url ::
      baseBreadcrumb

  def addressJourneyEnforcer(block: Nino => PersonDetails => Future[Result])(
    implicit request: UserRequest[_]): Future[Result] =
    (for {
      payeAccount   <- request.nino
      personDetails <- request.personDetails
    } yield {
      block(payeAccount)(personDetails)
    }).getOrElse {
      Future.successful {
        val continueUrl = configDecorator.pertaxFrontendHost + controllers.routes.AddressController
          .personalDetails()
          .url
        Ok(views.html.interstitial.displayAddressInterstitial(continueUrl))
      }
    }

  private val authenticate
    : ActionBuilder[UserRequest, AnyContent] = authJourney.authWithPersonalDetails andThen withActiveTabAction
    .addActiveTab(ActiveTabYourAccount)

  def personalDetails: Action[AnyContent] = authenticate.async { implicit request =>
    import models.dto.AddressPageVisitedDto

    for {
      addressModel <- request.nino
                       .map { nino =>
                         editAddressLockRepository.get(nino.withoutSuffix)
                       }
                       .getOrElse(Future.successful(List[AddressJourneyTTLModel]()))

      personalDetailsCards: Seq[Html] = personalDetailsCardGenerator.getPersonalDetailsCards(addressModel)
      personDetails: Option[PersonDetails] = request.personDetails

      _ <- personDetails
            .map { details =>
              auditConnector.sendEvent(buildPersonDetailsEvent("personalDetailsPageLinkClicked", details))
            }
            .getOrElse(Future.successful(Unit))
      _ <- addToCache(AddressPageVisitedDtoId, AddressPageVisitedDto(true))

    } yield Ok(views.html.personaldetails.personalDetails(personalDetailsCards))
  }

  def cannotUseThisService(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        gettingCachedAddressPageVisitedDto { addressPageVisitedDto =>
          enforceDisplayAddressPageVisited(addressPageVisitedDto) {
            Future.successful(Ok(views.html.personaldetails.cannotUseService(typ)))
          }
        }
      }
    }

  def ensuringSubmissionRequirments(typ: AddrType, journeyData: AddressJourneyData)(
    block: => Future[Result]): Future[Result] =
    if (journeyData.submittedStartDateDto.isEmpty && (typ == PrimaryAddrType | typ == SoleAddrType)) {
      Future.successful(Redirect(routes.AddressController.personalDetails()))
    } else {
      block
    }

  def closePostalAddressChoice: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        val address = getAddress(personDetails.address).fullAddress
        Future.successful(
          Ok(views.html.personaldetails.closeCorrespondenceAdressChoice(address, ClosePostalAddressChoiceDto.form)))
      }
    }

  def processClosePostalAddressChoice: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personalDetails =>
        ClosePostalAddressChoiceDto.form.bindFromRequest.fold(
          formWithErrors => {
            Future.successful(
              BadRequest(
                views.html.personaldetails
                  .closeCorrespondenceAdressChoice(getAddress(personalDetails.address).fullAddress, formWithErrors))
            )
          },
          closePostalAddressChoiceDto => {
            if (closePostalAddressChoiceDto.value) {
              Future.successful(Redirect(routes.AddressController.confirmClosePostalAddress()))
            } else {
              Future.successful(Redirect(routes.AddressController.personalDetails()))
            }
          }
        )
      }
    }

  def confirmClosePostalAddress: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        val address = getAddress(personDetails.address).fullAddress
        Future.successful(Ok(views.html.personaldetails.confirmCloseCorrespondenceAddress(address)))
      }
    }

  private def submitConfirmClosePostalAddress(nino: Nino, personDetails: PersonDetails)(
    implicit request: UserRequest[_]): Future[Result] = {
    def internalServerError =
      InternalServerError(
        views.html.error(
          "global.error.InternalServerError500.title",
          Some("global.error.InternalServerError500.title"),
          List("global.error.InternalServerError500.message")
        ))

    val address = getAddress(personDetails.correspondenceAddress)
    val closingAddress = address.copy(endDate = Some(LocalDate.now), startDate = Some(LocalDate.now))

    for {
      response <- citizenDetailsService.updateAddress(nino, personDetails.etag, closingAddress)
      action <- response match {
                 case UpdateAddressBadRequestResponse =>
                   Future.successful(
                     BadRequest(
                       views.html.error(
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
                             auditForClosingPostalAddress(closingAddress, personDetails.etag, "correspondence")))
                     _        <- clearCache() //This clears ENTIRE session cache, no way to target individual keys
                     inserted <- editAddressLockRepository.insert(nino.withoutSuffix, PostalAddrType)
                     _        <- addressMovedService.moved(address.postcode.getOrElse(""), address.postcode.getOrElse(""))
                   } yield
                     if (inserted) {
                       Ok(
                         views.html.personaldetails.updateAddressConfirmation(
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

  def submitConfirmClosePostalAddress: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { nino => personDetails =>
        for {
          addressChanges <- editAddressLockRepository.get(nino.withoutSuffix)
          result <- if (addressChanges.nonEmpty) {
                     Future.successful(Redirect(routes.AddressController.personalDetails()))
                   } else submitConfirmClosePostalAddress(nino, personDetails)

        } yield result
      }
    }

  def reviewChanges(typ: AddrType): Action[AnyContent] =
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
            ensuringSubmissionRequirments(typ, journeyData) {
              journeyData.submittedAddressDto.fold(
                Future.successful(Redirect(routes.AddressController.personalDetails()))) { addressDto =>
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
            ensuringSubmissionRequirments(typ, journeyData) {
              journeyData.submittedAddressDto.fold(
                Future.successful(Redirect(routes.AddressController.personalDetails()))) { addressDto =>
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

  private def mapAddressType(typ: AddrType) = typ match {
    case PostalAddrType => "Correspondence"
    case _              => "Residential"
  }

  def submitChanges(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      val addressType = mapAddressType(typ)

      addressJourneyEnforcer { nino => personDetails =>
        gettingCachedJourneyData(typ) { journeyData =>
          ensuringSubmissionRequirments(typ, journeyData) {

            journeyData.submittedAddressDto.fold(
              Future.successful(Redirect(routes.AddressController.personalDetails()))) { addressDto =>
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

  def showAddressAlreadyUpdated(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        Future.successful(Ok(views.html.personaldetails.addressAlreadyUpdated()))
      }
    }
}

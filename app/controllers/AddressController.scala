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

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.bindable._
import controllers.controllershelpers.AddressJourneyAuditingHelper._
import controllers.controllershelpers.{AddressJourneyCachingHelper, CountryHelper, PersonalDetailsCardGenerator}
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
import views.html.error
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails._

import scala.concurrent.{ExecutionContext, Future}

class AddressController @Inject()(
  val citizenDetailsService: CitizenDetailsService,
  val addressLookupService: AddressLookupService,
  val addressMovedService: AddressMovedService,
  val personalDetailsCardGenerator: PersonalDetailsCardGenerator,
  val countryHelper: CountryHelper,
  val editAddressLockRepository: EditAddressLockRepository,
  ninoDisplayService: NinoDisplayService,
  authJourney: AuthJourney,
  cachingHelper: AddressJourneyCachingHelper,
  withActiveTabAction: WithActiveTabAction,
  auditConnector: AuditConnector,
  cc: MessagesControllerComponents,
  displayAddressInterstitialView: DisplayAddressInterstitialView,
  personalDetailsView: PersonalDetailsView,
  taxCreditsChoiceView: TaxCreditsChoiceView,
  residencyChoiceView: ResidencyChoiceView,
  internationalAddressChoiceView: InternationalAddressChoiceView,
  cannotUseServiceView: CannotUseServiceView,
  postcodeLookupView: PostcodeLookupView,
  addressSelectorView: AddressSelectorView,
  updateAddressView: UpdateAddressView,
  updateInternationalAddressView: UpdateInternationalAddressView,
  enterStartDateView: EnterStartDateView,
  cannotUpdateAddressView: CannotUpdateAddressView,
  closeCorrespondenceAddressChoiceView: CloseCorrespondenceAddressChoiceView,
  confirmCloseCorrespondenceAddressView: ConfirmCloseCorrespondenceAddressView,
  updateAddressConfirmationView: UpdateAddressConfirmationView,
  reviewChangesView: ReviewChangesView,
  addressAlreadyUpdatedView: AddressAlreadyUpdatedView
)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends PertaxBaseController(cc) {

  def dateDtoForm: Form[DateDto] = DateDto.form(configDecorator.currentLocalDate)

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
        Ok(displayAddressInterstitialView(continueUrl))
      }
    }

  def lookingUpAddress(
    typ: AddrType,
    postcode: String,
    lookupServiceDown: Boolean,
    filter: Option[String] = None,
    forceLookup: Boolean = false)(f: PartialFunction[AddressLookupResponse, Future[Result]])(
    implicit request: UserRequest[_]): Future[Result] =
    if (!forceLookup && lookupServiceDown) {
      Future.successful(Redirect(routes.AddressController.showUpdateAddressForm(typ)))
    } else {
      val handleError: PartialFunction[AddressLookupResponse, Future[Result]] = {
        case AddressLookupErrorResponse(_) | AddressLookupUnexpectedResponse(_) =>
          cachingHelper.cacheAddressLookupServiceDown() map { _ =>
            Redirect(routes.AddressController.showUpdateAddressForm(typ))
          }
      }
      addressLookupService.lookup(postcode, filter).flatMap(handleError orElse f)
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
      ninoToDisplay <- ninoDisplayService.getNino
      personalDetailsCards: Seq[Html] = personalDetailsCardGenerator
        .getPersonalDetailsCards(addressModel, ninoToDisplay)
      personDetails: Option[PersonDetails] = request.personDetails

      _ <- personDetails
            .map { details =>
              auditConnector.sendEvent(buildPersonDetailsEvent("personalDetailsPageLinkClicked", details))
            }
            .getOrElse(Future.successful(Unit))
      _ <- cachingHelper.addToCache(AddressPageVisitedDtoId, AddressPageVisitedDto(true))

    } yield Ok(personalDetailsView(personalDetailsCards))
  }

  def taxCreditsChoice: Action[AnyContent] = authenticate.async { implicit request =>
    addressJourneyEnforcer { _ => _ =>
      cachingHelper.gettingCachedAddressPageVisitedDto { addressPageVisitedDto =>
        cachingHelper.enforceDisplayAddressPageVisited(addressPageVisitedDto) {
          Future.successful(
            Ok(taxCreditsChoiceView(TaxCreditsChoiceDto.form, configDecorator.tcsChangeAddressUrl))
          )
        }
      }
    }
  }

  def processTaxCreditsChoice: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        TaxCreditsChoiceDto.form.bindFromRequest.fold(
          formWithErrors => {
            Future.successful(BadRequest(taxCreditsChoiceView(formWithErrors, configDecorator.tcsChangeAddressUrl)))
          },
          taxCreditsChoiceDto => {
            cachingHelper.addToCache(SubmittedTaxCreditsChoiceId, taxCreditsChoiceDto) map { _ =>
              if (taxCreditsChoiceDto.value) {
                Redirect(configDecorator.tcsChangeAddressUrl)
              } else {
                Redirect(routes.AddressController.residencyChoice())
              }
            }
          }
        )

      }
    }

  def residencyChoice: Action[AnyContent] = authenticate.async { implicit request =>
    addressJourneyEnforcer { _ => _ =>
      cachingHelper.gettingCachedTaxCreditsChoiceDto {
        case Some(TaxCreditsChoiceDto(false)) =>
          Ok(residencyChoiceView(ResidencyChoiceDto.form))
        case _ =>
          if (configDecorator.taxCreditsEnabled) {
            Redirect(routes.AddressController.personalDetails())
          } else {
            Ok(residencyChoiceView(ResidencyChoiceDto.form))
          }
      }
    }
  }

  def processResidencyChoice: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        ResidencyChoiceDto.form.bindFromRequest.fold(
          formWithErrors => {
            Future.successful(BadRequest(residencyChoiceView(formWithErrors)))
          },
          residencyChoiceDto => {
            cachingHelper
              .addToCache(SubmittedResidencyChoiceDtoId(residencyChoiceDto.residencyChoice), residencyChoiceDto) map {
              _ =>
                Redirect(routes.AddressController.internationalAddressChoice(residencyChoiceDto.residencyChoice))
            }
          }
        )

      }
    }

  def internationalAddressChoice(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        cachingHelper.gettingCachedAddressPageVisitedDto { addressPageVisitedDto =>
          cachingHelper.enforceDisplayAddressPageVisited(addressPageVisitedDto) {
            Future.successful(
              Ok(internationalAddressChoiceView(InternationalAddressChoiceDto.form, typ))
            )
          }
        }
      }
    }

  def processInternationalAddressChoice(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        InternationalAddressChoiceDto.form.bindFromRequest.fold(
          formWithErrors => {
            Future.successful(BadRequest(internationalAddressChoiceView(formWithErrors, typ)))
          },
          internationalAddressChoiceDto => {
            cachingHelper.addToCache(SubmittedInternationalAddressChoiceId, internationalAddressChoiceDto) map { _ =>
              if (internationalAddressChoiceDto.value) {
                Redirect(routes.AddressController.showPostcodeLookupForm(typ))
              } else {
                if (configDecorator.updateInternationalAddressInPta) {
                  Redirect(routes.AddressController.showUpdateInternationalAddressForm(typ))
                } else {
                  Redirect(routes.AddressController.cannotUseThisService(typ))
                }
              }
            }
          }
        )

      }
    }

  def cannotUseThisService(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        cachingHelper.gettingCachedAddressPageVisitedDto { addressPageVisitedDto =>
          cachingHelper.enforceDisplayAddressPageVisited(addressPageVisitedDto) {
            Future.successful(Ok(cannotUseServiceView(typ)))
          }
        }
      }
    }

  def showPostcodeLookupForm(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        cachingHelper.gettingCachedJourneyData(typ) { journeyData =>
          cachingHelper.addToCache(SubmittedInternationalAddressChoiceId, InternationalAddressChoiceDto(true))
          typ match {
            case PostalAddrType =>
              auditConnector.sendEvent(
                buildAddressChangeEvent(
                  "postalAddressChangeLinkClicked",
                  personDetails,
                  isInternationalAddress = false))
              cachingHelper.enforceDisplayAddressPageVisited(journeyData.addressPageVisitedDto) {
                Future.successful(Ok(postcodeLookupView(AddressFinderDto.form, typ)))
              }
            case _ =>
              auditConnector.sendEvent(
                buildAddressChangeEvent("mainAddressChangeLinkClicked", personDetails, isInternationalAddress = false))
              cachingHelper.enforceResidencyChoiceSubmitted(journeyData) { _ =>
                Future.successful(Ok(postcodeLookupView(AddressFinderDto.form, typ)))
              }
          }
        }
      }
    }

  def processPostcodeLookupForm(typ: AddrType, back: Option[Boolean] = None): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        AddressFinderDto.form.bindFromRequest.fold(
          formWithErrors => {
            Future.successful(BadRequest(postcodeLookupView(formWithErrors, typ)))
          },
          addressFinderDto => {

            if (addressFinderDto.postcode.isEmpty)
              Logger.warn("post code is empty for processPostCodeLookupForm")

            for {
              _ <- cachingHelper.addToCache(AddressFinderDtoId(typ), addressFinderDto)
              lookupDown <- cachingHelper.gettingCachedAddressLookupServiceDown { lookup =>
                             lookup
                           }
              result <- lookingUpAddress(
                         typ,
                         addressFinderDto.postcode,
                         lookupDown.getOrElse(false),
                         addressFinderDto.filter,
                         forceLookup = true) {
                         case AddressLookupSuccessResponse(RecordSet(Seq())) => //No records returned by postcode lookup
                           auditConnector.sendEvent(
                             buildEvent(
                               "addressLookupNotFound",
                               "find_address",
                               Map(postcode -> Some(addressFinderDto.postcode), filter -> addressFinderDto.filter)))
                           Future.successful(
                             NotFound(postcodeLookupView(
                               AddressFinderDto.form
                                 .fill(AddressFinderDto(addressFinderDto.postcode, addressFinderDto.filter))
                                 .withError(FormError(postcode, "error.address_doesnt_exist_try_to_enter_manually")),
                               typ
                             )))
                         case AddressLookupSuccessResponse(RecordSet(Seq(addressRecord))) => //One record returned by postcode lookup
                           if (back.getOrElse(false)) {
                             Future.successful(Redirect(routes.AddressController.showPostcodeLookupForm(typ)))
                           } else {
                             auditConnector.sendEvent(
                               buildEvent(
                                 "addressLookupResults",
                                 "find_address",
                                 Map(
                                   postcode -> Some(addressRecord.address.postcode),
                                   filter   -> addressFinderDto.filter)))
                             cachingHelper.addToCache(SelectedAddressRecordId(typ), addressRecord) map { _ =>
                               Redirect(routes.AddressController.showUpdateAddressForm(typ))
                             }
                           }
                         case AddressLookupSuccessResponse(recordSet) => //More than one record returned by postcode lookup
                           auditConnector.sendEvent(
                             buildEvent(
                               "addressLookupResults",
                               "find_address",
                               Map(postcode -> Some(addressFinderDto.postcode), filter -> addressFinderDto.filter)))

                           cachingHelper.addToCache(SelectedRecordSetId(typ), recordSet) map { _ =>
                             Redirect(routes.AddressController.showAddressSelectorForm(typ))
                               .addingToSession(
                                 (postcode, addressFinderDto.postcode),
                                 (filter, addressFinderDto.filter.getOrElse("")))
                           }
                       }
            } yield result
          }
        )
      }
    }

  def showAddressSelectorForm(typ: AddrType) =
    authenticate.async { implicit request =>
      cachingHelper.gettingCachedJourneyData(typ) { journeyData =>
        journeyData.recordSet match {
          case Some(set) =>
            Future.successful(
              Ok(
                addressSelectorView(
                  AddressSelectorDto.form,
                  set,
                  typ,
                  postcodeFromRequest,
                  filterFromRequest
                )
              )
            )
          case _ => Future.successful(Redirect(routes.AddressController.showPostcodeLookupForm(typ)))
        }
      }
    }

  def processAddressSelectorForm(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        cachingHelper.gettingCachedJourneyData(typ) { journeyData =>
          AddressSelectorDto.form.bindFromRequest.fold(
            formWithErrors => {

              journeyData.recordSet match {
                case Some(set) =>
                  Future.successful(
                    BadRequest(
                      addressSelectorView(
                        formWithErrors,
                        set,
                        typ,
                        postcodeFromRequest,
                        filterFromRequest
                      )
                    ))
                case _ =>
                  Logger.warn("Failed to retrieve Address Record Set from cache")
                  Future.successful(internalServerError)
              }
            },
            addressSelectorDto => {
              journeyData.recordSet
                .flatMap(_.addresses.find(_.id == addressSelectorDto.addressId.getOrElse(""))) match {
                case Some(addressRecord) =>
                  val addressDto = AddressDto.fromAddressRecord(addressRecord)

                  for {
                    _ <- cachingHelper.addToCache(SelectedAddressRecordId(typ), addressRecord)
                    _ <- cachingHelper.addToCache(SubmittedAddressDtoId(typ), addressDto)
                  } yield {
                    val postCodeHasChanged = !postcodeFromRequest
                      .replace(" ", "")
                      .equalsIgnoreCase(personDetails.address.flatMap(_.postcode).getOrElse("").replace(" ", ""))
                    (typ, postCodeHasChanged) match {
                      case (PostalAddrType, false) =>
                        Redirect(routes.AddressController.showUpdateAddressForm(typ))
                      case (_, true) => Redirect(routes.AddressController.enterStartDate(typ))
                      case (_, false) =>
                        cachingHelper.addToCache(SubmittedStartDateId(typ), DateDto(LocalDate.now()))
                        Redirect(routes.AddressController.reviewChanges(typ))
                    }
                  }
                case _ =>
                  Logger.warn("Address selector was unable to find address using the id returned by a previous request")
                  Future.successful(internalServerError)
              }
            }
          )
        }
      }
    }

  def showUpdateAddressForm(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      cachingHelper.gettingCachedJourneyData[Result](typ) { journeyData =>
        val showEnterAddressHeader = journeyData.addressLookupServiceDown || journeyData.selectedAddressRecord.isEmpty
        addressJourneyEnforcer { _ => _ =>
          typ match {
            case PostalAddrType =>
              cachingHelper.enforceDisplayAddressPageVisited(journeyData.addressPageVisitedDto) {
                val addressForm = journeyData.getAddressToDisplay.fold(AddressDto.ukForm)(AddressDto.ukForm.fill)
                Future.successful(
                  Ok(
                    updateAddressView(
                      addressForm.discardingErrors,
                      typ,
                      journeyData.addressFinderDto,
                      journeyData.addressLookupServiceDown,
                      showEnterAddressHeader)))
              }
            case _ =>
              cachingHelper.enforceResidencyChoiceSubmitted(journeyData) { journeyData =>
                val addressForm = journeyData.getAddressToDisplay.fold(AddressDto.ukForm)(AddressDto.ukForm.fill)
                Future.successful(
                  Ok(
                    updateAddressView(
                      addressForm.discardingErrors,
                      typ,
                      journeyData.addressFinderDto,
                      journeyData.addressLookupServiceDown,
                      showEnterAddressHeader
                    )
                  )
                )
              }
          }
        }
      }
    }

  def processUpdateAddressForm(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      cachingHelper.gettingCachedJourneyData[Result](typ) { journeyData =>
        val showEnterAddressHeader = journeyData.addressLookupServiceDown || journeyData.selectedAddressRecord.isEmpty
        addressJourneyEnforcer { _ => personDetails =>
          {
            AddressDto.ukForm.bindFromRequest.fold(
              formWithErrors => {
                Future.successful(
                  BadRequest(
                    updateAddressView(
                      formWithErrors,
                      typ,
                      journeyData.addressFinderDto,
                      journeyData.addressLookupServiceDown,
                      showEnterAddressHeader
                    )
                  )
                )
              },
              addressDto => {
                cachingHelper.addToCache(SubmittedAddressDtoId(typ), addressDto) flatMap {
                  _ =>
                    val postCodeHasChanged = !addressDto.postcode
                      .getOrElse("")
                      .replace(" ", "")
                      .equalsIgnoreCase(personDetails.address.flatMap(_.postcode).getOrElse("").replace(" ", ""))
                    (typ, postCodeHasChanged) match {
                      case (PostalAddrType, _) =>
                        cachingHelper.addToCache(SubmittedStartDateId(typ), DateDto(LocalDate.now()))
                        Future.successful(Redirect(routes.AddressController.reviewChanges(typ)))
                      case (_, false) =>
                        cachingHelper.addToCache(SubmittedStartDateId(typ), DateDto(LocalDate.now()))
                        Future.successful(Redirect(routes.AddressController.reviewChanges(typ)))
                      case (_, true) =>
                        Future.successful(Redirect(routes.AddressController.enterStartDate(typ)))
                    }
                }
              }
            )
          }
        }
      }
    }

  def showUpdateInternationalAddressForm(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      cachingHelper.gettingCachedJourneyData[Result](typ) { journeyData =>
        addressJourneyEnforcer { _ => personDetails =>
          typ match {
            case PostalAddrType =>
              auditConnector.sendEvent(
                buildAddressChangeEvent("postalAddressChangeLinkClicked", personDetails, isInternationalAddress = true))
              cachingHelper.enforceDisplayAddressPageVisited(journeyData.addressPageVisitedDto) {
                Future.successful(
                  Ok(
                    updateInternationalAddressView(
                      journeyData.submittedAddressDto.fold(AddressDto.internationalForm)(
                        AddressDto.internationalForm.fill),
                      typ,
                      countryHelper.countries
                    )
                  )
                )
              }

            case _ =>
              auditConnector.sendEvent(
                buildAddressChangeEvent("mainAddressChangeLinkClicked", personDetails, isInternationalAddress = true))
              cachingHelper.enforceResidencyChoiceSubmitted(journeyData) { _ =>
                Future.successful(
                  Ok(
                    updateInternationalAddressView(AddressDto.internationalForm, typ, countryHelper.countries)
                  )
                )
              }
          }
        }
      }
    }

  def processUpdateInternationalAddressForm(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      cachingHelper.gettingCachedJourneyData[Result](typ) { _ =>
        addressJourneyEnforcer { _ => _ =>
          {
            AddressDto.internationalForm.bindFromRequest.fold(
              formWithErrors => {
                Future.successful(
                  BadRequest(updateInternationalAddressView(formWithErrors, typ, countryHelper.countries)))
              },
              addressDto => {
                cachingHelper.addToCache(SubmittedAddressDtoId(typ), addressDto) flatMap { _ =>
                  typ match {
                    case PostalAddrType =>
                      cachingHelper.addToCache(SubmittedStartDateId(typ), DateDto(LocalDate.now()))
                      Future.successful(Redirect(routes.AddressController.reviewChanges(typ)))
                    case _ =>
                      Future.successful(Redirect(routes.AddressController.enterStartDate(typ)))
                  }
                }
              }
            )
          }
        }
      }
    }

  def nonPostalJourneyEnforcer(typ: AddrType)(block: => Future[Result]): Future[Result] =
    typ match {
      case _: ResidentialAddrType => block
      case PostalAddrType         => Future.successful(Redirect(routes.AddressController.showUpdateAddressForm(typ)))
    }

  def enterStartDate(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        nonPostalJourneyEnforcer(typ) {
          cachingHelper.gettingCachedJourneyData(typ) { journeyData =>
            val newPostcode = journeyData.submittedAddressDto.map(_.postcode).getOrElse("").toString
            val oldPostcode = personDetails.address.flatMap(add => add.postcode).getOrElse("")
            journeyData.submittedAddressDto map { _ =>
              Future.successful(Ok(enterStartDateView(
                if (newPostcode.replace(" ", "").equalsIgnoreCase(oldPostcode.replace(" ", "")))
                  journeyData.submittedStartDateDto.fold(dateDtoForm)(dateDtoForm.fill)
                else dateDtoForm,
                typ
              )))
            } getOrElse {
              Future.successful(Redirect(routes.AddressController.personalDetails()))
            }
          }
        }
      }
    }

  def processEnterStartDate(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        nonPostalJourneyEnforcer(typ) {
          dateDtoForm.bindFromRequest.fold(
            formWithErrors => {
              Future.successful(BadRequest(enterStartDateView(formWithErrors, typ)))
            },
            dateDto => {
              cachingHelper.addToCache(SubmittedStartDateId(typ), dateDto) map {
                _ =>
                  val proposedStartDate = dateDto.startDate

                  personDetails.address match {
                    case Some(Address(_, _, _, _, _, _, _, Some(currentStartDate), _, _)) =>
                      if (!currentStartDate.isBefore(proposedStartDate))
                        BadRequest(
                          cannotUpdateAddressView(typ, LanguageHelper.langUtils.Dates.formatDate(proposedStartDate)))
                      else Redirect(routes.AddressController.reviewChanges(typ))
                    case _ => Redirect(routes.AddressController.reviewChanges(typ))
                  }
              }
            }
          )
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
        Future.successful(Ok(closeCorrespondenceAddressChoiceView(address, ClosePostalAddressChoiceDto.form)))
      }
    }

  def processClosePostalAddressChoice: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personalDetails =>
        ClosePostalAddressChoiceDto.form.bindFromRequest.fold(
          formWithErrors => {
            Future.successful(
              BadRequest(
                closeCorrespondenceAddressChoiceView(getAddress(personalDetails.address).fullAddress, formWithErrors))
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
        Future.successful(Ok(confirmCloseCorrespondenceAddressView(address)))
      }
    }

  private def submitConfirmClosePostalAddress(nino: Nino, personDetails: PersonDetails)(
    implicit request: UserRequest[_]): Future[Result] = {

    val address = getAddress(personDetails.correspondenceAddress)
    val closingAddress = address.copy(endDate = Some(LocalDate.now), startDate = Some(LocalDate.now))

    citizenDetailsService.getEtag(nino.nino) flatMap {
      case None =>
        Logger.error("Failed to retrieve Etag from citizen-details")
        Future.successful(internalServerError)

      case Some(version) =>
        for {
          response <- citizenDetailsService.updateAddress(nino, version.etag, closingAddress)
          action <- response match {
                     case UpdateAddressBadRequestResponse =>
                       Future.successful(
                         BadRequest(
                           error(
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
                                 auditForClosingPostalAddress(closingAddress, version.etag, "correspondence")))
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
                           internalServerError
                         }
                   }
        } yield action
    }
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
        cachingHelper.gettingCachedJourneyData(typ) { journeyData =>
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
            ensuringSubmissionRequirments(typ, journeyData) {
              journeyData.submittedAddressDto.fold(
                Future.successful(Redirect(routes.AddressController.personalDetails()))) { addressDto =>
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

  private def handleAddressChangeAuditing(
    originalAddressDto: Option[AddressDto],
    addressDto: AddressDto,
    version: ETag,
    addressType: String)(implicit hc: HeaderCarrier, request: UserRequest[_]) =
    if (addressWasUnmodified(originalAddressDto, addressDto))
      auditConnector.sendEvent(
        buildEvent(
          "postcodeAddressSubmitted",
          "change_of_address",
          dataToAudit(addressDto, version.etag, addressType, None, originalAddressDto.flatMap(_.propertyRefNo))
            .filter(!_._1.startsWith("originalLine")) - "originalPostcode"
        ))
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

  private def mapAddressType(typ: AddrType) = typ match {
    case PostalAddrType => "Correspondence"
    case _              => "Residential"
  }

  def submitChanges(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      val addressType = mapAddressType(typ)

      addressJourneyEnforcer { nino => personDetails =>
        citizenDetailsService.getEtag(nino.nino) flatMap {
          case None =>
            Logger.error("Failed to retrieve Etag from citizen-details")
            Future.successful(internalServerError)

          case Some(version) =>
            cachingHelper.gettingCachedJourneyData(typ) { journeyData =>
              ensuringSubmissionRequirments(typ, journeyData) {

                journeyData.submittedAddressDto.fold(
                  Future.successful(Redirect(routes.AddressController.personalDetails()))) { addressDto =>
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
                          addressType)
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
                        result <- citizenDetailsService.updateAddress(nino, version.etag, address)
                      } yield {
                        result.response(successResponseBlock)
                      }
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
        Future.successful(Ok(addressAlreadyUpdatedView()))
      }
    }

  private def internalServerError(implicit userRequest: UserRequest[_]): Result =
    InternalServerError(
      error(
        "global.error.InternalServerError500.title",
        Some("global.error.InternalServerError500.title"),
        List("global.error.InternalServerError500.message")
      ))
}

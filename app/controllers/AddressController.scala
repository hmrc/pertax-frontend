/*
 * Copyright 2019 HM Revenue & Customs
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
import connectors.{FrontEndDelegationConnector, PertaxAuditConnector, PertaxAuthConnector}
import controllers.auth.{AuthorisedActions, PertaxRegime}
import controllers.bindable._
import controllers.helpers.AddressJourneyAuditingHelper._
import controllers.helpers.{AddressJourneyCachingHelper, CountryHelper, PersonalDetailsCardGenerator}
import error.LocalErrorHandler
import javax.inject.Inject

import models._
import models.addresslookup.RecordSet
import models.dto._
import org.joda.time.LocalDate
import play.api.Logger
import play.api.data.FormError
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.twirl.api.Html
import repositories.CorrespondenceAddressLockRepository
import services._
import services.partials.MessageFrontendService
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException}
import uk.gov.hmrc.play.frontend.auth.connectors.domain.PayeAccount
import uk.gov.hmrc.play.language.LanguageUtils.Dates._
import uk.gov.hmrc.renderer.ActiveTabYourAccount
import util.AuditServiceTools._
import util.LocalPartialRetriever

import scala.concurrent.Future


class AddressController @Inject() (
  val messagesApi: MessagesApi,
  val citizenDetailsService: CitizenDetailsService,
  val userDetailsService: UserDetailsService,
  val addressLookupService: AddressLookupService,
  val messageFrontendService: MessageFrontendService,
  val delegationConnector: FrontEndDelegationConnector,
  val sessionCache: LocalSessionCache,
  val auditConnector: PertaxAuditConnector,
  val authConnector: PertaxAuthConnector,
  val partialRetriever: LocalPartialRetriever,
  val configDecorator: ConfigDecorator,
  val pertaxRegime: PertaxRegime,
  val localErrorHandler: LocalErrorHandler,
  val personalDetailsCardGenerator: PersonalDetailsCardGenerator,
  val countryHelper: CountryHelper,
  val correspondenceAddressLockRepository: CorrespondenceAddressLockRepository
) extends PertaxBaseController with AuthorisedActions with AddressJourneyCachingHelper {

  def dateDtoForm = DateDto.form(configDecorator.currentLocalDate)

  def currentAddressType(personDetails: PersonDetails) = personDetails.address.flatMap(_.`type`).getOrElse("Residential")

  def getAddress(address: Option[Address]): Address = {
    address match {
      case Some(address) => address
      case None => throw new Exception("Address does not exist in the current context")
    }

  }

  def addressBreadcrumb: Breadcrumb =
    "label.personal_details" -> routes.AddressController.personalDetails.url ::
    baseBreadcrumb

  def addressJourneyEnforcer(block: PayeAccount => PersonDetails => Future[Result])(implicit pertaxContext: PertaxContext): Future[Result] = {
    PertaxUser.ifHighGovernmentGatewayOrVerifyUser {
      enforcePersonDetails { payeAccount => personDetails =>
        block(payeAccount)(personDetails)
      }
    } getOrElse Future.successful {
      val continueUrl = configDecorator.pertaxFrontendHost + controllers.routes.AddressController.personalDetails.url
      Ok(views.html.interstitial.displayAddressInterstitial(continueUrl))
    }
  }




  def lookingUpAddress(typ: AddrType, postcode: String, lookupServiceDown: Boolean, filter: Option[String] = None, forceLookup: Boolean = false)(f: PartialFunction[AddressLookupResponse, Future[Result]])(implicit context: PertaxContext): Future[Result] = {
    if (!forceLookup && lookupServiceDown) {
      Future.successful(Redirect(routes.AddressController.showUpdateAddressForm(typ)))
    } else {
      val handleError: PartialFunction[AddressLookupResponse, Future[Result]] = {
        case AddressLookupErrorResponse(_) | AddressLookupUnexpectedResponse(_) =>
          cacheAddressLookupServiceDown() map { _ =>
            Redirect(routes.AddressController.showUpdateAddressForm(typ))
          }
      }
      addressLookupService.lookup(postcode, filter).flatMap(handleError orElse f)
    }
  }

  def personalDetails: Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) {
    implicit pertaxContext =>
      import models.dto.AddressPageVisitedDto
      def optNino = pertaxContext.user.flatMap(_.personDetails.flatMap(_.person.nino))
      for {
        hasCorrespondenceAddressLock <- optNino match {
          case Some(nino) => correspondenceAddressLockRepository.get(nino) map {
            lock =>
              Logger.warn(s"correspondence lock some expire at : ${lock.map(_.expireAt)}")
              lock.isDefined
          }
          case _ => Future.successful(false)
        }
        personalDetailsCards: Seq[Html] = personalDetailsCardGenerator.getPersonalDetailsCards(hasCorrespondenceAddressLock)
        personDetails: Option[PersonDetails] = pertaxContext.user.flatMap(_.personDetails)
        _ <- personDetails match {
          case Some(p) => auditConnector.sendEvent(buildAddressChangeEvent("personalDetailsPageLinkClicked", p))
          case _ => Future.successful(Unit)
        }
        _ <- cacheAddressPageVisited(AddressPageVisitedDto(true))
      } yield Ok(views.html.personaldetails.personalDetails(personalDetailsCards))
  }

  def taxCreditsChoice = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) { implicit pertaxContext =>
    addressJourneyEnforcer { payeAccount => personalDetails =>
      gettingCachedAddressPageVisitedDto { addressPageVisitedDto =>
        enforceDisplayAddressPageVisited(addressPageVisitedDto) {
          Future.successful(Ok(views.html.personaldetails.taxCreditsChoice(TaxCreditsChoiceDto.form, configDecorator.tcsChangeAddressUrl)))
        }
      }
    }
  }

  def processTaxCreditsChoice = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) { implicit pertaxContext =>
    addressJourneyEnforcer { payeAccount => personalDetails =>
      TaxCreditsChoiceDto.form.bindFromRequest.fold(
        formWithErrors => {
          Future.successful(BadRequest(views.html.personaldetails.taxCreditsChoice(formWithErrors, configDecorator.tcsChangeAddressUrl)))
        },
        taxCreditsChoiceDto => {
          cacheSubmitedTaxCreditsChoiceDto(taxCreditsChoiceDto) map { _ =>
            taxCreditsChoiceDto.value match {
              case true => Redirect(configDecorator.tcsChangeAddressUrl)
              case false => Redirect(routes.AddressController.residencyChoice())
            }
          }
        }
      )

    }
  }

  def residencyChoice: Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) { implicit pertaxContext =>
    addressJourneyEnforcer { payeAccount => personDetails =>
      auditConnector.sendEvent(buildAddressChangeEvent("mainAddressChangeLinkClicked", personDetails))
      gettingCachedTaxCreditsChoiceDto {
        case Some(TaxCreditsChoiceDto(false)) =>
          Ok(views.html.personaldetails.residencyChoice(ResidencyChoiceDto.form))
        case _ => configDecorator.taxCreditsEnabled match {
          case true => Redirect(routes.AddressController.personalDetails)
          case false => Ok(views.html.personaldetails.residencyChoice(ResidencyChoiceDto.form))
        }
      }
    }
  }

  def processResidencyChoice: Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) { implicit pertaxContext =>
    addressJourneyEnforcer { payeAccount => personDetails =>
      ResidencyChoiceDto.form.bindFromRequest.fold(
        formWithErrors => {
          Future.successful(BadRequest(views.html.personaldetails.residencyChoice(formWithErrors)))
        },
        residencyChoiceDto => {
          cacheSubmitedResidencyChoiceDto(residencyChoiceDto) map { _ =>
            Redirect(routes.AddressController.internationalAddressChoice(residencyChoiceDto.residencyChoice))
          }
        }
      )

    }
  }

  def internationalAddressChoice(typ: AddrType): Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) { implicit pertaxContext =>
    addressJourneyEnforcer { payeAccount => personalDetails =>
      gettingCachedAddressPageVisitedDto { addressPageVisitedDto =>
        enforceDisplayAddressPageVisited(addressPageVisitedDto) {
          Future.successful(Ok(views.html.personaldetails.internationalAddressChoice(InternationalAddressChoiceDto.form, typ)))
        }
      }
    }
  }

  def processInternationalAddressChoice(typ: AddrType): Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) { implicit pertaxContext =>
    addressJourneyEnforcer { payeAccount => personalDetails =>
      InternationalAddressChoiceDto.form.bindFromRequest.fold(
        formWithErrors => {
          Future.successful(BadRequest(views.html.personaldetails.internationalAddressChoice(formWithErrors, typ)))
        },
        internationalAddressChoiceDto => {
          cacheSubmittedInternationalAddressChoiceDto(internationalAddressChoiceDto) map { _ =>
            internationalAddressChoiceDto.value match {
              case true => Redirect(routes.AddressController.showPostcodeLookupForm(typ))
              case false => if(configDecorator.updateInternationalAddressInPta) {
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

  def cannotUseThisService(typ: AddrType): Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) { implicit pertaxContext =>
    addressJourneyEnforcer { payeAccount => personalDetails =>
      gettingCachedAddressPageVisitedDto { addressPageVisitedDto =>
        enforceDisplayAddressPageVisited(addressPageVisitedDto) {
          Future.successful(Ok(views.html.personaldetails.cannotUseService(typ)))
        }
      }
    }
  }

  def showPostcodeLookupForm(typ: AddrType): Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) {
    implicit pertaxContext =>
      addressJourneyEnforcer { payeAccount => personDetails =>
        gettingCachedJourneyData(typ) { journeyData =>
          cacheSubmittedInternationalAddressChoiceDto(InternationalAddressChoiceDto.apply(true))
          typ match {
            case PostalAddrType =>
              auditConnector.sendEvent(buildAddressChangeEvent("postalAddressChangeLinkClicked", personDetails))
              enforceDisplayAddressPageVisited(journeyData.addressPageVisitedDto) {
                Future.successful(Ok(views.html.personaldetails.postcodeLookup(AddressFinderDto.form, typ)))
              }
            case _ =>
              enforceResidencyChoiceSubmitted(journeyData) { x =>
                Future.successful(Ok(views.html.personaldetails.postcodeLookup(AddressFinderDto.form, typ)))
              }
          }
        }
      }
  }

  def processPostcodeLookupForm(typ: AddrType, back: Option[Boolean] = None): Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) { implicit pertaxContext =>
    addressJourneyEnforcer { payeAccount => personDetails =>
      AddressFinderDto.form.bindFromRequest.fold(
        formWithErrors => {
          Future.successful(BadRequest(views.html.personaldetails.postcodeLookup(formWithErrors, typ)))
        },
        addressFinderDto => {
          for {
            cacheMap <- cacheAddressFinderDto(typ, addressFinderDto)
            lookupDown <- gettingCachedAddressLookupServiceDown { lookup => lookup}
            result <- lookingUpAddress(typ, addressFinderDto.postcode, lookupDown.getOrElse(false), addressFinderDto.filter, forceLookup = true) {
                case AddressLookupSuccessResponse(RecordSet(Seq())) => //No records returned by postcode lookup
                {
                  auditConnector.sendEvent(buildEvent("addressLookupNotFound", "find_address", Map("postcode" -> Some(addressFinderDto.postcode), "filter" -> addressFinderDto.filter)))
                  Future.successful(NotFound(views.html.personaldetails.postcodeLookup(AddressFinderDto.form.fill(AddressFinderDto(addressFinderDto.postcode, addressFinderDto.filter))
                    .withError(FormError("postcode", "error.address_doesnt_exist_try_to_enter_manually")), typ)))
                }
                case AddressLookupSuccessResponse(RecordSet(Seq(addressRecord))) => //One record returned by postcode lookup
                {
                  if (back.getOrElse(false)) {
                    Future.successful(Redirect(routes.AddressController.showPostcodeLookupForm(typ)))
                  }
                  else {
                    auditConnector.sendEvent(buildEvent("addressLookupResults", "find_address", Map("postcode" -> Some(addressRecord.address.postcode), "filter" -> addressFinderDto.filter)))
                    cacheSelectedAddressRecord(typ, addressRecord) map { _ =>
                      Redirect(routes.AddressController.showUpdateAddressForm(typ))
                    }
                  }
                }
                case AddressLookupSuccessResponse(recordSet) => //More than one record returned by postcode lookup
                {
                  auditConnector.sendEvent(buildEvent("addressLookupResults", "find_address", Map("postcode" -> Some(addressFinderDto.postcode), "filter" -> addressFinderDto.filter)))
                  Future.successful(Ok(views.html.personaldetails.addressSelector(AddressSelectorDto.form, recordSet, typ, addressFinderDto.postcode, addressFinderDto.filter)))
                }
              }
            } yield result
          }
      )
    }
  }

  def processAddressSelectorForm(typ: AddrType, postcode: String, filter: Option[String]): Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) {
    implicit pertaxContext =>
      addressJourneyEnforcer { payeAccount => personDetails =>
        gettingCachedJourneyData(typ) { journeyData =>
          AddressSelectorDto.form.bindFromRequest.fold(
            formWithErrors => {
              lookingUpAddress(typ, postcode, journeyData.addressLookupServiceDown, filter) {
                case AddressLookupSuccessResponse(recordSet) =>
                  Future.successful(BadRequest(views.html.personaldetails.addressSelector(formWithErrors, recordSet, typ, postcode, filter)))
              }
            },
            addressSelectorDto => {
              lookingUpAddress(typ, postcode, journeyData.addressLookupServiceDown) {
                case AddressLookupSuccessResponse(recordSet) =>
                  recordSet.addresses.find(_.id == addressSelectorDto.addressId.getOrElse("")) map { addressRecord =>

                      val addressDto = AddressDto.fromAddressRecord(addressRecord)
                      cacheSelectedAddressRecord(typ, addressRecord) flatMap { _ =>
                        cacheSubmittedAddressDto(typ, addressDto) map { _ =>
                          val postCodeHasChanged = !postcode.replace(" ", "").equalsIgnoreCase(personDetails.address.flatMap(_.postcode).getOrElse("").replace(" ", ""))
                          (typ, postCodeHasChanged) match {
                            case (PostalAddrType, true) => Redirect(routes.AddressController.enterStartDate(typ))
                            case (PostalAddrType, false) => Redirect(routes.AddressController.showUpdateAddressForm(typ))
                            case (_, true) => Redirect(routes.AddressController.enterStartDate(typ))
                            case (_, false) => {
                              cacheSubmittedStartDate(typ, DateDto(LocalDate.now()))
                              Redirect(routes.AddressController.reviewChanges(typ))
                            }
                          }
                        }
                      }
                  } getOrElse {
                    Logger.warn("Address selector was unable to find address using the id returned by a previous request")
                    Future.successful(InternalServerError(views.html.error("global.error.InternalServerError500.title",
                      Some("global.error.InternalServerError500.title"),
                      Some("global.error.InternalServerError500.message"), false)))
                  }
              }
            }
          )
        }
      }
  }

  def showUpdateAddressForm(typ: AddrType): Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) {
    implicit pertaxContext =>
      gettingCachedJourneyData[Result](typ) { journeyData =>
        val showEnterAddressHeader = journeyData.addressLookupServiceDown || journeyData.selectedAddressRecord == None
        addressJourneyEnforcer { payeAccount => personDetails =>
          typ match {
            case PostalAddrType =>
              enforceDisplayAddressPageVisited(journeyData.addressPageVisitedDto) {
                val addressForm = journeyData.getAddressToDisplay.fold(AddressDto.ukForm)(AddressDto.ukForm.fill)
                Future.successful(Ok(views.html.personaldetails.updateAddress(addressForm.discardingErrors, typ, journeyData.addressFinderDto, journeyData.addressLookupServiceDown, showEnterAddressHeader)))
              }
            case _ =>
              enforceResidencyChoiceSubmitted(journeyData) { journeyData =>
                val addressForm = journeyData.getAddressToDisplay.fold(AddressDto.ukForm)(AddressDto.ukForm.fill)
                Future.successful(Ok(views.html.personaldetails.updateAddress(addressForm.discardingErrors, typ, journeyData.addressFinderDto, journeyData.addressLookupServiceDown, showEnterAddressHeader)))
              }
          }
        }
      }
  }

  def processUpdateAddressForm(typ: AddrType): Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) {
    implicit pertaxContext =>
      gettingCachedJourneyData[Result](typ) { journeyData =>
        val showEnterAddressHeader = journeyData.addressLookupServiceDown || journeyData.selectedAddressRecord == None
        addressJourneyEnforcer {
          payeAccount => personDetails => {
            AddressDto.ukForm.bindFromRequest.fold(
              formWithErrors => {
                Future.successful(BadRequest(views.html.personaldetails.updateAddress(formWithErrors, typ, journeyData.addressFinderDto, journeyData.addressLookupServiceDown, showEnterAddressHeader)))
              },
              addressDto => {
                cacheSubmittedAddressDto(typ, addressDto) flatMap { _ =>
                  val postCodeHasChanged = !addressDto.postcode.getOrElse("").replace(" ", "").equalsIgnoreCase(personDetails.address.flatMap(_.postcode).getOrElse("").replace(" ", ""))
                  (typ, postCodeHasChanged) match {
                    case (PostalAddrType, _) =>
                      cacheSubmittedStartDate(typ, DateDto(LocalDate.now()))
                      Future.successful(Redirect(routes.AddressController.reviewChanges(typ)))
                    case (_, false) =>
                      cacheSubmittedStartDate(typ, DateDto(LocalDate.now()))
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

  def showUpdateInternationalAddressForm(typ: AddrType): Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) {
    implicit pertaxContext =>
      gettingCachedJourneyData[Result](typ) { journeyData =>
        addressJourneyEnforcer { payeAccount => personDetails =>
          typ match {
            case PostalAddrType =>
              enforceDisplayAddressPageVisited(journeyData.addressPageVisitedDto) {
                Future.successful(Ok(views.html.personaldetails.updateInternationalAddress(journeyData.submittedAddressDto.fold(AddressDto.internationalForm)(AddressDto.internationalForm.fill), typ, countryHelper.countries)))
              }

            case _ =>
              enforceResidencyChoiceSubmitted(journeyData) { journeyData =>
                Future.successful(Ok(views.html.personaldetails.updateInternationalAddress(AddressDto.internationalForm, typ, countryHelper.countries)))
              }
          }
        }
      }
  }

  def processUpdateInternationalAddressForm(typ: AddrType): Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) {
    implicit pertaxContext =>
      gettingCachedJourneyData[Result](typ) { journeyData =>
        addressJourneyEnforcer {
          payeAccount => personDetails => {
            AddressDto.internationalForm.bindFromRequest.fold(
              formWithErrors => {
                Future.successful(BadRequest(views.html.personaldetails.updateInternationalAddress(formWithErrors, typ, countryHelper.countries)))
              },
              addressDto => {
                cacheSubmittedAddressDto(typ, addressDto) flatMap { _ =>
                  typ match {
                    case PostalAddrType =>
                      cacheSubmittedStartDate(typ, DateDto(LocalDate.now()))
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

  def nonPostalJourneyEnforcer(typ: AddrType)(block: => Future[Result])(implicit pertaxContext: PertaxContext) = typ match {
    case x: ResidentialAddrType => block
    case PostalAddrType => Future.successful(Redirect(routes.AddressController.showUpdateAddressForm(typ)))
  }

  def enterStartDate(typ: AddrType): Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) {
    implicit pertaxContext =>
      addressJourneyEnforcer { payeAccount => personDetails =>
        nonPostalJourneyEnforcer(typ) {
          gettingCachedJourneyData(typ) { journeyData =>
            val newPostcode = journeyData.submittedAddressDto.map(_.postcode).getOrElse("").toString
            val oldPostcode = personDetails.address.flatMap(add => add.postcode).getOrElse("")
            journeyData.submittedAddressDto map { a =>
              Future.successful(Ok(views.html.personaldetails.enterStartDate(if(newPostcode.replace(" ", "").equalsIgnoreCase(oldPostcode.replace(" ", ""))) journeyData.submittedStartDateDto.fold(dateDtoForm)(dateDtoForm.fill) else dateDtoForm, typ)))
            } getOrElse {
              Future.successful(Redirect(routes.AddressController.personalDetails()))
            }
          }
        }
      }
  }

  def processEnterStartDate(typ: AddrType): Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) {
    implicit pertaxContext =>
      addressJourneyEnforcer { payeAccount => personDetails =>
        nonPostalJourneyEnforcer(typ) {
          dateDtoForm.bindFromRequest.fold(
            formWithErrors => {
              Future.successful(BadRequest(views.html.personaldetails.enterStartDate(formWithErrors, typ)))
            },
            dateDto => {
              cacheSubmittedStartDate(typ, dateDto) map { _ =>

                val proposedStartDate = dateDto.startDate

                personDetails.address match {
                  case Some(Address(_, _, _, _, _, _, _, Some(currentStartDate), _, _)) =>
                    if(!currentStartDate.isBefore(proposedStartDate))
                      BadRequest(views.html.personaldetails.cannotUpdateAddress(typ, formatDate(proposedStartDate)))
                    else Redirect(routes.AddressController.reviewChanges(typ))
                  case _ => Redirect(routes.AddressController.reviewChanges(typ))
                }
              }
            }
          )
        }
      }
  }


  def ensuringSubmissionRequirments(typ: AddrType, journeyData: AddressJourneyData)(block: => Future[Result]) = {
    if(journeyData.submittedStartDateDto == None && (typ == PrimaryAddrType | typ == SoleAddrType))
      Future.successful(Redirect(routes.AddressController.personalDetails()))
    else
      block
  }

  def closePostalAddressChoice: Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) {
    implicit pertaxContext =>
      addressJourneyEnforcer { payeAccount =>
        personDetails =>
           val address = getAddress(personDetails.address).fullAddress
              Future.successful(Ok(views.html.personaldetails.closeCorrespondenceAdressChoice(address, ClosePostalAddressChoiceDto.form )))
      }
  }

  def processClosePostalAddressChoice: Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) {
    implicit pertaxContext =>
      addressJourneyEnforcer { payeAccount =>
        personalDetails =>
          ClosePostalAddressChoiceDto.form.bindFromRequest.fold(
            formWithErrors => {
              Future.successful(BadRequest(views.html.personaldetails.closeCorrespondenceAdressChoice(getAddress(personalDetails.address).fullAddress, formWithErrors)))
            },
            closePostalAddressChoiceDto => {
                closePostalAddressChoiceDto.value match {
                  case true => Future.successful(Redirect(routes.AddressController.confirmClosePostalAddress()))
                  case false => Future.successful(Redirect(routes.AddressController.personalDetails()))
                }
            }
          )
      }
  }

  def confirmClosePostalAddress: Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) {
    implicit pertaxContext =>
      addressJourneyEnforcer { payeAccount =>
        personDetails =>
            val address = getAddress(personDetails.address).fullAddress
            Future.successful(Ok(views.html.personaldetails.confirmCloseCorrespondenceAddress(address)))

      }
  }

  private def submitConfirmClosePostalAddress(payeAccount: PayeAccount, personDetails: PersonDetails)(implicit pertaxContext: PertaxContext): Future[Result] = {
    def internalServerError = InternalServerError(views.html.error("global.error.InternalServerError500.title",
      Some("global.error.InternalServerError500.title"), Some("global.error.InternalServerError500.message"), showContactHmrc = false))

    val address = getAddress(personDetails.correspondenceAddress)
    val closingAddress = address.copy(endDate = Some(LocalDate.now), startDate = Some(LocalDate.now))

    for {
      response <- citizenDetailsService.updateAddress(payeAccount.nino, personDetails.etag, closingAddress)
      action <- response match {
        case UpdateAddressBadRequestResponse =>
          Future.successful(BadRequest(views.html.error("global.error.BadRequest.title", Some("global.error.BadRequest.title"),
            Some("global.error.BadRequest.message"), showContactHmrc = false)))
        case UpdateAddressUnexpectedResponse(_) | UpdateAddressErrorResponse(_) =>
          Future.successful(internalServerError)
        case UpdateAddressSuccessResponse =>
          for {
            _ <- auditConnector.sendEvent(buildEvent("closedAddressSubmitted", "closure_of_correspondence", auditForClosingPostalAddress(closingAddress, personDetails.etag, "correspondence")))
            _ <- clearCache() //This clears ENTIRE session cache, no way to target individual keys
            inserted <- correspondenceAddressLockRepository.insert(payeAccount.nino)
          } yield
            if (inserted)
              Ok(views.html.personaldetails.updateAddressConfirmation(PostalAddrType, closedPostalAddress = true, Some(getAddress(personDetails.address).fullAddress)))
            else
              internalServerError
      }
    } yield action
  }

  def submitConfirmClosePostalAddress: Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount))  {
    implicit pertaxContext =>
      addressJourneyEnforcer { payeAccount => personDetails =>
          for {
            optLock <- correspondenceAddressLockRepository.get(payeAccount.nino)
            result <- optLock match {
              case Some(_) =>
                Future.successful(Redirect(routes.AddressController.personalDetails()))
              case None =>
                submitConfirmClosePostalAddress(payeAccount,personDetails)
            }
          } yield result
      }
    }

  def reviewChanges(typ: AddrType): Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) {
    implicit pertaxContext =>
      addressJourneyEnforcer { payeAccount => personDetails =>
        gettingCachedJourneyData(typ) { journeyData =>

          val isUkAddress: Boolean = journeyData.subbmittedInternationalAddressChoiceDto.map(_.value).getOrElse(true)
          val doYouLiveInTheUK: String = journeyData.subbmittedInternationalAddressChoiceDto.map(_.value).getOrElse(true) match {
            case true => "label.yes"
            case false => "label.no"
          }

          if (isUkAddress) {
            val newPostcode: String = journeyData.submittedAddressDto.map(_.postcode).fold("")(_.getOrElse(""))
            val oldPostcode: String = personDetails.address.flatMap(add => add.postcode).fold("")(_.toString)

            val showAddressChangedDate: Boolean = !newPostcode.replace(" ", "").equalsIgnoreCase(oldPostcode.replace(" ", ""))
            ensuringSubmissionRequirments(typ, journeyData) {
              journeyData.submittedAddressDto.fold(Future.successful(Redirect(routes.AddressController.personalDetails()))) { addressDto =>
                Future.successful(Ok(views.html.personaldetails.reviewChanges(typ, addressDto, doYouLiveInTheUK, isUkAddress, journeyData.submittedStartDateDto, showAddressChangedDate)))
              }
            }
          } else {
            ensuringSubmissionRequirments(typ, journeyData) {
              journeyData.submittedAddressDto.fold(Future.successful(Redirect(routes.AddressController.personalDetails()))) { addressDto =>
                Future.successful(Ok(views.html.personaldetails.reviewChanges(typ, addressDto, doYouLiveInTheUK, isUkAddress, journeyData.submittedStartDateDto, true)))
              }
            }
          }
        }
      }
  }

  private def handleAddressChangeAuditing(originalAddressDto: Option[AddressDto], addressDto: AddressDto, personDetails: PersonDetails, addressType: String)(implicit hc: HeaderCarrier, pertaxContext: PertaxContext) = {

    if (addressWasUnmodified(originalAddressDto, addressDto))
      auditConnector.sendEvent(buildEvent("postcodeAddressSubmitted", "change_of_address", dataToAudit(addressDto, personDetails.etag, addressType, None, originalAddressDto.flatMap(_.propertyRefNo)).filter(!_._1.startsWith("originalLine")) - "originalPostcode"))
    else if (addressWasHeavilyModifiedOrManualEntry(originalAddressDto, addressDto))
      auditConnector.sendEvent(buildEvent("manualAddressSubmitted", "change_of_address", dataToAudit(addressDto, personDetails.etag, addressType, None, originalAddressDto.flatMap(_.propertyRefNo))))
    else
      auditConnector.sendEvent(buildEvent("postcodeAddressModifiedSubmitted", "change_of_address", dataToAudit(addressDto, personDetails.etag, addressType, originalAddressDto, originalAddressDto.flatMap(_.propertyRefNo))))
  }

  private def mapAddressType(typ: AddrType) = typ match {
    case PostalAddrType => "Correspondence"
    case _ => "Residential"
  }

  def submitChanges(typ: AddrType): Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) {
    implicit pertaxContext =>

      val addressType = mapAddressType(typ)

      addressJourneyEnforcer { payeAccount => personDetails =>

        gettingCachedJourneyData(typ) { journeyData =>

          ensuringSubmissionRequirments(typ, journeyData) {

            val originalAddressDto: Option[AddressDto] = journeyData.selectedAddressRecord.map(AddressDto.fromAddressRecord)

            journeyData.submittedAddressDto.fold(Future.successful(Redirect(routes.AddressController.personalDetails()))) { addressDto =>

              val address = addressDto.toAddress(addressType, journeyData.submittedStartDateDto.fold(LocalDate.now)(_.startDate))

              citizenDetailsService.updateAddress(payeAccount.nino, personDetails.etag, address) map {

                case UpdateAddressBadRequestResponse =>
                  BadRequest(views.html.error("global.error.BadRequest.title", Some("global.error.BadRequest.title"),
                    Some("global.error.BadRequest.message"), false))

                case UpdateAddressUnexpectedResponse(response) =>
                  InternalServerError(views.html.error("global.error.InternalServerError500.title",
                    Some("global.error.InternalServerError500.title"), Some("global.error.InternalServerError500.message"), false))

                case UpdateAddressErrorResponse(cause) =>
                  InternalServerError(views.html.error("global.error.InternalServerError500.title",
                    Some("global.error.InternalServerError500.title"), Some("global.error.InternalServerError500.message"), false))

                case UpdateAddressSuccessResponse =>
                  handleAddressChangeAuditing(originalAddressDto, addressDto, personDetails, addressType)
                  clearCache() //This clears ENTIRE session cache, no way to target individual keys
                  Ok(views.html.personaldetails.updateAddressConfirmation(typ, false, None))
                }
              }
          }
        }
      }
  }

  def showAddressAlreadyUpdated(typ: AddrType): Action[AnyContent] = VerifiedAction(baseBreadcrumb, activeTab = Some(ActiveTabYourAccount)) {
    implicit pertaxContext =>
      addressJourneyEnforcer { payeAccount => personDetails =>
        Future.successful(Ok(views.html.personaldetails.addressAlreadyUpdated()))
      }
  }
}

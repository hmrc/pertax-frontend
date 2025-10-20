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
import controllers.bindable.AddrType
import controllers.controllershelpers.{AddressJourneyCachingHelper, AddressSubmissionControllerHelper}
import error.ErrorRenderer
import models.dto.{AddressDto, InternationalAddressChoiceDto}
import models.AddressJourneyData
import play.api.Logging
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.CitizenDetailsService
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import views.html.InternalServerErrorView
import views.html.personaldetails.ReviewChangesView

import scala.concurrent.{ExecutionContext, Future}

class AddressSubmissionController @Inject() (
  authJourney: AuthJourney,
  cachingHelper: AddressJourneyCachingHelper,
  cc: MessagesControllerComponents,
  errorRenderer: ErrorRenderer,
  reviewChangesView: ReviewChangesView,
  featureFlagService: FeatureFlagService,
  citizenDetailsService: CitizenDetailsService,
  internalServerErrorView: InternalServerErrorView,
  addressSubmissionControllerHelper: AddressSubmissionControllerHelper
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

                if (
                  addressSubmissionControllerHelper.isSubmittedAddressStartDateValid(
                    journeyData.submittedStartDateDto,
                    typ
                  )
                ) {
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
                }
              } else {
                if (
                  addressSubmissionControllerHelper.isSubmittedAddressStartDateValid(
                    journeyData.submittedStartDateDto,
                    typ
                  )
                ) {
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
                }
              }
            case _                              => Future.successful(Redirect(routes.PersonalDetailsController.onPageLoad))
          }
        }
      }
    }

  def onSubmit(addressType: AddrType): Action[AnyContent] = authenticate.async { implicit request =>
    addressJourneyEnforcer { nino => personDetails =>
      (for {
        journeyData: AddressJourneyData <- EitherT[Future, UpstreamErrorResponse, AddressJourneyData](
                                             cachingHelper.gettingCachedJourneyData(addressType).map(Right(_))
                                           )
      } yield {
        val maybeSubmittedAddress = journeyData.submittedAddressDto

        (
          addressSubmissionControllerHelper.isSubmittedAddressStartDateValid(
            journeyData.submittedStartDateDto,
            addressType
          ),
          maybeSubmittedAddress
        ) match {
          case (true, Some(submittedAddress: AddressDto)) =>
            addressSubmissionControllerHelper.updateCitizenDetailsAddress(
              nino,
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

}

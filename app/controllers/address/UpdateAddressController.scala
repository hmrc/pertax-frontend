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
import controllers.bindable.{AddrType, PostalAddrType}
import controllers.controllershelpers.AddressJourneyCachingHelper
import error.ErrorRenderer
import models.dto.{AddressDto, DateDto}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import routePages.{SubmittedAddressPage, SubmittedStartDatePage}
import services.CitizenDetailsService
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import views.html.InternalServerErrorView
import views.html.personaldetails.UpdateAddressView

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class UpdateAddressController @Inject() (
  cachingHelper: AddressJourneyCachingHelper,
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  updateAddressView: UpdateAddressView,
  errorRenderer: ErrorRenderer,
  featureFlagService: FeatureFlagService,
  citizenDetailsService: CitizenDetailsService,
  internalServerErrorView: InternalServerErrorView
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends AddressController(
      authJourney,
      cc,
      featureFlagService,
      errorRenderer,
      citizenDetailsService,
      internalServerErrorView
    ) {

  def onPageLoad(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      cachingHelper.gettingCachedJourneyData[Result](typ) { journeyData =>
        val showEnterAddressHeader = journeyData.selectedAddressRecord.isEmpty

        addressJourneyEnforcer { _ => _ =>
          val addressForm = journeyData.getAddressToDisplay.fold(AddressDto.ukForm)(AddressDto.ukForm.fill)
          cachingHelper.enforceDisplayAddressPageVisited(
            Ok(
              updateAddressView(
                addressForm.discardingErrors,
                typ,
                showEnterAddressHeader
              )
            )
          )
        }
      }
    }

  def onSubmit(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      cachingHelper.gettingCachedJourneyData[Result](typ) { journeyData =>
        val showEnterAddressHeader = journeyData.selectedAddressRecord.isEmpty
        addressJourneyEnforcer { _ => personDetails =>
          AddressDto.ukForm
            .bindFromRequest()
            .fold(
              formWithErrors =>
                Future.successful(
                  BadRequest(
                    updateAddressView(
                      formWithErrors,
                      typ,
                      showEnterAddressHeader
                    )
                  )
                ),
              addressDto =>
                cachingHelper.addToCache(SubmittedAddressPage(typ), addressDto) flatMap { _ =>
                  val postCodeHasChanged = !addressDto.postcode
                    .getOrElse("")
                    .replace(" ", "")
                    .equalsIgnoreCase(personDetails.address.flatMap(_.postcode).getOrElse("").replace(" ", ""))
                  (typ, postCodeHasChanged) match {
                    case (PostalAddrType, _) =>
                      cacheStartDate(
                        typ,
                        Redirect(routes.AddressSubmissionController.onPageLoad(typ))
                      )
                    case (_, false)          =>
                      cacheStartDate(
                        typ,
                        Redirect(routes.AddressSubmissionController.onPageLoad(typ))
                      )
                    case (_, true)           =>
                      Future.successful(Redirect(routes.StartDateController.onPageLoad(typ)))
                  }
                }
            )
        }
      }
    }

  private def cacheStartDate(typ: AddrType, redirect: Result)(implicit request: UserRequest[_]): Future[Result] =
    cachingHelper.addToCache(SubmittedStartDatePage(typ), DateDto(LocalDate.now())) map (_ => redirect)
}

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
import controllers.bindable.PostalAddrType
import controllers.controllershelpers.AddressJourneyCachingHelper
import error.ErrorRenderer
import models.dto.InternationalAddressChoiceDto
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import routePages.SubmittedInternationalAddressChoicePage
import services.CitizenDetailsService
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import views.html.InternalServerErrorView
import views.html.personaldetails.InternationalAddressChoiceView

import scala.concurrent.{ExecutionContext, Future}

class PostalDoYouLiveInTheUKController @Inject() (
  cachingHelper: AddressJourneyCachingHelper,
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  errorRenderer: ErrorRenderer,
  internationalAddressChoiceView: InternationalAddressChoiceView,
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

  def onPageLoad: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        cachingHelper.enforceDisplayAddressPageVisited(
          Ok(
            internationalAddressChoiceView(
              InternationalAddressChoiceDto.form("error.postal_address_uk_select"),
              PostalAddrType
            )
          )
        )
      }
    }

  def onSubmit: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        InternationalAddressChoiceDto
          .form("error.postal_address_uk_select")
          .bindFromRequest()
          .fold(
            formWithErrors =>
              Future.successful(BadRequest(internationalAddressChoiceView(formWithErrors, PostalAddrType))),
            internationalAddressChoiceDto =>
              cachingHelper.addToCache(SubmittedInternationalAddressChoicePage, internationalAddressChoiceDto) map {
                _ =>
                  if (InternationalAddressChoiceDto.isUk(Some(internationalAddressChoiceDto))) {
                    Redirect(routes.PostcodeLookupController.onPageLoad(PostalAddrType))
                  } else {
                    if (configDecorator.updateInternationalAddressInPta) {
                      Redirect(routes.UpdateInternationalAddressController.onPageLoad(PostalAddrType))
                    } else {
                      Redirect(routes.AddressErrorController.cannotUseThisService(PostalAddrType))
                    }
                  }
              }
          )

      }
    }
}

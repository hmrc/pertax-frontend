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
import config.{BannerTcsServiceClosure, ConfigDecorator}
import controllers.auth.AuthJourney
import controllers.bindable.AddrType
import controllers.controllershelpers.AddressJourneyCachingHelper
import error.ErrorRenderer
import models.admin.AddressTaxCreditsBrokerCallToggle
import models.dto.TaxCreditsChoiceDto
import play.api.Logging
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import repositories.EditAddressLockRepository
import routePages.TaxCreditsChoicePage
import services.{CitizenDetailsService, TaxCreditsService}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import views.html.InternalServerErrorView
import views.html.personaldetails.TaxCreditsChoiceView

import java.time.ZonedDateTime
import scala.concurrent.{ExecutionContext, Future}

class TaxCreditsChoiceController @Inject() (
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  cachingHelper: AddressJourneyCachingHelper,
  editAddressLockRepository: EditAddressLockRepository,
  errorRenderer: ErrorRenderer,
  taxCreditsService: TaxCreditsService,
  featureFlagService: FeatureFlagService,
  citizenDetailsService: CitizenDetailsService,
  internalServerErrorView: InternalServerErrorView,
  taxCreditsChoiceView: TaxCreditsChoiceView
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
  def onPageLoad: Action[AnyContent] = authenticate.async { implicit request =>
    addressJourneyEnforcer { nino => _ =>
      def taxCreditsChecks: Future[Result] =
        featureFlagService
          .get(AddressTaxCreditsBrokerCallToggle)
          .flatMap { toggle =>
            if (toggle.isEnabled) {
              taxCreditsService
                .isAddressChangeInPTA(nino)
                .fold(
                  _ => Ok(taxCreditsChoiceView(TaxCreditsChoiceDto.form)),
                  maybeChangeInPTA =>
                    maybeChangeInPTA.fold {
                      Ok(taxCreditsChoiceView(TaxCreditsChoiceDto.form))
                    } { isAddressChangeInPTA =>
                      if (isAddressChangeInPTA) {
                        cachingHelper.addToCache(TaxCreditsChoicePage, TaxCreditsChoiceDto(false))
                        Redirect(routes.DoYouLiveInTheUKController.onPageLoad)
                        // TODO: If start change of address page experiment is successful replace above line with below
                        //Redirect(routes.StartChangeOfAddressController.onPageLoad(ResidentialAddrType))
                      } else {
                        cachingHelper.addToCache(TaxCreditsChoicePage, TaxCreditsChoiceDto(true))
                        Redirect(controllers.routes.InterstitialController.displayTaxCreditsInterstitial)
                      }
                    }
                )
            } else {
              Future.successful(
                Ok(taxCreditsChoiceView(TaxCreditsChoiceDto.form))
              )
            }
          }
          .flatMap(cachingHelper.enforceDisplayAddressPageVisited)

      configDecorator.featureBannerTcsServiceClosure match {
        case BannerTcsServiceClosure.Enabled
            if ZonedDateTime.now.compareTo(configDecorator.tcsFrontendEndDateTime) > 0 =>
          Future.successful(Redirect(controllers.address.routes.DoYouLiveInTheUKController.onPageLoad))
        case _ => taxCreditsChecks
      }
    }
  }

  def onSubmit: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { nino => _ =>
        TaxCreditsChoiceDto.form
          .bindFromRequest()
          .fold(
            formWithErrors =>
              Future
                .successful(BadRequest(taxCreditsChoiceView(formWithErrors))),
            taxCreditsChoiceDto =>
              cachingHelper.addToCache(TaxCreditsChoicePage, taxCreditsChoiceDto) map { _ =>
                if (taxCreditsChoiceDto.hasTaxCredits) {
                  editAddressLockRepository
                    .insert(
                      nino.withoutSuffix,
                      AddrType.apply("residential").get
                    )
                    .map {
                      case true => logger.warn("Address locked for tcs users")
                      case _    =>
                        logger.error(s"Could not insert address lock for user $request.nino.get.withoutSuffix")
                    }
                  Redirect(controllers.routes.InterstitialController.displayTaxCreditsInterstitial)
                } else {
                  Redirect(routes.DoYouLiveInTheUKController.onPageLoad)
                  // TODO: If start change of address page experiment is successful replace above line with below
                  //Redirect(routes.StartChangeOfAddressController.onPageLoad(ResidentialAddrType))
                }
              }
          )
      }
    }
}

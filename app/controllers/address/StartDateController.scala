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
import controllers.bindable.{AddrType, PostalAddrType, ResidentialAddrType}
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.dto.DateDto
import models.{Address, SubmittedStartDateId}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.language.LanguageUtils
import views.html.InternalServerErrorView
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.{CannotUpdateAddressEarlyDateView, CannotUpdateAddressFutureDateView, EnterStartDateView}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class StartDateController @Inject() (
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  cachingHelper: AddressJourneyCachingHelper,
  languageUtils: LanguageUtils,
  enterStartDateView: EnterStartDateView,
  cannotUpdateAddressEarlyDateView: CannotUpdateAddressEarlyDateView,
  cannotUpdateAddressFutureDateView: CannotUpdateAddressFutureDateView,
  displayAddressInterstitialView: DisplayAddressInterstitialView,
  featureFlagService: FeatureFlagService,
  internalServerErrorView: InternalServerErrorView
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends AddressController(
      authJourney,
      cc,
      displayAddressInterstitialView,
      featureFlagService,
      internalServerErrorView
    ) {

  def onPageLoad(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        nonPostalJourneyEnforcer(typ) {
          cachingHelper.gettingCachedJourneyData(typ) { journeyData =>
            val newPostcode = journeyData.submittedAddressDto.map(_.postcode).getOrElse("").toString
            val oldPostcode = personDetails.address.flatMap(add => add.postcode).getOrElse("")
            journeyData.submittedAddressDto map { _ =>
              val postcodesMatch =
                if (newPostcode.replace(" ", "").equalsIgnoreCase(oldPostcode.replace(" ", ""))) {
                  journeyData.submittedStartDateDto.fold(dateDtoForm)(dateDtoForm.fill)
                } else {
                  dateDtoForm
                }

              Future.successful(Ok(enterStartDateView(postcodesMatch, typ)))
            } getOrElse {
              Future.successful(Redirect(routes.PersonalDetailsController.onPageLoad))
            }
          }
        }
      }
    }

  def onSubmit(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        nonPostalJourneyEnforcer(typ) {
          dateDtoForm
            .bindFromRequest()
            .fold(
              formWithErrors => Future.successful(BadRequest(enterStartDateView(formWithErrors, typ))),
              dateDto =>
                cachingHelper.gettingCachedJourneyData(typ) { cache =>
                  val proposedStartDate = dateDto.startDate
                  val p85Enabled        = cache.submittedInternationalAddressChoiceDto.exists(!_.value)

                  personDetails.address match {
                    case Some(Address(_, _, _, _, _, _, _, Some(currentStartDate), _, _, _)) =>
                      if (!currentStartDate.isBefore(proposedStartDate)) {
                        Future.successful(
                          BadRequest(
                            cannotUpdateAddressEarlyDateView(
                              typ,
                              languageUtils.Dates.formatDate(proposedStartDate),
                              p85Enabled
                            )
                          )
                        )
                      } else if (proposedStartDate.isAfter(LocalDate.now())) {
                        Future.successful(
                          BadRequest(
                            cannotUpdateAddressFutureDateView(
                              typ,
                              languageUtils.Dates.formatDate(proposedStartDate),
                              p85Enabled
                            )
                          )
                        )
                      } else {
                        cachingHelper.addToCache(SubmittedStartDateId(typ), dateDto) map { cache =>
                          Redirect(routes.AddressSubmissionController.onPageLoad(typ))
                        }
                      }
                    case _                                                                   => Future.successful(Redirect(routes.AddressSubmissionController.onPageLoad(typ)))
                  }
                }
            )
        }
      }
    }

  private def dateDtoForm: Form[DateDto] = DateDto.form(configDecorator.currentLocalDate)

  private def nonPostalJourneyEnforcer(typ: AddrType)(block: => Future[Result]): Future[Result] =
    typ match {
      case ResidentialAddrType => block
      case PostalAddrType      =>
        Future.successful(Redirect(controllers.address.routes.UpdateAddressController.onPageLoad(typ)))
    }
}

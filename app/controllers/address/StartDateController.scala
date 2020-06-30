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
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.bindable.{AddrType, PostalAddrType, ResidentialAddrType}
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.dto.DateDto
import models.{Address, SubmittedStartDateId}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.renderer.TemplateRenderer
import util.{LanguageHelper, LocalPartialRetriever}
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.{CannotUpdateAddressView, EnterStartDateView}

import scala.concurrent.{ExecutionContext, Future}

class StartDateController @Inject()(
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction,
  cc: MessagesControllerComponents,
  cachingHelper: AddressJourneyCachingHelper,
  enterStartDateView: EnterStartDateView,
  cannotUpdateAddressView: CannotUpdateAddressView,
  displayAddressInterstitialView: DisplayAddressInterstitialView)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends AddressController(authJourney, withActiveTabAction, cc, displayAddressInterstitialView) {

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
              Future.successful(Redirect(routes.PersonalDetailsController.onPageLoad()))
            }
          }
        }
      }
    }

  def onSubmit(typ: AddrType): Action[AnyContent] =
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
                      if (!currentStartDate.isBefore(proposedStartDate)) {
                        BadRequest(
                          cannotUpdateAddressView(typ, LanguageHelper.langUtils.Dates.formatDate(proposedStartDate)))
                      } else {
                        Redirect(routes.AddressSubmissionController.onPageLoad(typ))
                      }
                    case _ => Redirect(routes.AddressSubmissionController.onPageLoad(typ))
                  }
              }
            }
          )
        }
      }
    }

  private def dateDtoForm: Form[DateDto] = DateDto.form(configDecorator.currentLocalDate)

  private def nonPostalJourneyEnforcer(typ: AddrType)(block: => Future[Result]): Future[Result] =
    typ match {
      case _: ResidentialAddrType => block
      case PostalAddrType =>
        Future.successful(Redirect(controllers.address.routes.UpdateAddressController.onPageLoad(typ)))
    }
}

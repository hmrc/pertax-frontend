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
import controllers.address
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.bindable.{AddrType, PostalAddrType, ResidentialAddrType}
import models.Address
import models.dto.DateDto
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import services.LocalSessionCache
import uk.gov.hmrc.renderer.TemplateRenderer
import util.{LanguageHelper, LocalPartialRetriever}

import scala.concurrent.{ExecutionContext, Future}

class StartDateController @Inject()(
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
        nonPostalJourneyEnforcer(typ) {
          gettingCachedJourneyData(typ) { journeyData =>
            val newPostcode = journeyData.submittedAddressDto.map(_.postcode).getOrElse("").toString
            val oldPostcode = personDetails.address.flatMap(add => add.postcode).getOrElse("")
            journeyData.submittedAddressDto map { _ =>
              Future.successful(Ok(views.html.personaldetails.enterStartDate(
                if (newPostcode.replace(" ", "").equalsIgnoreCase(oldPostcode.replace(" ", "")))
                  journeyData.submittedStartDateDto.fold(dateDtoForm)(dateDtoForm.fill)
                else dateDtoForm,
                typ
              )))
            } getOrElse {
              Future.successful(Redirect(controllers.routes.AddressController.personalDetails()))
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
              Future.successful(BadRequest(views.html.personaldetails.enterStartDate(formWithErrors, typ)))
            },
            dateDto => {
              addToCache(SubmittedStartDateId(typ), dateDto) map {
                _ =>
                  val proposedStartDate = dateDto.startDate

                  personDetails.address match {
                    case Some(Address(_, _, _, _, _, _, _, Some(currentStartDate), _, _))
                        if !currentStartDate.isBefore(proposedStartDate) =>
                      BadRequest(
                        views.html.personaldetails
                          .cannotUpdateAddress(typ, LanguageHelper.langUtils.Dates.formatDate(proposedStartDate)))
                    case _ => Redirect(routes.AddressSubmissionController.onPageLoad(typ))
                  }
              }
            }
          )
        }
      }
    }

  private val dateDtoForm: Form[DateDto] = DateDto.form(configDecorator.currentLocalDate)

  private def nonPostalJourneyEnforcer(typ: AddrType)(block: => Future[Result]): Future[Result] =
    typ match {
      case _: ResidentialAddrType => block
      case PostalAddrType         => Future.successful(Redirect(address.routes.UpdateAddressController.onPageLoad(typ)))
    }
}

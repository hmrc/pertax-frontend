/*
 * Copyright 2025 HM Revenue & Customs
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
import error.ErrorRenderer
import models.dto.DateDto
import models.Address
import models.dto.InternationalAddressChoiceDto
import models.dto.InternationalAddressChoiceDto.OutsideUK
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import routePages.SubmittedStartDatePage
import services.CitizenDetailsService
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.language.LanguageUtils
import views.html.InternalServerErrorView
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
  featureFlagService: FeatureFlagService,
  citizenDetailsService: CitizenDetailsService,
  errorRenderer: ErrorRenderer,
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

  private def dateDtoForm: Form[DateDto] = DateDto.form(configDecorator.currentLocalDate)

  private def normPostcode(p: Option[String]): String =
    p.getOrElse("").toUpperCase.replaceAll("\\s+", "")

  private def normCountry(c: Option[String]): String =
    c.getOrElse("").trim.toUpperCase.replaceAll("\\s+", "")

  private def normCountryFromChoice(choice: Option[InternationalAddressChoiceDto]): String =
    normCountry(choice.map(_.toString))

  private def isScotland(cNorm: String): Boolean =
    cNorm == "SCOTLAND"

  private def isCrossBorderScotland(oldCN: String, newCN: String): Boolean =
    isScotland(oldCN) ^ isScotland(newCN)

  def onPageLoad(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        nonPostalJourneyEnforcer(typ) {
          cachingHelper.gettingCachedJourneyData(typ) { journeyData =>
            journeyData.submittedAddressDto map { newAddrDto =>
              val newPc      = normPostcode(newAddrDto.postcode)
              val oldPc      = normPostcode(personDetails.address.flatMap(_.postcode))
              val newCountry = normCountryFromChoice(journeyData.submittedInternationalAddressChoiceDto)
              val oldCountry = normCountry(personDetails.address.flatMap(_.country))

              val prefill = newPc.nonEmpty && newPc == oldPc && newCountry == oldCountry

              val formToShow =
                if (prefill) {
                  journeyData.submittedStartDateDto.fold(dateDtoForm)(dateDtoForm.fill)
                }
                else {
                  dateDtoForm
                }

              Future.successful(Ok(enterStartDateView(formToShow, typ)))
            } getOrElse
              Future.successful(Redirect(routes.PersonalDetailsController.onPageLoad))
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
                  val proposed   = dateDto.startDate
                  val p85Enabled = cache.submittedInternationalAddressChoiceDto.exists(_ == OutsideUK)

                  if (proposed.isAfter(LocalDate.now())) {
                    Future.successful(
                      BadRequest(
                        cannotUpdateAddressFutureDateView(
                          typ,
                          languageUtils.Dates.formatDate(proposed),
                          p85Enabled
                        )
                      )
                    )
                  } else {
                    val newCountryNorm = normCountryFromChoice(cache.submittedInternationalAddressChoiceDto)
                    val oldCountryNorm = normCountry(personDetails.address.flatMap(_.country))
                    val crossBorder    = isCrossBorderScotland(oldCountryNorm, newCountryNorm)

                    personDetails.address match {
                      case Some(Address(_, _, _, _, _, _, _, Some(currentStart), _, _, _)) =>
                        if (p85Enabled || crossBorder) {
                          if (!currentStart.isBefore(proposed)) {
                            Future.successful(
                              BadRequest(
                                cannotUpdateAddressEarlyDateView(
                                  typ,
                                  languageUtils.Dates.formatDate(proposed),
                                  p85Enabled
                                )
                              )
                            )
                          } else {
                            cachingHelper
                              .addToCache(SubmittedStartDatePage(typ), dateDto)
                              .map(_ => Redirect(routes.AddressSubmissionController.onPageLoad(typ)))
                          }
                        } else {
                          val toPersist =
                            if (!proposed.isAfter(currentStart)) {
                              DateDto(LocalDate.now())
                            }
                            else {
                              dateDto
                            }

                          cachingHelper
                            .addToCache(SubmittedStartDatePage(typ), toPersist)
                            .map(_ => Redirect(routes.AddressSubmissionController.onPageLoad(typ)))
                        }

                      case _ =>
                        cachingHelper
                          .addToCache(SubmittedStartDatePage(typ), dateDto)
                          .map(_ => Redirect(routes.AddressSubmissionController.onPageLoad(typ)))
                    }
                  }
                }
            )
        }
      }
    }

  private def nonPostalJourneyEnforcer(typ: AddrType)(block: => Future[Result]): Future[Result] =
    typ match {
      case ResidentialAddrType => block
      case PostalAddrType      =>
        Future.successful(Redirect(controllers.address.routes.UpdateAddressController.onPageLoad(typ)))
    }
}

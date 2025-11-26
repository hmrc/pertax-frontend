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
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import routePages.SubmittedStartDatePage
import services.{AddressCountryService, CitizenDetailsService, NormalizationUtils, StartDateDecisionService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.play.language.LanguageUtils
import views.html.InternalServerErrorView
import views.html.personaldetails.{CannotUpdateAddressEarlyDateView, CannotUpdateAddressFutureDateView, EnterStartDateView}

import java.time.LocalDate
import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
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
  internalServerErrorView: InternalServerErrorView,
  startDateDecisionService: StartDateDecisionService,
  addressCountryService: AddressCountryService,
  normalizationUtils: NormalizationUtils
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends AddressController(
      authJourney,
      cc,
      featureFlagService,
      errorRenderer,
      citizenDetailsService,
      internalServerErrorView
    ) {

  private def dateDtoForm: Form[DateDto] =
    DateDto.form(configDecorator.currentLocalDate)

  def onPageLoad(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        nonPostalJourneyEnforcer(typ) {
          cachingHelper.gettingCachedJourneyData(typ) { journeyData =>
            journeyData.submittedAddressDto match {
              case Some(_) =>
                val submittedPostcode = journeyData.submittedAddressDto.flatMap(_.postcode)
                val existingPostcode  = personDetails.address.flatMap(_.postcode)

                val formToShow =
                  if (normalizationUtils.postcodesMatch(submittedPostcode, existingPostcode))
                    journeyData.submittedStartDateDto.fold(dateDtoForm)(dateDtoForm.fill)
                  else
                    dateDtoForm

                Future.successful(Ok(enterStartDateView(formToShow, typ)))

              case None =>
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
                  val proposedStartDate: LocalDate         = dateDto.startDate
                  val recordedStartDate: Option[LocalDate] =
                    personDetails.address.flatMap(_.startDate)
                  val today: LocalDate                     = LocalDate.now()

                  val currentCountryCodeOpt: Option[String] =
                    personDetails.address
                      .flatMap(_.country)
                      .map(countryName => normalizationUtils.normalizeCountryName(Some(countryName)))

                  val submittedPostcode: Option[String] =
                    cache.submittedAddressDto.flatMap(_.postcode)

                  implicit val hc: HeaderCarrier =
                    HeaderCarrierConverter.fromRequestAndSession(request, request.session)

                  addressCountryService
                    .deriveCountryForPostcode(submittedPostcode)
                    .flatMap { derivedCountryCodeOpt =>
                      val (p85Enabled, isCrossBorderMove): (Boolean, Boolean) =
                        derivedCountryCodeOpt match {
                          case Some(newCountryCode) =>
                            val newIsNonUk: Boolean =
                              normalizationUtils.isNonUkCountry(newCountryCode)

                            val currentIsNonUk: Boolean =
                              currentCountryCodeOpt.exists(normalizationUtils.isNonUkCountry)

                            val overseasMove: Boolean =
                              newIsNonUk || currentIsNonUk

                            val movedAcrossBorder: Boolean =
                              currentCountryCodeOpt match {
                                case Some(currentCountryCode) =>
                                  normalizationUtils.movedAcrossScottishBorder(
                                    currentCountryCode,
                                    newCountryCode
                                  )
                                case None                     =>
                                  true
                              }

                            (overseasMove, movedAcrossBorder)

                          case None =>
                            (false, true)
                        }

                      startDateDecisionService
                        .determineStartDate(
                          requestedDate = proposedStartDate,
                          recordedStartDate = recordedStartDate,
                          today = today,
                          overseasMove = p85Enabled,
                          scotlandBorderChange = isCrossBorderMove
                        )
                        .fold(
                          {
                            case StartDateDecisionService.FutureDateError =>
                              Future.successful(
                                BadRequest(
                                  cannotUpdateAddressFutureDateView(
                                    typ,
                                    languageUtils.Dates.formatDate(proposedStartDate),
                                    p85Enabled
                                  )
                                )
                              )

                            case StartDateDecisionService.EarlyDateError =>
                              Future.successful(
                                BadRequest(
                                  cannotUpdateAddressEarlyDateView(
                                    typ,
                                    languageUtils.Dates.formatDate(proposedStartDate),
                                    p85Enabled
                                  )
                                )
                              )
                          },
                          dateToPersist =>
                            cachingHelper
                              .addToCache(SubmittedStartDatePage(typ), DateDto(dateToPersist))
                              .map(_ => Redirect(routes.AddressSubmissionController.onPageLoad(typ)))
                        )
                    }
                    .recoverWith { case _ =>
                      errorRenderer.futureError(INTERNAL_SERVER_ERROR)
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

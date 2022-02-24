/*
 * Copyright 2022 HM Revenue & Customs
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
import connectors.{CitizenDetailsConnector, UpdateAddressSuccessResponse}
import controllers.PertaxBaseController
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction}
import error.{ErrorRenderer, GenericErrors}
import models.dto.RlsAddressConfirmDto
import models.{AddressJourneyTTLModel, PersonDetails}
import org.joda.time.LocalDate
import play.api.Logging
import play.api.mvc._
import repositories.EditAddressLockRepository
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.renderer.{ActiveTabYourProfile, TemplateRenderer}
import viewmodels.PersonalDetailsViewModel
import views.html.InternalServerErrorView
import views.html.personaldetails.{RlsAddressSubmittedView, RlsConfirmYourAddressView}

import scala.concurrent.{ExecutionContext, Future}

class RlsConfirmAddressController @Inject() (
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  val editAddressLockRepository: EditAddressLockRepository,
  withActiveTabAction: WithActiveTabAction,
  personalDetailsViewModel: PersonalDetailsViewModel,
  citizenDetailsConnector: CitizenDetailsConnector,
  errorRenderer: ErrorRenderer,
  internalServerErrorView: InternalServerErrorView,
  genericErrors: GenericErrors,
  rlsConfirmYourAddressView: RlsConfirmYourAddressView,
  rlsAddressSubmittedView: RlsAddressSubmittedView
)(implicit
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext
) extends PertaxBaseController(cc) with Logging {

  private val authenticate: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails andThen withActiveTabAction
      .addActiveTab(ActiveTabYourProfile)

  def onPageLoad(isMainAddress: Boolean): Action[AnyContent] = authenticate.async { implicit request =>
    for {
      addressModel <- request.nino
                        .map { nino =>
                          editAddressLockRepository.get(nino.withoutSuffix)
                        }
                        .getOrElse(
                          Future.successful(List[AddressJourneyTTLModel]())
                        )
    } yield {
      val row =
        if (isMainAddress) {
          request.personDetails
            .flatMap(personalDetailsViewModel.getMainAddress(_, addressModel.map(y => y.editedAddress)))
        } else {
          request.personDetails
            .flatMap(personalDetailsViewModel.getPostalAddress(_, addressModel.map(y => y.editedAddress)))
        }

      val addressDetails = personalDetailsViewModel.getAddressRow(addressModel)

      Ok(rlsConfirmYourAddressView(row, addressDetails, isMainAddress, RlsAddressConfirmDto.form))
    }
  }

  def onSubmit: Action[AnyContent] =
    authenticate.async { implicit request =>
      RlsAddressConfirmDto.form.bindFromRequest.value
        .map { isMainAddress =>
          addressJourneyEnforcer { nino => personDetails =>
            citizenDetailsConnector.getEtag(nino.nino) flatMap {
              case None =>
                logger.error("Failed to retrieve Etag from citizen-details")
                errorRenderer.futureError(INTERNAL_SERVER_ERROR)
              case Some(version) =>
                val addressToConfirm =
                  if (isMainAddress.isMainAddress)
                    personDetails.address
                  else
                    personDetails.correspondenceAddress

                addressToConfirm.fold(
                  Future.successful(genericErrors.badRequest)
                )(address =>
                  citizenDetailsConnector
                    .updateAddress(
                      nino,
                      version.etag,
                      address.copy(startDate = Some(LocalDate.now()), endDate = None)
                    )
                    .map {
                      case UpdateAddressSuccessResponse => Ok(rlsAddressSubmittedView())
                      case _                            => InternalServerError(internalServerErrorView())
                    }
                )
            }
          }
        }
        .getOrElse(Future.successful(genericErrors.internalServerError))
    }

  private def addressJourneyEnforcer(
    block: Nino => PersonDetails => Future[Result]
  )(implicit request: UserRequest[_]): Future[Result] =
    (for {
      payeAccount   <- request.nino
      personDetails <- request.personDetails
    } yield {
      block(payeAccount)(personDetails)
    }).getOrElse {
      Future.successful {
        genericErrors.internalServerError
      }
    }
}

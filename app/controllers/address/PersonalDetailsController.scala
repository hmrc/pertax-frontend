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
import controllers.controllershelpers.{AddressJourneyCachingHelper, PersonalDetailsCardGenerator, RlsInterruptHelper}
import models.{AddressJourneyTTLModel, AddressPageVisitedDtoId}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.EditAddressLockRepository
import services.AgentClientAuthorisationService
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import util.AuditServiceTools.buildPersonDetailsEvent
import viewmodels.PersonalDetailsViewModel
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.PersonalDetailsView

import scala.concurrent.{ExecutionContext, Future}

class PersonalDetailsController @Inject() (
  val personalDetailsCardGenerator: PersonalDetailsCardGenerator,
  val personalDetailsViewModel: PersonalDetailsViewModel,
  val editAddressLockRepository: EditAddressLockRepository,
  authJourney: AuthJourney,
  cachingHelper: AddressJourneyCachingHelper,
  auditConnector: AuditConnector,
  rlsInterruptHelper: RlsInterruptHelper,
  agentClientAuthorisationService: AgentClientAuthorisationService,
  cc: MessagesControllerComponents,
  displayAddressInterstitialView: DisplayAddressInterstitialView,
  personalDetailsView: PersonalDetailsView
)(implicit
  configDecorator: ConfigDecorator,
  ec: ExecutionContext
) extends AddressController(authJourney, cc, displayAddressInterstitialView) {

  def redirectToYourProfile: Action[AnyContent] = authenticate.async { _ =>
    Future.successful(Redirect(controllers.address.routes.PersonalDetailsController.onPageLoad, MOVED_PERMANENTLY))
  }

  def onPageLoad: Action[AnyContent] =
    authenticate.async { implicit request =>
      import models.dto.AddressPageVisitedDto

      rlsInterruptHelper.enforceByRlsStatus(for {
        agentClientStatus <- agentClientAuthorisationService.getAgentClientStatus
        addressModel      <- request.nino
                               .map { nino =>
                                 editAddressLockRepository.get(nino.withoutSuffix)
                               }
                               .getOrElse(
                                 Future.successful(List[AddressJourneyTTLModel]())
                               )

        _ <- request.personDetails
               .map { details =>
                 auditConnector.sendEvent(
                   buildPersonDetailsEvent(
                     "personalDetailsPageLinkClicked",
                     details
                   )
                 )
               }
               .getOrElse(Future.successful(()))
        _ <- cachingHelper
               .addToCache(AddressPageVisitedDtoId, AddressPageVisitedDto(true))

        paperLessPreference <- personalDetailsViewModel.getPaperlessSettingsRow

      } yield {
        val personalDetails      = personalDetailsViewModel
          .getPersonDetailsTable(request.nino)
        val addressDetails       = personalDetailsViewModel.getAddressRow(addressModel)
        val trustedHelpers       = personalDetailsViewModel.getTrustedHelpersRow
        val paperlessHelpers     = paperLessPreference
        val signinDetailsHelpers = personalDetailsViewModel.getSignInDetailsRow
        val manageTaxAgent       = if (agentClientStatus) personalDetailsViewModel.getManageTaxAgentsRow else None

        Ok(
          personalDetailsView(
            personalDetails,
            addressDetails,
            trustedHelpers,
            paperlessHelpers,
            signinDetailsHelpers,
            manageTaxAgent
          )
        )
      })
    }

}

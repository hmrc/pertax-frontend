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

package controllers.controllershelpers

import cats.data.OptionT
import cats.instances.future._
import com.google.inject.{ImplementedBy, Inject}
import controllers.PertaxBaseController
import controllers.auth.requests.UserRequest
import models.admin.RlsInterruptToggle
import play.api.Logging
import play.api.mvc.{MessagesControllerComponents, Result}
import repositories.EditAddressLockRepository
import services.CitizenDetailsService
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[RlsInterruptHelperImpl])
trait RlsInterruptHelper {
  def enforceByRlsStatus(
    block: => Future[Result]
  )(implicit
    request: UserRequest[_],
    ec: ExecutionContext
  ): Future[Result]
}

class RlsInterruptHelperImpl @Inject() (
  cc: MessagesControllerComponents,
  editAddressLockRepository: EditAddressLockRepository,
  featureFlagService: FeatureFlagService,
  citizenDetailsService: CitizenDetailsService
) extends PertaxBaseController(cc)
    with RlsInterruptHelper
    with Logging {

  def enforceByRlsStatus(
    block: => Future[Result]
  )(implicit
    request: UserRequest[_],
    ec: ExecutionContext
  ): Future[Result] =
    featureFlagService.get(RlsInterruptToggle).flatMap { featureFlag =>
      if (featureFlag.isEnabled) {
        logger.debug("Check for RLS interrupt")

        (for {
          personDetailsEither <- OptionT.liftF(
                                   citizenDetailsService
                                     .personDetails(request.helpeeNinoOrElse)
                                     .value
                                 )
          personDetails        = personDetailsEither.toOption.flatten

          addressesLock <- OptionT.liftF(
                             editAddressLockRepository.getAddressesLock(request.helpeeNinoOrElse.withoutSuffix)
                           )
        } yield {
          val mainRls   = personDetails.flatMap(_.address).exists(_.isRls)
          val postalRls = personDetails.flatMap(_.correspondenceAddress).exists(_.isRls)

          logger.info(s"Residential mongo lock: ${addressesLock.main}")
          logger.info(s"Correspondence mongo lock: ${addressesLock.postal}")
          logger.info(s"Residential address RLS: $mainRls")
          logger.info(s"Correspondence address RLS: $postalRls")

          if (mainRls && !addressesLock.main) {
            Future.successful(Redirect(controllers.routes.RlsController.rlsInterruptOnPageLoad))
          } else if (postalRls && !addressesLock.postal) {
            Future.successful(Redirect(controllers.routes.RlsController.rlsInterruptOnPageLoad))
          } else {
            block
          }
        }).getOrElse(block).flatten

      } else {
        logger.error("The RLS toggle is turned off")
        block
      }
    }
}

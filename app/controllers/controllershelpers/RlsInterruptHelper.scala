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

package controllers.controllershelpers

import cats.data.OptionT
import cats.instances.future._
import com.google.inject.Inject
import config.ConfigDecorator
import controllers.PertaxBaseController
import controllers.auth.requests.UserRequest
import play.api.Logging
import play.api.mvc.{MessagesControllerComponents, Result}
import repositories.EditAddressLockRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class RlsInterruptHelper @Inject() (
  cc: MessagesControllerComponents,
  editAddressLockRepository: EditAddressLockRepository
)(implicit ec: ExecutionContext)
    extends PertaxBaseController(cc) with Logging {

  def enforceByRlsStatus(
    block: => Future[Result]
  )(implicit
    request: UserRequest[_],
    hc: HeaderCarrier,
    ec: ExecutionContext,
    configDecorator: ConfigDecorator
  ): Future[Result] =
    if (configDecorator.rlsInterruptToggle) {
      logger.debug("Check for RLS interrupt")
      (for {
        personDetails <- OptionT.fromOption(request.personDetails)
        nino          <- OptionT.fromOption(request.nino)
        addressesLock <-
          OptionT.liftF(editAddressLockRepository.getAddressesLock(nino.withoutSuffix))
      } yield {
        logger.info("Residential mongo lock: " + addressesLock.main.toString)
        logger.info("Correspondence mongo lock: " + addressesLock.postal.toString)
        logger.info("Residential address rls: " + personDetails.address.exists(_.isRls))
        logger.info("Correspondence address rls: " + personDetails.correspondenceAddress.exists(_.isRls))

        if (personDetails.address.exists(_.isRls) && !addressesLock.main)
          Future.successful(Redirect(controllers.routes.RlsController.rlsInterruptOnPageLoad()))
        else if (personDetails.correspondenceAddress.exists(_.isRls) && !addressesLock.postal)
          Future.successful(Redirect(controllers.routes.RlsController.rlsInterruptOnPageLoad()))
        else
          block
      }).getOrElse(block).flatten
    } else {
      logger.error("The RLS toggle is turned off")
      block
    }
}

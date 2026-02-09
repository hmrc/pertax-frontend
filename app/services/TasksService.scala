/*
 * Copyright 2026 HM Revenue & Customs
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

package services

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.Execution.trampoline
import models.admin.ShowTaxCalcTileToggle
import controllers.auth.requests.UserRequest
import play.api.i18n.Messages
import services.partials.TaxCalcPartialService
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import viewmodels.{Task, TaskStatus}
import models.*
import play.api.Logging

import scala.concurrent.Future

class TasksService @Inject() (
  configDecorator: ConfigDecorator,
  taxCalcPartialService: TaxCalcPartialService,
  featureFlagService: FeatureFlagService
) extends Logging {

  def getListOfTasks(implicit request: UserRequest[?], messages: Messages): Future[Seq[Task]] = {
    val taxcalcF = getTaxCalcTasks(request.trustedHelper.isDefined)
    Future.sequence(Seq(taxcalcF)).map(_.flatten)
  }

  def getTaxCalcTasks(
    isTrustedHelper: Boolean
  )(implicit request: UserRequest[?], messages: Messages): Future[Seq[Task]] =
    if (isTrustedHelper) {
      Future.successful(Seq.empty)
    } else {
      featureFlagService.get(ShowTaxCalcTileToggle).flatMap { taxCalcTileFlag =>
        if (taxCalcTileFlag.isEnabled) {
          taxCalcPartialService.getTaxCalcPartial.map { listOfCards =>
            listOfCards.flatMap { card =>
              card.partialReconciliationStatus match {
                case Underpaid                =>
                  val amountRegexp =
                    "(?s).*(You owe|Mae arnoch)\\s*£([0-9.,]+).*".r // `underpayment.you_owe` message key on taxcalc-frontend
                  card.partialContent.body match {
                    case amountRegexp(_, amount) =>
                      Some(
                        Task(
                          messages(
                            "newLabel.you_owe_hmrc",
                            amount,
                            card.startTaxYear.toString,
                            (card.startTaxYear + 1).toString
                          ),
                          TaskStatus.Incomplete,
                          configDecorator.taxCalcHomePageUrl
                        )
                      )
                    case _                       =>
                      logger.info(s"No underpayment amount has been found")
                      None
                  }
                case Overpaid                 =>
                  val amountRegexp =
                    "(?s).*(HMRC owe you|Mae ar CThEM arian i chi)\\s*£([0-9.,]+).*".r // `overpayment.hmrc_owe_you` message key in taxcalc-frontend
                  card.partialContent.body match {
                    case amountRegexp(_, amount) =>
                      Some(
                        Task(
                          messages(
                            "newLabel.hmrc_owes_you_a_refund",
                            amount,
                            card.startTaxYear.toString,
                            (card.startTaxYear + 1).toString
                          ),
                          TaskStatus.Incomplete,
                          configDecorator.taxCalcHomePageUrl
                        )
                      )
                    case _                       => None
                  }
                case BalancedSA               => None
                case BalancedNoEmployment     => None
                case NoReconciliationStatus   => None
                case Balanced                 => None
                case OverpaidWithinTolerance  => None
                case UnderpaidWithinTolerance => None
              }
            }
          }
        } else {
          Future.successful(Seq.empty)
        }
      }
    }
}

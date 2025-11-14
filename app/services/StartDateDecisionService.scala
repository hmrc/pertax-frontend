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

package services

import services.StartDateDecisionService.{EarlyDateError, FutureDateError, StartDateError}

import java.time.LocalDate
import javax.inject.{Inject, Singleton}

@Singleton
class StartDateDecisionService @Inject() {

  def decide(
    proposed: LocalDate,
    currentStart: Option[LocalDate],
    today: LocalDate,
    p85Enabled: Boolean,
    crossBorder: Boolean
  ): Either[StartDateError, LocalDate] =

    if (proposed.isAfter(today)) {
      Left(FutureDateError)
    } else {
      currentStart match {
        case Some(cs) if p85Enabled || crossBorder =>
          if (!cs.isBefore(proposed)) Left(EarlyDateError) else Right(proposed)

        case Some(cs) =>
          if (!proposed.isAfter(cs)) Right(today) else Right(proposed)

        case None => Right(proposed)
      }
    }
}

object StartDateDecisionService {
  sealed trait StartDateError

  case object FutureDateError extends StartDateError

  case object EarlyDateError extends StartDateError
}

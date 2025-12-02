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

  def determineStartDate(
    requestedDate: LocalDate,
    recordedStartDate: Option[LocalDate],
    today: LocalDate,
    overseasMove: Boolean,
    scotlandBorderChange: Boolean
  ): Either[StartDateError, LocalDate] =

    if (requestedDate.isAfter(today)) {
      Left(FutureDateError)
    } else {
      recordedStartDate match {

        case Some(existingDate) if overseasMove || scotlandBorderChange =>
          if (!existingDate.isBefore(requestedDate)) Left(EarlyDateError)
          else Right(requestedDate)

        case Some(existingDate) =>
          if (!requestedDate.isAfter(existingDate)) Right(today)
          else Right(requestedDate)

        case None =>
          Right(requestedDate)
      }
    }
}

object StartDateDecisionService {
  sealed trait StartDateError
  case object FutureDateError extends StartDateError
  case object EarlyDateError extends StartDateError
}

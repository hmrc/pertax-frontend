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

package models

sealed trait BreathingSpaceIndicatorResponse
object BreathingSpaceIndicatorResponse {
  case object WithinPeriod extends BreathingSpaceIndicatorResponse // true status
  case object OutOfPeriod extends BreathingSpaceIndicatorResponse // false status
  case object NotFound extends BreathingSpaceIndicatorResponse // not found response
  case object StatusUnknown
      extends BreathingSpaceIndicatorResponse // all others including feature disabled and exceptions

  def fromBoolean(b: Boolean): BreathingSpaceIndicatorResponse =
    if (b) WithinPeriod else OutOfPeriod
}

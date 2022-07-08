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

package metrics

object MetricsEnumeration extends Enumeration {

  type MetricsEnumeration = Value
  val GET_AGENT_CLIENT_STATUS = Value
  val GET_SEISS_CLAIMS = Value
  val GET_UNREAD_MESSAGE_COUNT = Value
  val LOAD_PARTIAL = Value
  val GET_BREATHING_SPACE_INDICATOR = Value
}

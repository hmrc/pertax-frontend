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

import play.api.libs.json.Json

case class PaperlessStatus(name: String, category: String)

object PaperlessStatus {
  implicit val format = Json.format[PaperlessStatus]
}

case class PaperlessUrl(link: String, text: String)

object PaperlessUrl {
  implicit val format = Json.format[PaperlessUrl]
}

case class PaperlessResponse(status: PaperlessStatus, url: PaperlessUrl)

object PaperlessResponse {
  implicit val formats = Json.format[PaperlessResponse]
}

case class PaperlessMessages(responseText: String, linkText: String, hiddenText: Option[String])

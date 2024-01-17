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

package models

import play.api.libs.json.{JsString, JsSuccess, Json, Reads}
import play.twirl.api.HtmlFormat

case class SummaryCardPartial(partialName: String, partialContent: HtmlFormat.Appendable)

object SummaryCardPartial {
  /*
    implicit val htmlWrites: Writes[HtmlFormat.Appendable] = (html: HtmlFormat.Appendable) => {
    JsString(HtmlFormat.raw(html.toString()).toString())
  }

  implicit val writes: Writes[SummaryCardPartial] = Json.writes[SummaryCardPartial]
   */
  implicit val htmlReads: Reads[HtmlFormat.Appendable] = jsValue => {
    JsSuccess(HtmlFormat.escape(jsValue.as[JsString].value))
  }

  implicit val reads: Reads[SummaryCardPartial] = Json.reads[SummaryCardPartial]
}

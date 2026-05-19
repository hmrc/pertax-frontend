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

package viewmodels

import org.jsoup.Jsoup
import play.twirl.api.Html

import scala.jdk.CollectionConverters.*

final case class HMRCCardModel(content: Html) {
  require(
    HMRCCardModel.hasFocusableControl(content),
    "HMRCCardModel content must include at least one focusable control"
  )
}

object HMRCCardModel {
  private val FocusableControlSelector =
    "a[href], button:not([disabled]), input:not([disabled]):not([type=hidden]), select:not([disabled]), textarea:not([disabled]), [tabindex]"

  def hasFocusableControl(content: Html): Boolean =
    Jsoup
      .parseBodyFragment(content.body)
      .select(FocusableControlSelector)
      .asScala
      .exists(element => element.attr("tabindex") != "-1" && element.attr("aria-hidden") != "true")
}

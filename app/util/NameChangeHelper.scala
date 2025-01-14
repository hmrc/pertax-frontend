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

package util

import config.ConfigDecorator

object NameChangeHelper {
  def correctName(content: String, configDecorator: ConfigDecorator, messages: play.api.i18n.Messages): String =
    if (configDecorator.featureNameChangeMtdItSaToMtdIt) {
      content
    } else {
      val newName = messages("label.mtd_for_sa")
      val oldName = messages.lang.language match {
        case "cy" => "Troi Treth yn Ddigidol ar gyfer Hunanasesiad Treth Incwm"
        case _    => "Making Tax Digital for Income Tax Self Assessment"
      }
      content.replace(newName, oldName)
    }
}

/*
 * Copyright 2019 HM Revenue & Customs
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

import org.joda.time.LocalDate
import play.api.i18n.Messages
import util.LanguageHelper

case class SelfAssessmentPayment(date: LocalDate, referenceNumber: String, amount: Double) {

  def getDisplayDate()(implicit messages: Messages): String = {
    val dateWithLang = LanguageHelper.langUtils.Dates.formatDate(date)
    dateWithLang.substring(0, dateWithLang.lastIndexOf(" "))
  }

  def getDisplayAmount: String = f"£$amount%.2f"
}

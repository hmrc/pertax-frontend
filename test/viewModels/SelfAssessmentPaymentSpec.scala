/*
 * Copyright 2020 HM Revenue & Customs
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

package viewModels

import org.joda.time.LocalDate
import play.api.i18n.Messages
import viewmodels.SelfAssessmentPayment
import views.html.ViewSpec

class SelfAssessmentPaymentSpec extends ViewSpec {

  "SelfAssessmentPayment" when {

    "getDisplayDate is called" should {

      "display the date in the correct format in English" in {

        implicit val messagesApi: Messages = messages

        val result = SelfAssessmentPayment(new LocalDate("2019-04-05"), "test", 1.0).getDisplayDate

        result shouldBe "5 April"
      }

      "display the date in the correct format in Welsh" in {

        implicit val messagesApi: Messages = welshMessages

        val result = SelfAssessmentPayment(new LocalDate("2019-04-05"), "test", 1.0).getDisplayDate

        result shouldBe "5 Ebrill"
      }
    }

    "getDisplayAmount is called" should {

      "display the amount in the correct format" in {

        val result = SelfAssessmentPayment(new LocalDate("2019-04-20"), "test", 57.08).getDisplayAmount

        result shouldBe "£57.08"
      }
    }
  }
}

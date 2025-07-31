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

package models

import play.api.data.Forms.{mapping, optional, text}
import play.api.data.{Form, Mapping}
import play.api.i18n.Messages
import play.api.libs.json.{Json, OFormat}

final case class SelectSABPPPaymentModel(saBppWhatPaymentType: Option[String])

object SelectSABPPPaymentModel {
  def unapply(selectSABPPPaymentModel: SelectSABPPPaymentModel): Option[Option[String]] = Some(
    selectSABPPPaymentModel.saBppWhatPaymentType
  )
}

object SelectSABPPPaymentFormProvider {

  val saBppOverduePayment = "saBppOverduePayment"
  val saBppAdvancePayment = "saBppAdvancePayment"

  val answers: Seq[String] = Seq(saBppOverduePayment, saBppAdvancePayment)

  def inputOptions(implicit messages: Messages): Seq[(String, String)] = answers.map { paymentType =>
    paymentType -> messages(s"sa.message.selectSABPPPaymentType.$paymentType")
  }

  def form(implicit messages: Messages): Form[SelectSABPPPaymentModel] =
    Form[SelectSABPPPaymentModel](
      mapping(
        "saBppWhatPaymentType" -> answerFieldValidator
      )(SelectSABPPPaymentModel.apply)(SelectSABPPPaymentModel.unapply)
    )

  private def answerFieldValidator: Mapping[Option[String]] =
    optional(text).verifying(
      "sa.message.selectSABPPPaymentType.error.required",
      data => data.fold(false)(answers.contains)
    )

  implicit val formatsSelectSABPPPaymentModel: OFormat[SelectSABPPPaymentModel] = Json.format[SelectSABPPPaymentModel]
}

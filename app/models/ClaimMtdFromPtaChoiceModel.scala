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

package models

import play.api.data.Form
import play.api.data.Forms.{boolean, mapping, optional}
import play.api.libs.json.{Json, OFormat}

final case class ClaimMtdFromPtaChoiceModel(choice: Boolean)

object ClaimMtdFromPtaChoiceModel {
  def unapply(model: ClaimMtdFromPtaChoiceModel): Option[Boolean] =
    Some(model.choice)
}

object ClaimMtdFromPtaChoiceFormProvider {

  def form: Form[ClaimMtdFromPtaChoiceModel] =
    Form(
      mapping(
        "mtd-choice" -> optional(boolean)
          .verifying("label.mtdit.claim.error", _.isDefined)
          .transform[Boolean](_.getOrElse(false), Some(_))
      )(ClaimMtdFromPtaChoiceModel.apply)(ClaimMtdFromPtaChoiceModel.unapply)
    )

  implicit val formats: OFormat[ClaimMtdFromPtaChoiceModel] =
    Json.format[ClaimMtdFromPtaChoiceModel]
}

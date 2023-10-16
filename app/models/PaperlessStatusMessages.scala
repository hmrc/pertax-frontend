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

import play.api.libs.json.{JsError, JsResult, JsSuccess, JsValue, Reads}

sealed trait PaperlessMessagesStatus {
  val responseCode: String
  val responseText: String
  val linkText: String
  val hiddenText: Option[String]
  val link: String
}

object PaperlessMessagesStatus {
  private def getPaperlessMessage(key: String, link: String): JsResult[PaperlessMessagesStatus] = Map(
    PaperlessStatusNewCustomer().responseCode   -> JsSuccess(PaperlessStatusNewCustomer(link)),
    PaperlessStatusBounced().responseCode       -> JsSuccess(PaperlessStatusBounced(link)),
    PaperlessStatusUnverified().responseCode    -> JsSuccess(PaperlessStatusUnverified(link)),
    PaperlessStatusReopt().responseCode         -> JsSuccess(PaperlessStatusReopt(link)),
    PaperlessStatusReoptModified().responseCode -> JsSuccess(PaperlessStatusReoptModified(link)),
    PaperlessStatusOptOut().responseCode        -> JsSuccess(PaperlessStatusOptOut(link)),
    PaperlessStatusOptIn().responseCode         -> JsSuccess(PaperlessStatusOptIn(link)),
    PaperlessStatusNoEmail().responseCode       -> JsSuccess(PaperlessStatusNoEmail(link))
  ).getOrElse(key, JsError("Invalid response code for paperless status"))

  implicit val reads: Reads[PaperlessMessagesStatus] = new Reads[PaperlessMessagesStatus] {
    override def reads(json: JsValue): JsResult[PaperlessMessagesStatus] =
      getPaperlessMessage((json \ "status" \ "name").as[String], (json \ "url" \ "link").as[String])
  }
}

case class PaperlessStatusNewCustomer(link: String = "") extends PaperlessMessagesStatus {
  override val responseCode: String       = "NEW_CUSTOMER"
  override val responseText: String       = "label.paperless_new_response"
  override val linkText: String           = "label.paperless_new_link"
  override val hiddenText: Option[String] = Some("label.paperless_new_hidden")
}

case class PaperlessStatusBounced(link: String = "") extends PaperlessMessagesStatus {
  override val responseCode: String       = "BOUNCED_EMAIL"
  override val responseText: String       = "label.paperless_bounced_response"
  override val linkText: String           = "label.paperless_bounced_link"
  override val hiddenText: Option[String] = Some("label.paperless_bounced_hidden")
}

case class PaperlessStatusUnverified(link: String = "") extends PaperlessMessagesStatus {
  override val responseCode: String       = "EMAIL_NOT_VERIFIED"
  override val responseText: String       = "label.paperless_unverified_response"
  override val linkText: String           = "label.paperless_unverified_link"
  override val hiddenText: Option[String] = Some("label.paperless_unverified_hidden")
}

case class PaperlessStatusReopt(link: String = "") extends PaperlessMessagesStatus {
  override val responseCode: String       = "RE_OPT_IN"
  override val responseText: String       = "label.paperless_reopt_response"
  override val linkText: String           = "label.paperless_reopt_link"
  override val hiddenText: Option[String] = None
}

case class PaperlessStatusReoptModified(link: String = "") extends PaperlessMessagesStatus {
  override val responseCode: String       = "RE_OPT_IN_MODIFIED"
  override val responseText: String       = "label.paperless_reopt_modified_response"
  override val linkText: String           = "label.paperless_reopt_modified_link"
  override val hiddenText: Option[String] = None
}

case class PaperlessStatusOptOut(link: String = "") extends PaperlessMessagesStatus {
  override val responseCode: String       = "PAPER"
  override val responseText: String       = "label.paperless_opt_out_response"
  override val linkText: String           = "label.paperless_opt_out_link"
  override val hiddenText: Option[String] = Some("label.paperless_opt_out_hidden")
}

case class PaperlessStatusOptIn(link: String = "") extends PaperlessMessagesStatus {
  override val responseCode: String       = "ALRIGHT"
  override val responseText: String       = "label.paperless_opt_in_response"
  override val linkText: String           = "label.paperless_opt_in_link"
  override val hiddenText: Option[String] = Some("label.paperless_opt_in_hidden")
}

case class PaperlessStatusNoEmail(link: String = "") extends PaperlessMessagesStatus {
  override val responseCode: String       = "NO_EMAIL"
  override val responseText: String       = "label.paperless_no_email_response"
  override val linkText: String           = "label.paperless_no_email_link"
  override val hiddenText: Option[String] = Some("label.paperless_no_email_hidden")
}

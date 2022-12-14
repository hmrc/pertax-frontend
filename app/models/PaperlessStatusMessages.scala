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

sealed trait PaperlessMessages {
  val responseText: String
  val linkText: String
  val hiddenText: Option[String]
}

case object PaperlessStatusNewCustomer extends PaperlessMessages {
  override val responseText: String       = "label.paperless_new_response"
  override val linkText: String           = "label.paperless_new_link"
  override val hiddenText: Option[String] = Some("label.paperless_new_hidden")
}

case object PaperlessStatusBounced extends PaperlessMessages {
  override val responseText: String       = "label.paperless_bounced_response"
  override val linkText: String           = "label.paperless_bounced_link"
  override val hiddenText: Option[String] = Some("label.paperless_bounced_hidden")
}

case object PaperlessStatusUnverified extends PaperlessMessages {
  override val responseText: String       = "label.paperless_unverified_response"
  override val linkText: String           = "label.paperless_unverified_link"
  override val hiddenText: Option[String] = Some("label.paperless_unverified_hidden")
}

case object PaperlessStatusReopt extends PaperlessMessages {
  override val responseText: String       = "label.paperless_reopt_response"
  override val linkText: String           = "label.paperless_reopt_link"
  override val hiddenText: Option[String] = None
}

case object PaperlessStatusReoptModified extends PaperlessMessages {
  override val responseText: String       = "label.paperless_reopt_modified_response"
  override val linkText: String           = "label.paperless_reopt_modified_link"
  override val hiddenText: Option[String] = None
}

case object PaperlessStatusOptOut extends PaperlessMessages {
  override val responseText: String       = "label.paperless_opt_out_response"
  override val linkText: String           = "label.paperless_opt_out_link"
  override val hiddenText: Option[String] = Some("label.paperless_opt_out_hidden")
}

case object PaperlessStatusOptIn extends PaperlessMessages {
  override val responseText: String       = "label.paperless_opt_in_response"
  override val linkText: String           = "label.paperless_opt_in_link"
  override val hiddenText: Option[String] = Some("label.paperless_opt_in_hidden")
}

case object PaperlessStatusNoEmail extends PaperlessMessages {
  override val responseText: String       = "label.paperless_no_email_response"
  override val linkText: String           = "label.paperless_no_email_link"
  override val hiddenText: Option[String] = Some("label.paperless_no_email_hidden")
}

case object PaperlessStatusFailed extends PaperlessMessages {
  override val responseText: String       = "label.paperless_failed_response"
  override val linkText: String           = "label.paperless_failed_link"
  override val hiddenText: Option[String] = Some("label.paperless_failed_hidden")
}

object PaperlessStatuses {
  val status: Map[String, PaperlessMessages] = Map(
    "BOUNCED_EMAIL"      -> PaperlessStatusBounced,
    "NEW_CUSTOMER"       -> PaperlessStatusNewCustomer,
    "EMAIL_NOT_VERIFIED" -> PaperlessStatusUnverified,
    "RE_OPT_IN"          -> PaperlessStatusReopt,
    "RE_OPT_IN_MODIFIED" -> PaperlessStatusReoptModified,
    "PAPER"              -> PaperlessStatusOptOut,
    "ALRIGHT"            -> PaperlessStatusOptIn,
    "NO_EMAIL"           -> PaperlessStatusNoEmail
  )
}

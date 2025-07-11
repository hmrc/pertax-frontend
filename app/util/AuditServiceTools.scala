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

package util

import controllers.auth.requests.UserRequest
import models.{PersonDetails, SelfAssessmentUser}
import uk.gov.hmrc.http.{HeaderCarrier, HeaderNames}
import uk.gov.hmrc.play.audit.model.DataEvent

object AuditServiceTools {

  val auditSource = "pertax-frontend"

  def buildEvent(auditType: String, transactionName: String, detail: Map[String, Option[String]])(implicit
    hc: HeaderCarrier,
    request: UserRequest[_]
  ): DataEvent = {
    val customTags = Map(
      "clientIP"        -> hc.trueClientIp,
      "clientPort"      -> hc.trueClientPort,
      "path"            -> Some(request.path),
      "transactionName" -> Some(transactionName)
    )

    val standardAuditData: Map[String, String] = List(
      Some(("nino", request.authNino.nino)),
      request.saUserType match {
        case saUser: SelfAssessmentUser => Some(("saUtr", saUser.saUtr.utr))
        case _                          => None
      },
      Some(("credId", request.credentials.providerId)),
      hc.deviceID.map(id => ("deviceId", id)),
      request.cookies.get("mdtpdf").map(fingerprint => ("deviceFingerprint", fingerprint.value))
    ).flatten.toMap

    val customAuditData = detail.map(x => x._2.map((x._1, _))).flatten.filter(_._2 != "").toMap

    DataEvent(
      auditSource = auditSource,
      auditType = auditType,
      tags = hc
        .headers(HeaderNames.explicitlyIncludedHeaders)
        .toMap ++ customTags.map(x => x._2.map((x._1, _))).flatten.toMap,
      detail = standardAuditData ++ customAuditData
    )
  }

  def buildPersonDetailsEvent(auditType: String, personDetails: PersonDetails)(implicit
    hc: HeaderCarrier,
    request: UserRequest[_]
  ): DataEvent =
    buildEvent(
      auditType,
      "change_address",
      Map(
        "line1"             -> Some(personDetails.address.flatMap(_.line1).getOrElse(None).toString),
        "line2"             -> Some(personDetails.address.flatMap(_.line2).getOrElse(None).toString),
        "line3"             -> Some(personDetails.address.flatMap(_.line3).getOrElse(None).toString),
        "line4"             -> Some(personDetails.address.flatMap(_.line4).getOrElse(None).toString),
        "postcode"          -> Some(personDetails.address.flatMap(_.postcode).getOrElse(None).toString),
        "startDate"         -> Some(personDetails.address.flatMap(_.startDate).getOrElse(None).toString),
        "type"              -> Some(personDetails.address.flatMap(_.`type`).getOrElse(None).toString),
        "welshLanguageUnit" -> personDetails.correspondenceAddress.fold(Some("false"))(address =>
          Some(address.isWelshLanguageUnit.toString)
        )
      )
    )

  def buildAddressChangeEvent(auditType: String, personDetails: PersonDetails, isInternationalAddress: Boolean)(implicit
    hc: HeaderCarrier,
    request: UserRequest[_]
  ): DataEvent =
    buildEvent(
      auditType,
      "change_address",
      Map(
        "line1"                  -> Some(personDetails.address.flatMap(_.line1).getOrElse(None).toString),
        "line2"                  -> Some(personDetails.address.flatMap(_.line2).getOrElse(None).toString),
        "line3"                  -> Some(personDetails.address.flatMap(_.line3).getOrElse(None).toString),
        "line4"                  -> Some(personDetails.address.flatMap(_.line4).getOrElse(None).toString),
        "postcode"               -> Some(personDetails.address.flatMap(_.postcode).getOrElse(None).toString),
        "startDate"              -> Some(personDetails.address.flatMap(_.startDate).getOrElse(None).toString),
        "type"                   -> Some(personDetails.address.flatMap(_.`type`).getOrElse(None).toString),
        "welshLanguageUnit"      -> personDetails.correspondenceAddress.fold(Some("false"))(address =>
          Some(address.isWelshLanguageUnit.toString)
        ),
        "isInternationalAddress" -> Some(isInternationalAddress.toString)
      )
    )
}

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

package util

import java.io

import models.{PersonDetails, PertaxContext}
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.frontend.auth.connectors.domain.Accounts
import uk.gov.hmrc.http.HeaderCarrier

object AuditServiceTools {

  def buildEvent(auditType:String, transactionName:String, detail: Map[String, Option[String]])(implicit hc: HeaderCarrier, context: PertaxContext): DataEvent = {

    def getIdentifierPair(key: String, f: Accounts => Option[String]): Option[(String, String)] =
      context.user.flatMap(user => f(user.authContext.principal.accounts)).map( (key, _) )

    val standardAuditData: Map[String, String] = List(
      getIdentifierPair( "ctUtr",    _.ct.map(_.utr.utr) ),
      getIdentifierPair( "nino",     _.paye.map(_.nino.nino)),
      getIdentifierPair( "saUtr",    _.sa.map(_.utr.utr) ),
      getIdentifierPair( "vrn",      _.vat.map(_.vrn.vrn) ),
      context.user.flatMap( user => user.authContext.user.governmentGatewayToken ).map( ("credId", _) ),
      hc.deviceID.map( ( "deviceId", _ ) ),
      context.request.cookies.get("mdtpdf").map( cookie => ("deviceFingerprint", cookie.value) )
    ).flatten.toMap

    val customAuditData = detail.map(x => x._2.map((x._1, _))).flatten.filter(_._2!="").toMap

    val customTags = Map (
      "clientIP"        -> hc.trueClientIp,
      "clientPort"      -> hc.trueClientPort,
      "path"            -> Some(context.request.path),
      "transactionName" -> Some(transactionName)
    )

    DataEvent(
      auditSource = "pertax-frontend",
      auditType = auditType,
      tags = hc.headers.toMap ++ customTags.map( x => x._2.map( (x._1, _) )).flatten.toMap,
      detail = standardAuditData ++ customAuditData
    )

  }

  def buildAddressChangeEvent(auditType: String, personDetails: PersonDetails)(implicit hc: HeaderCarrier, context: PertaxContext): DataEvent = {

    buildEvent(auditType,"change_address",
    Map("line1" -> Some(personDetails.address.flatMap(_.line1).getOrElse(None).toString),
        "line2" ->  Some(personDetails.address.flatMap(_.line2).getOrElse(None).toString),
        "line3" -> Some(personDetails.address.flatMap(_.line3).getOrElse(None).toString),
        "line4" -> Some(personDetails.address.flatMap(_.line4).getOrElse(None).toString),
        "line5" -> Some(personDetails.address.flatMap(_.line5).getOrElse(None).toString),
        "postcode" -> Some(personDetails.address.flatMap(_.postcode).getOrElse(None).toString),
        "startDate" -> Some(personDetails.address.flatMap(_.startDate).getOrElse(None).toString),
        "type" -> Some(personDetails.address.flatMap(_.`type`).getOrElse(None).toString),
        "welshLanguageUnit" -> personDetails.correspondenceAddress.fold(Some("false"))(address => Some(address.isWelshLanguageUnit.toString))))
  }
}

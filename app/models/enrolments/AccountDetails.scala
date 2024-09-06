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

package models.enrolments

import play.api.i18n.Messages
import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json._
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.Locale

case class MFADetails(factorNameKey: String, factorValue: String) {
  def this(additonalFactors: AdditonalFactors) =
    this(
      factorNameKey = additonalFactors.factorType match {
        case "totp"  => "mfaDetails.totp"
        case "voice" => "mfaDetails.voice"
        case _       => "mfaDetails.text"
      },
      factorValue = additonalFactors.factorType match {
        case "totp" => additonalFactors.name.getOrElse("")
        case _      => additonalFactors.trimmedPhoneNumber
      }
    )
}

object MFADetails {
  implicit val format: Format[MFADetails] = Json.format[MFADetails]
}

case class AccountDetails(
  identityProviderType: IdentityProviderType,
  credId: String,
  userId: String,
  private val email: Option[SensitiveString],
  lastLoginDate: Option[String],
  mfaDetails: Seq[MFADetails],
  hasSA: Option[Boolean] = None
) {

  val emailDecrypted: Option[String] = email.map(_.decryptedValue)

  private def formatDate(implicit messages: Messages): Option[String] =
    lastLoginDate.map { date =>
      val locale        = Locale.forLanguageTag(s"${messages.lang.code}-GB")
      val zonedDateTime = ZonedDateTime.ofInstant(Instant.parse(date), ZoneId.of("GB"))
      zonedDateTime.format(DateTimeFormatter.ofPattern("dd MMMM uuuu").withLocale(locale)) +
        " " + messages("common.dateToTime") + " " +
        zonedDateTime.format(DateTimeFormatter.ofPattern("h:mm")) +
        zonedDateTime
          .format(DateTimeFormatter.ofPattern("a"))
          .toUpperCase // <- Older versions of Java return this in lower case
    }
}

object AccountDetails {

  def additionalFactorsToMFADetails(additionalFactors: Option[List[AdditonalFactors]]): Seq[MFADetails] =
    additionalFactors.fold[Seq[MFADetails]](Seq.empty[MFADetails]) { additionalFactors =>
      additionalFactors.map { additionalFactor =>
        new MFADetails(additionalFactor)
      }
    }

  def userFriendlyAccountDetails(accountDetails: AccountDetails)(implicit messages: Messages): AccountDetails =
    accountDetails.copy(
      credId = accountDetails.credId,
      userId = AccountDetails.trimmedUserId(accountDetails.userId),
      email = accountDetails.email,
      lastLoginDate = accountDetails.formatDate
    )

  def trimmedUserId(obfuscatedId: String): String =
    obfuscatedId.replaceAll("[*]", "")

  implicit val AccountDetailsWrites: Writes[AccountDetails] = new Writes[AccountDetails] {
    override def writes(o: AccountDetails): JsValue =
      Json.obj(
        "credId"        -> o.credId,
        "userId"        -> o.userId,
        "lastLoginDate" -> o.lastLoginDate,
        "mfaDetails"    -> o.mfaDetails
      ) ++ o.emailDecrypted.map(email => Json.obj("email" -> email)).getOrElse(Json.obj()) ++
        o.hasSA.map(hasSA => Json.obj("hasSA" -> hasSA)).getOrElse(Json.obj())
  }

  def mongoFormats(implicit crypto: Encrypter with Decrypter): Format[AccountDetails] = {

    implicit val strFormats: Format[String] = Format(Reads.StringReads, Writes.StringWrites)
    implicit val ssf                        = JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)

    ((__ \ "identityProviderType").format[IdentityProviderType](IdentityProviderTypeFormat.reads)(
      IdentityProviderTypeFormat.writes
    ) ~
      (__ \ "credId").format[String] ~
      (__ \ "userId").format[String] ~
      (__ \ "email").formatNullable[SensitiveString] ~
      (__ \ "lastLoginDate").formatNullable[String] ~
      (__ \ "mfaDetails").format[Seq[MFADetails]] ~
      (__ \ "hasSA").formatNullable[Boolean])(AccountDetails.apply, unlift(AccountDetails.unapply))

  }
}

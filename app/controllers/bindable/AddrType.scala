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

package controllers.bindable

import models.{EditCorrespondenceAddress, EditResidentialAddress, EditedAddress}
import play.api.mvc.{JavascriptLiteral, QueryStringBindable}
import util.Enumerable

import java.time.Instant

object AddrType {
  def apply(value: String): Option[AddrType] = value match {
    case "residential" => Some(ResidentialAddrType)
    case "postal"      => Some(PostalAddrType)
    case _             => None
  }

  def toEditedAddress(addrType: AddrType, date: Instant): EditedAddress = addrType match {
    case PostalAddrType      => EditCorrespondenceAddress(date)
    case ResidentialAddrType => EditResidentialAddress(date)
  }

  val values: Seq[AddrType]                                                                                   = Seq(ResidentialAddrType, PostalAddrType)
  implicit val enumerable: Enumerable[AddrType]                                                               = Enumerable(values.map(v => v.toString -> v): _*)
  implicit def queryBinder(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[AddrType] =
    new QueryStringBindable[AddrType] {
      def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, AddrType]] =
        stringBinder.bind(key, params).flatMap {
          case Right(s) =>
            values.find(_.toString == s) match {
              case Some(ot) => Some(Right(ot))
              case _        => None
            }
          case _        => Some(Left(s"Unable to bind query parameter: $key"))
        }

      def unbind(key: String, value: AddrType): String = stringBinder.unbind(key, value.toString)
    }

  implicit val jsLiteral: JavascriptLiteral[AddrType] = (value: AddrType) => value.toString
}
sealed trait AddrType {
  override def toString: String = ifIs("residential", "postal")

  def ifIs[T](residential: => T, postal: => T): T = this match {
    case ResidentialAddrType => residential
    case PostalAddrType      => postal
  }

}
case object PostalAddrType extends AddrType
case object ResidentialAddrType extends AddrType

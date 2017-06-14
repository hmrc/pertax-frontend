/*
 * Copyright 2017 HM Revenue & Customs
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

object AddrType {
  def apply(value: String): Option[AddrType] = value match {
    case "sole"    => Some(SoleAddrType)
    case "primary" => Some(PrimaryAddrType)
    case "postal"  => Some(PostalAddrType)
    case _ => None
  }
}
sealed trait AddrType {
  override def toString = ifIs("primary", "sole", "postal")

  def ifIsPrimary[T](value: T) = ifIs(Some(value), None, None)
  def ifIsSole[T](value: T) = ifIs(None, Some(value), None)
  def ifIsPostal[T](value: T) = ifIs(None, None, Some(value))

  def ifIs[T](primary: => T, sole: => T, postal: => T): T = this match {
    case PrimaryAddrType => primary
    case SoleAddrType    => sole
    case PostalAddrType  => postal
  }
}
case object PostalAddrType extends AddrType
sealed trait ResidentialAddrType extends AddrType
case object SoleAddrType extends ResidentialAddrType
case object PrimaryAddrType extends ResidentialAddrType

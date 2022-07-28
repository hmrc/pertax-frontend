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

package viewmodels

import java.time.LocalDate

case class Heading(label: Message, url: Url)

case class Link(message: Message, url: Url, gaLabel: String)

case class TaxYears(previousTaxYear: Int, currentTaxYear: Int)

sealed trait Message
case class Text(key: String, args: List[Message]) extends Message
case class Date(date: Option[LocalDate], default: String = "dd MMMM yyyy") extends Message
case class Literal(value: String) extends Message

object Message {

  def text(key: String): Message =
    Text(key, Nil)

  def text(key: String, args: String*): Message =
    Text(key, args.toList.map(Literal))

  def text(key: String, args: Message*)(implicit d: DummyImplicit): Message =
    Text(key, args.toList)
}

sealed trait Url
case object MakePaymentUrl extends Url
case object TaxPaidUrl extends Url
case class UnderpaidUrl(year: Int) extends Url
case class UnderpaidReasonsUrl(year: Int) extends Url
case class OverpaidUrl(year: Int) extends Url
case class OverpaidReasonsUrl(year: Int) extends Url
case class RightAmountUrl(year: Int) extends Url
case class NotCalculatedUrl(year: Int) extends Url
case class NotEmployedUrl(year: Int) extends Url
case object Empty extends Url

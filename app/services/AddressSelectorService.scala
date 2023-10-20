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

package services

import models.addresslookup.{Address, AddressRecord}

import javax.inject.Inject
import scala.util.Try

class AddressSelectorService @Inject() () {

  def orderSet(unorderedSeq: Seq[AddressRecord]): Seq[AddressRecord] =
    unorderedSeq.sortWith { (a, b) =>
      def sort(zipped: Seq[(Option[Int], Option[Int])]): Boolean = zipped match {
        case (Some(nA), Some(nB)) :: tail =>
          if (nA == nB) sort(tail) else nA < nB
        case (Some(_), None) :: _         => true
        case (None, Some(_)) :: _         => false
        case _                            => mkString(a.address) < mkString(b.address)
      }

      sort(numbersIn(a.address).zipAll(numbersIn(b.address), None, None).toList)
    }

  private def mkString(p: Address) = p.lines.mkString(" ").toLowerCase()

  private def numbersIn(p: Address): Seq[Option[Int]] =
    "([0-9]+)".r.findAllIn(mkString(p)).map(n => Try(n.toInt).toOption).toSeq.reverse :+ None

}

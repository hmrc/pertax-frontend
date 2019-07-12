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

package controllers

import play.api.Play
import play.api.mvc.{PathBindable, QueryStringBindable}
import uk.gov.hmrc.play.binders.ContinueUrl
import uk.gov.hmrc.play.config.RunMode

package object bindable {

  implicit def addrTypeBinder(implicit stringBinder: PathBindable[String]) = new PathBindable[AddrType] {

    def bind(key: String, value: String): Either[String, AddrType] =
      AddrType(value).map(Right(_)).getOrElse(Left("Invalid address type in path"))

    def unbind(key: String, addrType: AddrType): String = addrType.toString
  }


  implicit val continueUrlBinder = new QueryStringBindable[ContinueUrl] {

    val parentBinder: QueryStringBindable[ContinueUrl] = ContinueUrl.queryBinder

    def errorFor(invalidUrl: String) = s"'$invalidUrl' is not a valid continue URL"

    def bind(key: String, params: Map[String, Seq[String]]) =
      parentBinder.bind(key, params).map {
        case Right(continueUrl) if continueUrl.isRelativeOrDev(RunMode(Play.current.mode, Play.current.configuration).env) => Right(continueUrl)
        case Right(continueUrl) => Left(errorFor(continueUrl.url))
        case Left(message) => Left(message)
      }

    def unbind(key: String, value: ContinueUrl) = parentBinder.unbind(key, value)

  }
}

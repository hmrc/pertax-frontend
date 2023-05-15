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

import play.api.mvc.QueryStringBindable
import scala.util.{Success, Try}

case class Origin(origin: String) {
  require(Origin.Allowed.pattern.matcher(origin).find())
}

object Origin {

  private val Allowed = "^[\\w\\.-]{1,100}$".r

  val Default = Origin("unknown")

  implicit def queryBinder(implicit stringBinder: QueryStringBindable[String]) = new QueryStringBindable[Origin] {
    def bind(key: String, params: Map[String, Seq[String]]) = {
      val result = stringBinder.bind(key, params).map {
        case Right(s) =>
          Try(Origin(s)) match {
            case Success(url) => Right(url)
            case _            => Right(Default)
          }
        case _        => Right(Default)
      }
      result.orElse(Some(Right(Default)))
    }

    def unbind(key: String, value: Origin) = stringBinder.unbind(key, value.origin)

  }
}

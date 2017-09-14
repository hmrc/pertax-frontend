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

import java.net.URLEncoder

import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.play.config.RunMode

case class StrictContinueUrl(url: String) {

  val isAbsoluteUrl = url.startsWith("http")

  val isRelativeUrl = url.matches("^[/][^/].*")


  def isRelativeOrDev(env: String) = isRelativeUrl || env == "Dev"

  lazy val encodedUrl = URLEncoder.encode(url, "UTF-8")

}

object StrictContinueUrl {
  private def errorFor(invalidUrl: String) = s"'$invalidUrl' is not a valid continue URL"

  implicit def queryBinder(implicit stringBinder: QueryStringBindable[String]) = new QueryStringBindable[StrictContinueUrl] {
    def bind(key: String, params: Map[String, Seq[String]]) =
      stringBinder.bind(key, params).map {
        case Right(s) =>

          val scu = StrictContinueUrl(s)
          val isValid = (scu.isRelativeUrl || (RunMode.env=="Dev" && s.startsWith("http://localhost"))) && !s.contains("@")

          if (isValid) Right(scu)
          else Left(errorFor(s))

        case Left(message) => Left(message)
      }

    def unbind(key: String, value: StrictContinueUrl) = stringBinder.unbind(key, value.url)

  }
}

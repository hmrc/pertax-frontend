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

import java.io.File

import play.api.Mode.Mode
import play.api.mvc.{PathBindable, QueryStringBindable}
import play.api.{Environment, Mode, Play}
import uk.gov.hmrc.play.frontend.binders.RedirectUrl._
import uk.gov.hmrc.play.frontend.binders.RedirectUrlPolicy.Id
import uk.gov.hmrc.play.frontend.binders._
import uk.gov.hmrc.play.binders.ContinueUrl
import uk.gov.hmrc.play.config.RunMode


package object bindable {

  implicit def addrTypeBinder(implicit stringBinder: PathBindable[String]): PathBindable[AddrType] = new PathBindable[AddrType] {
    def bind(key: String, value: String): Either[String, AddrType] =
      AddrType(value).map(Right(_)).getOrElse(Left("Invalid address type in path"))

    def unbind(key: String, addrType: AddrType): String = addrType.toString
  }


  implicit val continueUrlBinder: QueryStringBindable[SafeRedirectUrl] = new QueryStringBindable[SafeRedirectUrl] {

    val runModeEnv : String = RunMode(Play.current.mode, Play.current.configuration).env

    print("%%%%%%%%%%%% runModeEnv = " +  runModeEnv)

    private lazy val isItLocalForTest : Mode = if (isRelativeUrl(SafeRedirectUrl.toString) || runModeEnv.toLowerCase.equals("dev")) Mode.Dev else Play.current.mode

    print("%%%%%%%%%%%% isItLocalForTest = " +  isItLocalForTest.toString)

    val parentBinder: QueryStringBindable[RedirectUrl] = RedirectUrl.queryBinder

    val policy: RedirectUrlPolicy[Id] = OnlyRelative | PermitAllOnDev(Environment.simple(mode = isItLocalForTest))

    def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, SafeRedirectUrl]] =
      parentBinder.bind(key, params).map {
        case Right(redirectUrl) => redirectUrl.getEither(policy)
        case Left(error) => Left(error)
      }

    def unbind(key: String, value: SafeRedirectUrl): String = parentBinder.unbind(key, RedirectUrl(value.url))

  }
}

/*
 * Copyright 2025 HM Revenue & Customs
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

import config.ConfigDecorator
import play.api.{Environment, Mode}
import uk.gov.hmrc.play.bootstrap.binders.{AbsoluteWithHostnameFromAllowlist, OnlyRelative, RedirectUrl}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl.idFunctor

import java.net.URLEncoder
import play.api.Logging

import java.net.URI
import javax.inject.Inject

class URLService @Inject() (
  appConfig: ConfigDecorator,
  env: Environment
) extends Logging {
  def safeEncodedUrl(url: String, hostAndPort: String): String =
    RedirectUrl(url).getEither(
      OnlyRelative | AbsoluteWithHostnameFromAllowlist("localhost")
    ) match {
      case Right(safeRedirectUrl) => URLEncoder.encode(safeRedirectUrl.url, "UTF-8")
      case Left(error)            =>
        val ex = new IllegalArgumentException(error)
        logger.error(ex.getMessage, ex)
        URLEncoder.encode(
          s"$hostAndPort${controllers.routes.HomeController.index}",
          "UTF-8"
        )
    }

  def localFriendlyEncodedUrl(url: String, hostAndPort: String): String = {
    val isLocalEnv =
      if (env.mode.equals(Mode.Test)) {
        false
      } else {
        appConfig.runMode.contains(Mode.Dev.toString)
      }

    val uri = new URI(url)

    if (!uri.isAbsolute && isLocalEnv) {
      safeEncodedUrl(s"http://$hostAndPort$url", hostAndPort)
    } else {
      safeEncodedUrl(url, hostAndPort)
    }
  }
}

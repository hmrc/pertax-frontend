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

package controllers.controllershelpers

import com.google.inject.{Inject, Singleton}
import config.{ConfigDecorator, NewsAndTilesConfig}
import controllers.auth.requests.UserRequest
import play.api.i18n.Messages
import play.api.mvc.AnyContent
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.sca.logging.Logging
import views.html.home.options.*

@Singleton
class HomeOptionsGenerator @Inject() (
  latestNewsAndUpdatesView: LatestNewsAndUpdatesView,
  newsAndTilesConfig: NewsAndTilesConfig
)(implicit configDecorator: ConfigDecorator)
    extends Logging {

  def getLatestNewsAndUpdatesCard()(implicit
    messages: Messages,
    request: UserRequest[AnyContent]
  ): Option[HtmlFormat.Appendable] =
    if (configDecorator.isNewsAndUpdatesTileEnabled && newsAndTilesConfig.getNewsAndContentModelList().nonEmpty) {
      Some(latestNewsAndUpdatesView())
    } else {
      None
    }

}

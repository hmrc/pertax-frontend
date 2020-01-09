/*
 * Copyright 2020 HM Revenue & Customs
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

import config.ConfigDecorator
import com.google.inject.Inject
import play.api.Configuration
import play.api.i18n.{Lang, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.play.language.{LanguageController, LanguageUtils}

class LanguageSwitchController @Inject()(
  configDecorator: ConfigDecorator,
  configuration: Configuration,
  languageUtils: LanguageUtils,
  val messagesApi: MessagesApi
) extends LanguageController(configuration, languageUtils) {

  def enGb(): Action[AnyContent] = switchToLanguage(language = "english")
  def cyGb(): Action[AnyContent] = switchToLanguage(language = "cymraeg")
  def fallbackURL: String = configDecorator.pertaxFrontendService
  def languageMap: Map[String, Lang] = Map(
    "english" -> Lang("en"),
    "cymraeg" -> Lang("cy")
  )

}

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

package config

import com.google.inject.{Inject, Singleton}
import models.NewsAndContentModel
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import play.api.Configuration
import play.api.i18n.Lang
import util.DateTimeTools.defaultTZ

import scala.collection.JavaConverters._

@Singleton
class NewsAndTilesConfig @Inject() (configuration: Configuration) {

  def getNewsAndContentModelList(lang: String): List[NewsAndContentModel] =
    configuration.underlying
      .getObject("feature.news")
      .asScala
      .map { case (newsSection, _) =>
        val shortDescription = if (lang equals "en") {
          configuration.get[String](s"feature.news.$newsSection.short-description-en")
        } else {
          configuration.get[String](s"feature.news.$newsSection.short-description-cy")
        }
        val content = if (lang equals "en") {
          configuration.get[String](s"feature.news.$newsSection.content-en")
        } else {
          configuration.get[String](s"feature.news.$newsSection.content-cy")
        }
        val startDate = configuration.get[String](s"feature.news.$newsSection.start-date")
        val endDate = configuration.get[String](s"feature.news.$newsSection.end-date")
        NewsAndContentModel(shortDescription, content, startDate, endDate)
      }
      .toList
}

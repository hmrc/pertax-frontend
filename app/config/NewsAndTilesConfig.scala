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

package config

import com.google.inject.{Inject, Singleton}
import models.NewsAndContentModel
import play.api.Configuration
import play.api.i18n.Messages
import util.LocalDateUtilities

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.jdk.CollectionConverters._

@Singleton
class NewsAndTilesConfig @Inject() (configuration: Configuration, localDateUtilities: LocalDateUtilities) {

  implicit val localDateOrdering: Ordering[LocalDate] = Ordering.by(-_.toEpochDay)

  def getNewsAndContentModelList()(implicit messages: Messages): List[NewsAndContentModel] = {
    val config = configuration.underlying

    if (config.hasPathOrNull("feature.news")) {
      config
        .getObject("feature.news")
        .asScala
        .map { case (newsSection, _) =>
          val formatter       = DateTimeFormatter.ofPattern("yyyy-MM-dd")
          val localStartDate  =
            LocalDate.parse(configuration.get[String](s"feature.news.$newsSection.start-date"), formatter)
          val optionalEndDate = configuration.getOptional[String](s"feature.news.$newsSection.end-date")
          val localEndDate    = optionalEndDate match {
            case Some(endDate) => LocalDate.parse(endDate, formatter)
            case None          => LocalDate.MAX
          }

          if (localDateUtilities.isBetween(LocalDate.now(), localStartDate, localEndDate)) {
            val isDynamicOptional = configuration.getOptional[Boolean](s"feature.news.$newsSection.dynamic-content")
            isDynamicOptional match {
              case Some(_) => Some(NewsAndContentModel(newsSection, "", "", isDynamic = true, localStartDate))
              case None    =>
                val shortDescription = if (messages.lang.code equals "en") {
                  configuration.get[String](s"feature.news.$newsSection.short-description-en")
                } else {
                  configuration.get[String](s"feature.news.$newsSection.short-description-cy")
                }
                val content          = if (messages.lang.code equals "en") {
                  configuration.get[String](s"feature.news.$newsSection.content-en")
                } else {
                  configuration.get[String](s"feature.news.$newsSection.content-cy")
                }
                Some(NewsAndContentModel(newsSection, shortDescription, content, isDynamic = false, localStartDate))
            }
          } else {
            None
          }
        }
        .toList
        .flatten
        .sortBy(_.startDate)
    } else {
      List.empty
    }
  }
}
